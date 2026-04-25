package com.suddenfix.pay.service.impl;

import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.suddenfix.common.dto.PayCreatedMessage;
import com.suddenfix.common.dto.PaySuccessMessage;
import com.suddenfix.common.exception.ServiceException;
import com.suddenfix.common.result.Result;
import com.suddenfix.common.utils.GeneIdGenerator;
import com.suddenfix.pay.domain.pojo.Msg;
import com.suddenfix.pay.domain.pojo.Pay;
import com.suddenfix.pay.mapper.MsgMapper;
import com.suddenfix.pay.mapper.PayMapper;
import com.suddenfix.pay.service.IPayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.suddenfix.common.enums.Exist.IS_EXIST;
import static com.suddenfix.common.enums.MsgStatus.PENDING_SENDING;
import static com.suddenfix.common.enums.MsgTopic.TOPIC_PAY_CREATED;
import static com.suddenfix.common.enums.MsgTopic.TOPIC_PAY_SUCCESS;
import static com.suddenfix.common.enums.PayStatus.PAYMENT_SUCCESS;
import static com.suddenfix.common.enums.PayStatus.PENDING_PAYMENT;
import static com.suddenfix.common.enums.RedisPreMessage.PAY_IDEMPOTENT;
import static com.suddenfix.common.enums.RedisPreMessage.PAY_NOTIFY_LOCKED;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayServiceImpl implements IPayService {

    private static final long JS_SAFE_INTEGER_RECOVERY_WINDOW = 4096L;
    private static final int RECENT_PENDING_PAY_LIMIT = 10;

    private final PayMapper payMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MsgMapper msgMapper;

    @Value("${alipay.gateway-url}")
    private String gatewayUrl;

    @Value("${alipay.app-id}")
    private String appId;

    @Value("${alipay.merchant-private-key}")
    private String merchantPrivateKey;

    @Value("${alipay.alipay-public-key}")
    private String alipayPublicKey;

    @Value("${alipay.notify-url}")
    private String notifyUrl;

    @Value("${alipay.return-url}")
    private String returnUrl;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initPayRecord(Long orderId, Long userId, Long payAmount, Integer payChannel) {
        String idempotentKey = PAY_IDEMPOTENT.getValue() + orderId;
        Boolean isExist = redisTemplate.opsForValue().setIfAbsent(idempotentKey, IS_EXIST.getIsExist(), 1, TimeUnit.DAYS);
        if (Boolean.FALSE.equals(isExist)) {
            log.info("【支付服务】订单 {} 已初始化过支付流水，忽略重复消息", orderId);
            return;
        }
        Integer resolvedPayChannel = payChannel == null ? 1 : payChannel;

        try {
            Long payId = GeneIdGenerator.generatorId(userId);
            String outTradeNo = "PAY" + UUID.randomUUID().toString().replace("-", "").substring(0, 15);

            Pay pay = Pay.builder()
                    .payId(payId)
                    .orderId(orderId)
                    .userId(userId)
                    .outTradeNo(outTradeNo)
                    .amount(payAmount)
                    .payChannel(resolvedPayChannel)
                    .status(PENDING_PAYMENT.getStatus())
                    .build();

            int insertRow = payMapper.insertPay(pay);
            if (insertRow <= 0) {
                throw new ServiceException("初始化支付流水落库失败");
            }

            insertLocalMessage(
                    userId,
                    TOPIC_PAY_CREATED.getTopic(),
                    JSONUtil.toJsonStr(PayCreatedMessage.builder()
                            .orderId(orderId)
                            .userId(userId)
                            .payId(payId)
                            .outTradeNo(outTradeNo)
                            .payChannel(resolvedPayChannel)
                            .amount(payAmount)
                            .build())
            );
            log.info("【支付服务】支付流水初始化成功，orderId={}, outTradeNo={}", orderId, outTradeNo);
        } catch (Exception e) {
            redisTemplate.delete(idempotentKey);
            throw new RuntimeException("初始化支付流水失败", e);
        }
    }

    @Override
    public Result<Pay> queryPay(Long orderId, Long userId) {
        Pay pay = resolvePay(orderId, userId);
        if (pay == null) {
            throw new ServiceException("支付单正在生成中，请稍后重试");
        }
        return Result.success(pay);
    }

    @Override
    public Result<Pay> createMockPay(Long orderId, Long userId) {
        return queryPay(orderId, userId);
    }

    @Override
    public String createAlipayPage(Long orderId, Long userId) {
        Pay pay = resolvePay(orderId, userId);
        if (pay == null) {
            throw new ServiceException("支付单尚未生成，请稍后重试");
        }
        if (!PENDING_PAYMENT.getStatus().equals(pay.getStatus())) {
            throw new ServiceException("当前支付单状态不允许再次发起支付");
        }

        try {
            AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
            request.setNotifyUrl(notifyUrl);
            request.setReturnUrl(returnUrl);

            JSONObject bizContent = new JSONObject();
            bizContent.set("out_trade_no", pay.getOutTradeNo());
            bizContent.set("total_amount", toYuan(pay.getAmount()));
            bizContent.set("subject", "suddenfix商城订单" + pay.getOrderId());
            bizContent.set("product_code", "FAST_INSTANT_TRADE_PAY");
            request.setBizContent(bizContent.toString());

            return alipayClient().pageExecute(request).getBody();
        } catch (AlipayApiException e) {
            throw new ServiceException("创建支付宝支付页失败");
        }
    }

    private Pay resolvePay(Long orderId, Long userId) {
        Pay pay = userId == null
                ? payMapper.selectPayByOrderId(orderId)
                : payMapper.selectPayByOrderIdAndUserId(orderId, userId);
        if (pay != null || userId == null || orderId == null) {
            return pay;
        }

        List<Pay> recentPays = payMapper.selectRecentPendingPayByUserId(userId, RECENT_PENDING_PAY_LIMIT);
        if (recentPays == null || recentPays.isEmpty()) {
            return null;
        }

        Pay recoveredPay = recentPays.stream()
                .filter(item -> item.getOrderId() != null)
                .filter(item -> Math.abs(item.getOrderId() - orderId) <= JS_SAFE_INTEGER_RECOVERY_WINDOW)
                .min(Comparator.comparingLong(item -> Math.abs(item.getOrderId() - orderId)))
                .orElse(null);
        if (recoveredPay != null) {
            log.warn("【支付服务】检测到前端订单号可能发生 JS 精度丢失，requestOrderId={}, recoveredOrderId={}, userId={}",
                    orderId, recoveredPay.getOrderId(), userId);
        }
        return recoveredPay;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String handleAlipayNotify(Map<String, String> params) {
        try {
            boolean signVerified = AlipaySignature.rsaCheckV1(params, alipayPublicKey, "UTF-8", "RSA2");
            if (!signVerified) {
                log.warn("【支付宝回调】验签失败: {}", params);
                return "failure";
            }

            String tradeStatus = params.get("trade_status");
            String outTradeNo = params.get("out_trade_no");
            String tradeNo = params.get("trade_no");
            if (outTradeNo == null || outTradeNo.isBlank()) {
                return "failure";
            }
            if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
                return "success";
            }

            Pay pay = payMapper.selectPayByOutTradeNo(outTradeNo);
            if (pay == null) {
                return "failure";
            }
            if (!PENDING_PAYMENT.getStatus().equals(pay.getStatus())) {
                return "success";
            }

            String notifyIdempotentKey = PAY_NOTIFY_LOCKED.getValue() + outTradeNo + ":" + PENDING_PAYMENT.getStatus();
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(notifyIdempotentKey, "1", 1, TimeUnit.DAYS);
            if (Boolean.FALSE.equals(locked)) {
                log.info("【支付宝回调】重复通知被幂等拦截，outTradeNo={}", outTradeNo);
                return "success";
            }

            return processPaySuccess(outTradeNo, tradeNo == null ? outTradeNo : tradeNo, "支付宝支付成功");
        } catch (Exception e) {
            log.error("【支付宝回调】处理异常", e);
            return "failure";
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refundPay(String outTradeNo, Long refundAmount, Long orderId) {
        Pay pay = payMapper.selectPayByOutTradeNo(outTradeNo);
        if (pay == null) {
            throw new ServiceException("支付单不存在，无法退款");
        }
        if (!PAYMENT_SUCCESS.getStatus().equals(pay.getStatus())) {
            log.info("【支付退款】支付单 {} 当前状态 {}，无需重复退款", outTradeNo, pay.getStatus());
            return;
        }

        String refundNo = "REFUND_" + orderId;
        try {
            AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
            JSONObject bizContent = new JSONObject();
            bizContent.set("out_trade_no", outTradeNo);
            bizContent.set("refund_amount", toYuan(refundAmount));
            bizContent.set("out_request_no", refundNo);
            bizContent.set("refund_reason", "订单取消退款");
            request.setBizContent(bizContent.toString());

            AlipayTradeRefundResponse response = alipayClient().execute(request);
            if (response != null && response.isSuccess()) {
                payMapper.updatePayRefunded(outTradeNo, "订单取消退款成功");
                log.info("【支付退款】订单 {} 已完成退款", orderId);
                return;
            }

            if (queryRefundSuccess(outTradeNo, refundNo)) {
                payMapper.updatePayRefunded(outTradeNo, "退款查询确认成功");
                log.info("【支付退款】订单 {} 通过查询确认退款成功", orderId);
                return;
            }

            String errorMsg = response == null ? "退款请求无响应" : response.getSubMsg();
            throw new ServiceException("退款请求失败: " + errorMsg);
        } catch (AlipayApiException e) {
            if (queryRefundSuccess(outTradeNo, refundNo)) {
                payMapper.updatePayRefunded(outTradeNo, "退款查询确认成功");
                return;
            }
            throw new ServiceException("调用支付宝退款失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String handleMockNotify(Map<String, String> params) {
        try {
            String outTradeNo = params.get("outTradeNo");
            String channelTradeNo = params.getOrDefault(
                    "channelTradeNo",
                    "MOCK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
            );
            if (outTradeNo == null || outTradeNo.isBlank()) {
                return "failure";
            }
            return processPaySuccess(outTradeNo, channelTradeNo, "模拟支付成功");
        } catch (Exception e) {
            log.error("【模拟支付回调】处理发生异常", e);
            return "failure";
        }
    }

    private String processPaySuccess(String outTradeNo, String channelTradeNo, String successMessage) {
        int updateRow = payMapper.updatePaySuccess(outTradeNo, channelTradeNo);
        if (updateRow <= 0) {
            log.info("【支付回调】支付流水已被处理或不存在。outTradeNo: {}", outTradeNo);
            return "success";
        }

        Pay pay = payMapper.selectPayByOutTradeNo(outTradeNo);
        if (pay == null) {
            return "failure";
        }

        insertLocalMessage(pay.getUserId(), TOPIC_PAY_SUCCESS.getTopic(), JSONUtil.toJsonStr(
                PaySuccessMessage.builder()
                        .orderId(pay.getOrderId())
                        .userId(pay.getUserId())
                        .outTradeNo(outTradeNo)
                        .channelTradeNo(channelTradeNo)
                        .build()
        ));
        log.info("【支付回调】{}，订单 {} 已进入已支付流程", successMessage, pay.getOrderId());
        return "success";
    }

    private boolean queryRefundSuccess(String outTradeNo, String refundNo) {
        try {
            AlipayTradeFastpayRefundQueryRequest queryRequest = new AlipayTradeFastpayRefundQueryRequest();
            JSONObject bizContent = new JSONObject();
            bizContent.set("out_trade_no", outTradeNo);
            bizContent.set("out_request_no", refundNo);
            queryRequest.setBizContent(bizContent.toString());
            AlipayTradeFastpayRefundQueryResponse response = alipayClient().execute(queryRequest);
            return response != null && response.isSuccess() && response.getRefundAmount() != null;
        } catch (AlipayApiException e) {
            log.warn("【支付退款】退款查询失败，outTradeNo={}", outTradeNo, e);
            return false;
        }
    }

    private void insertLocalMessage(Long userId, String topic, String payload) {
        Msg msg = Msg.builder()
                .msgId(GeneIdGenerator.generatorId(userId))
                .businessId(GeneIdGenerator.generatorId(userId))
                .topic(topic)
                .payload(payload)
                .status(PENDING_SENDING.getStatus())
                .retryCount(0)
                .nextRetryTime(new Date())
                .build();
        int insertRow = msgMapper.insertMsg(msg);
        if (insertRow <= 0) {
            throw new ServiceException("插入本地消息失败");
        }
    }

    private AlipayClient alipayClient() {
        return new DefaultAlipayClient(
                gatewayUrl,
                appId,
                merchantPrivateKey,
                "json",
                "UTF-8",
                alipayPublicKey,
                "RSA2"
        );
    }

    private String toYuan(Long amountInFen) {
        return BigDecimal.valueOf(amountInFen == null ? 0L : amountInFen, 2).toPlainString();
    }
}
