package com.suddenfix.order.service.impl;

import cn.hutool.json.JSONUtil;
import com.suddenfix.common.exception.ServiceException;
import com.suddenfix.common.result.Result;
import com.suddenfix.order.domain.dto.CouponPreheatDTO;
import com.suddenfix.order.domain.pojo.Coupon;
import com.suddenfix.order.domain.vo.CouponActivityVO;
import com.suddenfix.order.domain.vo.CouponPreheatVO;
import com.suddenfix.order.domain.vo.UserCouponVO;
import com.suddenfix.order.mapper.CouponMapper;
import com.suddenfix.order.mapper.CouponRecordMapper;
import com.suddenfix.order.service.ICouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.suddenfix.common.enums.RedisPreMessage.COUPON_BITMAP;
import static com.suddenfix.common.enums.RedisPreMessage.COUPON_IS_EXIST;
import static com.suddenfix.common.enums.RedisPreMessage.COUPON_META;
import static com.suddenfix.common.enums.RedisPreMessage.COUPON_STOCK_SEGMENT;
import static com.suddenfix.common.enums.RedisPreMessage.COUPON_USER_SET;
import static com.suddenfix.common.enums.RedisPreMessage.COUPON_USER_TOKEN_HASH;
import static com.suddenfix.order.config.CouponRabbitMQConfig.EXCHANGE_NAME;
import static com.suddenfix.order.config.CouponRabbitMQConfig.ROUTING_KEY;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements ICouponService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final CouponMapper couponMapper;
    private final CouponRecordMapper couponRecordMapper;

    @Override
    public Result<Void> preheatCoupon(CouponPreheatDTO couponPreheatDTO) {
        Coupon coupon = resolveCouponForPreheat(couponPreheatDTO);
        Long couponId = coupon.getId();
        Integer totalStock = coupon.getTotalStock();
        Integer segmentCount = coupon.getSegmentCount() == null ? 10 : coupon.getSegmentCount();
        segmentCount = Math.max(1, Math.min(segmentCount, totalStock));

        redisTemplate.delete(metaKey(couponId));
        redisTemplate.delete(userBuy(couponId));
        redisTemplate.delete(userSet(couponId));
        redisTemplate.delete(couponBitmap(couponId));
        redisTemplate.delete(stockISExist(couponId));
        for (int i = 0; i < segmentCount; i++) {
            redisTemplate.delete(segmentStock(couponId, i));
        }

        redisTemplate.opsForHash().put(metaKey(couponId), "totalStock", totalStock);
        redisTemplate.opsForHash().put(metaKey(couponId), "segmentCount", segmentCount);
        redisTemplate.opsForHash().put(metaKey(couponId), "status", "READY");
        redisTemplate.expire(metaKey(couponId), 1, TimeUnit.DAYS);

        int base = totalStock / segmentCount;
        int remainder = totalStock % segmentCount;
        int tokenCursor = 0;
        for (int i = 0; i < segmentCount; i++) {
            int currentStock = base + (i < remainder ? 1 : 0);
            List<String> tokens = new ArrayList<>();
            for (int j = 0; j < currentStock; j++) {
                tokens.add(couponId + "_" + tokenCursor++);
            }
            if (!tokens.isEmpty()) {
                redisTemplate.opsForList().rightPushAll(segmentStock(couponId, i), new ArrayList<>(tokens));
                redisTemplate.expire(segmentStock(couponId, i), 1, TimeUnit.DAYS);
                redisTemplate.opsForValue().setBit(couponBitmap(couponId), i, true);
            }
        }

        redisTemplate.expire(couponBitmap(couponId), 1, TimeUnit.DAYS);
        redisTemplate.opsForValue().set(stockISExist(couponId), 1, 1, TimeUnit.DAYS);
        redisTemplate.opsForSet().add(userSet(couponId), "-1");
        redisTemplate.opsForHash().put(userBuy(couponId), "-1", "DUMMY");
        redisTemplate.expire(userSet(couponId), 1, TimeUnit.DAYS);
        redisTemplate.expire(userBuy(couponId), 1, TimeUnit.DAYS);
        return Result.success();
    }

    @Override
    public Result<CouponPreheatVO> getCoupon(Long couponId, Long userId) {
        Coupon coupon = couponMapper.selectCoupon(couponId);
        validateCouponClaimWindow(coupon);
        if (hasClaimedCoupon(userId, couponId)) {
            throw new ServiceException("已经领取过该优惠券，请勿重复领取");
        }

        Object object = redisTemplate.opsForHash().get(metaKey(couponId), "status");
        if (object == null || !"READY".equals(String.valueOf(object))) {
            throw new ServiceException("优惠券库存尚未预热");
        }

        object = redisTemplate.opsForValue().get(stockISExist(couponId));
        if (object == null || "0".equals(String.valueOf(object))) {
            throw new ServiceException("优惠券已经派发完毕");
        }

        Boolean alreadyClaimed = redisTemplate.opsForSet().isMember(userSet(couponId), userId.toString());
        if (Boolean.TRUE.equals(alreadyClaimed)) {
            throw new ServiceException("已经领取过该优惠券，请勿重复领取");
        }

        Long addResult = redisTemplate.opsForSet().add(userSet(couponId), userId.toString());
        if (addResult == null || addResult == 0L) {
            throw new ServiceException("已经领取过该优惠券，请勿重复领取");
        }

        object = redisTemplate.opsForHash().get(metaKey(couponId), "segmentCount");
        if (object == null) {
            throw new ServiceException("无法查询到优惠券库存分段信息");
        }
        int segmentCount = Integer.parseInt(String.valueOf(object));
        int segment = (userId.hashCode() & Integer.MAX_VALUE) % segmentCount;

        int currentSegment = segment;
        while (true) {
            Boolean bit = redisTemplate.opsForValue().getBit(couponBitmap(couponId), currentSegment);
            if (Boolean.TRUE.equals(bit)) {
                object = redisTemplate.opsForList().leftPop(segmentStock(couponId, currentSegment));
                if (object != null) {
                    CouponPreheatVO preheatVO = CouponPreheatVO.builder()
                            .couponId(couponId)
                            .userId(userId)
                            .segment(currentSegment)
                            .couponToken(String.valueOf(object))
                            .build();
                    redisTemplate.opsForHash().put(userBuy(couponId), userId.toString(), JSONUtil.toJsonStr(preheatVO));
                    rabbitTemplate.convertAndSend(EXCHANGE_NAME, ROUTING_KEY, JSONUtil.toJsonStr(preheatVO));
                    return Result.success(preheatVO);
                }
                redisTemplate.opsForValue().setBit(couponBitmap(couponId), currentSegment, false);
            }

            Integer availableBit = redisTemplate.execute((RedisCallback<Integer>) connection -> {
                Long index = connection.bitPos(couponBitmap(couponId).getBytes(), true);
                return index == null ? -1 : index.intValue();
            });
            if (availableBit == null || availableBit == -1) {
                redisTemplate.opsForValue().set(stockISExist(couponId), 0);
                break;
            }
            currentSegment = availableBit;
        }

        redisTemplate.opsForSet().remove(userSet(couponId), userId.toString());
        return Result.fail();
    }

    @Override
    public Result<List<CouponActivityVO>> listAvailableCoupons(Long userId) {
        Date now = new Date();
        List<Coupon> coupons = couponMapper.selectActiveCoupons(now);
        if (coupons.isEmpty()) {
            return Result.success(List.of());
        }

        List<Long> couponIds = coupons.stream().map(Coupon::getId).toList();
        Set<Long> claimedCouponIds = new HashSet<>(safeClaimedCouponIds(userId, couponIds));
        List<CouponActivityVO> activities = coupons.stream()
                .map(coupon -> {
                    int remainingStock = resolveRemainingStock(coupon);
                    boolean claimed = claimedCouponIds.contains(coupon.getId());
                    return CouponActivityVO.builder()
                            .couponId(coupon.getId())
                            .name(coupon.getName())
                            .amount(coupon.getAmount())
                            .minPoint(coupon.getMinPoint())
                            .totalStock(coupon.getTotalStock())
                            .remainingStock(remainingStock)
                            .segmentCount(coupon.getSegmentCount())
                            .startTime(coupon.getStartTime())
                            .endTime(coupon.getEndTime())
                            .claimed(claimed)
                            .canClaim(!claimed && remainingStock > 0)
                            .build();
                })
                .toList();
        return Result.success(activities);
    }

    @Override
    public Result<List<UserCouponVO>> listUserCoupons(Long userId) {
        return Result.success(couponRecordMapper.selectUsableCouponsByUserId(userId, new Date()));
    }

    @Override
    public Result<List<UserCouponVO>> listCheckoutCoupons(Long userId, Long orderAmount) {
        long resolvedOrderAmount = orderAmount == null ? 0L : Math.max(0L, orderAmount);
        List<UserCouponVO> coupons = couponRecordMapper.selectUsableCouponsByUserId(userId, new Date());
        if (coupons == null || coupons.isEmpty()) {
            return Result.success(List.of());
        }

        List<UserCouponVO> result = coupons.stream()
                .map(coupon -> enrichCheckoutCoupon(coupon, resolvedOrderAmount))
                .sorted(Comparator
                        .comparing((UserCouponVO coupon) -> !Boolean.TRUE.equals(coupon.getAvailable()))
                        .thenComparing(UserCouponVO::getEstimatedDiscountAmount, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(UserCouponVO::getCouponId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return Result.success(result);
    }

    @Override
    public Result<CouponPreheatDTO> insertCoupon(Coupon coupon) {
        couponMapper.insertCoupon(coupon);
        CouponPreheatDTO couponPreheatDTO = CouponPreheatDTO.builder()
                .couponId(coupon.getId())
                .name(coupon.getName())
                .amount(coupon.getAmount())
                .minPoint(coupon.getMinPoint())
                .segmentCount(coupon.getSegmentCount())
                .totalStock(coupon.getTotalStock())
                .startTime(coupon.getStartTime())
                .endTime(coupon.getEndTime())
                .build();
        return Result.success(couponPreheatDTO);
    }

    private Coupon resolveCouponForPreheat(CouponPreheatDTO couponPreheatDTO) {
        if (couponPreheatDTO == null) {
            throw new ServiceException("优惠券预热参数不能为空");
        }

        if (couponPreheatDTO.getCouponId() != null) {
            Coupon existed = couponMapper.selectCoupon(couponPreheatDTO.getCouponId());
            if (existed == null) {
                throw new ServiceException("优惠券不存在");
            }
            if (existed.getTotalStock() == null || existed.getTotalStock() <= 0) {
                throw new ServiceException("优惠券库存必须大于 0");
            }
            if (existed.getSegmentCount() == null || existed.getSegmentCount() <= 0) {
                existed.setSegmentCount(Math.min(10, existed.getTotalStock()));
            }
            return existed;
        }

        if (couponPreheatDTO.getName() == null || couponPreheatDTO.getName().trim().isEmpty()) {
            throw new ServiceException("优惠券名称不能为空");
        }
        if (couponPreheatDTO.getAmount() == null || couponPreheatDTO.getAmount().signum() <= 0) {
            throw new ServiceException("优惠券面额必须大于 0");
        }
        if (couponPreheatDTO.getTotalStock() == null || couponPreheatDTO.getTotalStock() <= 0) {
            throw new ServiceException("优惠券库存必须大于 0");
        }
        if (couponPreheatDTO.getStartTime() == null || couponPreheatDTO.getEndTime() == null) {
            throw new ServiceException("请完整设置优惠券发放时间");
        }
        if (!couponPreheatDTO.getEndTime().after(couponPreheatDTO.getStartTime())) {
            throw new ServiceException("优惠券结束时间必须晚于开始时间");
        }

        int segmentCount = couponPreheatDTO.getSegmentCount() == null || couponPreheatDTO.getSegmentCount() <= 0
                ? Math.min(10, couponPreheatDTO.getTotalStock())
                : couponPreheatDTO.getSegmentCount();
        segmentCount = Math.max(1, Math.min(segmentCount, couponPreheatDTO.getTotalStock()));

        Coupon coupon = Coupon.builder()
                .name(couponPreheatDTO.getName().trim())
                .amount(couponPreheatDTO.getAmount())
                .minPoint(couponPreheatDTO.getMinPoint())
                .totalStock(couponPreheatDTO.getTotalStock())
                .segmentCount(segmentCount)
                .status(1)
                .startTime(couponPreheatDTO.getStartTime())
                .endTime(couponPreheatDTO.getEndTime())
                .build();
        couponMapper.insertCoupon(coupon);
        return coupon;
    }

    private void validateCouponClaimWindow(Coupon coupon) {
        if (coupon == null) {
            throw new ServiceException("优惠券不存在");
        }
        Date now = new Date();
        if (coupon.getStartTime() != null && now.before(coupon.getStartTime())) {
            throw new ServiceException("优惠券活动尚未开始");
        }
        if (coupon.getEndTime() != null && now.after(coupon.getEndTime())) {
            throw new ServiceException("优惠券活动已结束");
        }
    }

    private boolean hasClaimedCoupon(Long userId, Long couponId) {
        return safeClaimedCouponIds(userId, List.of(couponId)).contains(couponId);
    }

    private List<Long> safeClaimedCouponIds(Long userId, List<Long> couponIds) {
        if (couponIds == null || couponIds.isEmpty()) {
            return List.of();
        }
        List<Long> claimedCouponIds = couponRecordMapper.selectClaimedCouponIds(userId, couponIds);
        return claimedCouponIds == null ? List.of() : claimedCouponIds;
    }

    private int resolveRemainingStock(Coupon coupon) {
        Object stockExist = redisTemplate.opsForValue().get(stockISExist(coupon.getId()));
        if (stockExist != null && "0".equals(String.valueOf(stockExist))) {
            return 0;
        }

        int segmentCount = coupon.getSegmentCount() == null || coupon.getSegmentCount() <= 0
                ? Math.min(10, coupon.getTotalStock() == null ? 0 : coupon.getTotalStock())
                : coupon.getSegmentCount();
        if (segmentCount <= 0) {
            return 0;
        }

        int remaining = 0;
        for (int i = 0; i < segmentCount; i++) {
            Long size = redisTemplate.opsForList().size(segmentStock(coupon.getId(), i));
            remaining += size == null ? 0 : size.intValue();
        }
        return remaining;
    }

    private String metaKey(Long couponId) {
        return COUPON_META.getValue() + couponId;
    }

    private String segmentStock(Long couponId, Integer segment) {
        return COUPON_STOCK_SEGMENT.getValue() + couponId + ":" + segment;
    }

    private String userBuy(Long couponId) {
        return COUPON_USER_TOKEN_HASH.getValue() + couponId;
    }

    private String userSet(Long couponId) {
        return COUPON_USER_SET.getValue() + couponId;
    }

    private String couponBitmap(Long couponId) {
        return COUPON_BITMAP.getValue() + couponId;
    }

    private String stockISExist(Long couponId) {
        return COUPON_IS_EXIST.getValue() + couponId;
    }

    private UserCouponVO enrichCheckoutCoupon(UserCouponVO coupon, long orderAmount) {
        long threshold = toMinorUnits(coupon.getMinPoint());
        long estimatedDiscount = Math.min(orderAmount, toMinorUnits(coupon.getAmount()));
        boolean available = orderAmount > 0 && orderAmount >= threshold && estimatedDiscount > 0;

        coupon.setOrderAmount(orderAmount);
        coupon.setAvailable(available);
        coupon.setEstimatedDiscountAmount(available ? estimatedDiscount : 0L);
        if (available) {
            coupon.setUnavailableReason("");
        } else if (orderAmount <= 0) {
            coupon.setUnavailableReason("当前订单金额为 0，暂不可使用");
        } else if (threshold > orderAmount) {
            coupon.setUnavailableReason("未达到使用门槛");
        } else {
            coupon.setUnavailableReason("当前优惠券暂不可使用");
        }
        return coupon;
    }

    private long toMinorUnits(BigDecimal amount) {
        if (amount == null) {
            return 0L;
        }
        return amount.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValue();
    }
}
