package com.suddenfix.pay.mapper;

import com.suddenfix.pay.domain.pojo.Pay;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PayMapper {
    int insertPay(Pay pay);

    Pay selectPayByOrderIdAndUserId(@Param("orderId") Long orderId,@Param("userId") Long userId);

    Pay selectPayByOrderId(@Param("orderId") Long orderId);

    int updatePaySuccess(@Param("outTradeNo") String outTradeNo,@Param("channelTradeNo") String channelTradeNo);

    int updatePayRefunded(@Param("outTradeNo") String outTradeNo, @Param("errorMsg") String errorMsg);

    Pay selectPayByOutTradeNo(@Param("outTradeNo") String outTradeNo);

    List<Pay> selectRecentPendingPayByUserId(@Param("userId") Long userId, @Param("limit") Integer limit);
}
