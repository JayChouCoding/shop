package com.suddenfix.user.listener;

import com.suddenfix.user.config.UserEventRabbitConfig;
import com.suddenfix.user.mapper.UserAddressMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserCancelListener {

    private final UserAddressMapper userAddressMapper;

    @RabbitListener(queues = UserEventRabbitConfig.USER_CANCELLED_QUEUE)
    public void onUserCancel(String payload) {
        try {
            long userId = Long.parseLong(payload);
            userAddressMapper.deleteAllAddress(userId);
        }catch (Exception e) {
            throw e;
        }
    }
}
