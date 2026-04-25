package com.suddenfix.order.mapper;

import com.suddenfix.order.domain.pojo.Msg;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface MsgMapper {
    int insertMsg(Msg msg);

    List<Msg> selectPendingMsg(@Param("date") Date date,
                               @Param("batchSize") Integer batchSize,
                               @Param("maxRetryCount") Integer maxRetryCount);

    void updateMsgDead(@Param("msgId") Long msgId,@Param("businessId") Long businessId);

    void updateMsgSend(@Param("msgId") Long msgId,@Param("businessId") Long businessId);

    void updateMsgRelay(@Param("msgId") Long msgId,@Param("businessId") Long businessId,@Param("nextRelayTime") Date nextRelayTime);
}
