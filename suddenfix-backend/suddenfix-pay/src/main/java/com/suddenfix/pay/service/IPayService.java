package com.suddenfix.pay.service;

import com.suddenfix.common.result.Result;
import com.suddenfix.pay.domain.pojo.Pay;

import java.util.Map;

public interface IPayService {
    void initPayRecord(Long orderId, Long userId, Long payAmount,Integer payChannel);

    Result<Pay> queryPay(Long orderId, Long userId);

    Result<Pay> createMockPay(Long orderId, Long userId);

    String createAlipayPage(Long orderId, Long userId);

    String handleAlipayNotify(Map<String, String> params);

    void refundPay(String outTradeNo, Long refundAmount, Long orderId);

    String handleMockNotify(Map<String, String> params);
}
