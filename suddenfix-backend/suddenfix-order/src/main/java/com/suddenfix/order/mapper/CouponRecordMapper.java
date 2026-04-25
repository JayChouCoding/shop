package com.suddenfix.order.mapper;

import com.suddenfix.order.domain.pojo.CouponRecord;
import com.suddenfix.order.domain.vo.OrderCouponVO;
import com.suddenfix.order.domain.vo.UserCouponVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface CouponRecordMapper {
    void insertCouponRecord(CouponRecord couponRecord);

    int bindOrderToCoupon(@Param("userId") Long userId,
                          @Param("couponToken") String couponToken,
                          @Param("orderId") Long orderId);

    int markCouponUsedByOrderId(@Param("orderId") Long orderId);

    int rollbackCouponUsedByOrderId(@Param("orderId") Long orderId);

    int clearCouponBindingByOrderId(@Param("orderId") Long orderId);

    CouponRecord selectByOrderId(@Param("orderId") Long orderId);

    CouponRecord selectByUserIdAndCouponToken(@Param("userId") Long userId,
                                              @Param("couponToken") String couponToken);

    OrderCouponVO selectCouponDetailByOrderId(@Param("orderId") Long orderId);

    List<Long> selectClaimedCouponIds(@Param("userId") Long userId, @Param("couponIds") List<Long> couponIds);

    List<UserCouponVO> selectUsableCouponsByUserId(@Param("userId") Long userId, @Param("now") Date now);
}
