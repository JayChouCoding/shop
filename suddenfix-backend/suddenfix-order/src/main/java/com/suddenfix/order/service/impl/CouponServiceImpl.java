package com.suddenfix.order.service.impl;

import cn.hutool.json.JSONUtil;
import com.suddenfix.common.exception.ServiceException;
import com.suddenfix.common.result.Result;
import com.suddenfix.order.domain.dto.CouponPreheatDTO;
import com.suddenfix.order.domain.pojo.Coupon;
import com.suddenfix.order.domain.vo.CouponPreheatVO;
import com.suddenfix.order.mapper.CouponMapper;
import com.suddenfix.order.service.ICouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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

    private String metaKey(Long couponId) {
        return COUPON_META.getValue() + couponId;
    }

    private String segmentStock(Long couponId, Integer segment) {
        return COUPON_STOCK_SEGMENT.getValue() + couponId + segment;
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
}
