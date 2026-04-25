package com.suddenfix.user.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.suddenfix.common.enums.AccountStatus;
import com.suddenfix.common.constants.RabbitEventConstants;
import com.suddenfix.common.exception.ServiceException;
import com.suddenfix.common.result.Result;
import com.suddenfix.common.utils.JwtUtils;
import com.suddenfix.common.utils.ValidatorUtils;
import com.suddenfix.user.domain.dto.UserLoginDTO;
import com.suddenfix.user.domain.dto.UserRegisterDTO;
import com.suddenfix.user.domain.pojo.User;
import com.suddenfix.user.mapper.UserAuthMapper;
import com.suddenfix.user.mapper.UserMappingEmailMapper;
import com.suddenfix.user.mapper.UserMappingPhoneMapper;
import com.suddenfix.user.mapper.UserMappingUserNameMapper;
import com.suddenfix.user.service.IUserAuthService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static com.suddenfix.common.enums.AccountStatus.Disable;
import static com.suddenfix.common.enums.MsgTopic.TOPIC_USER_CANCELLED;
import static com.suddenfix.common.enums.RedisPreMessage.ALREADY_USED_NAME;
import static com.suddenfix.common.enums.RedisPreMessage.EMAIL_ID_MAPPING;
import static com.suddenfix.common.enums.RedisPreMessage.PHONE_ID_MAPPING;
import static com.suddenfix.common.enums.RedisPreMessage.TOKEN_BLACKLIST;
import static com.suddenfix.common.enums.RedisPreMessage.USERNAME_ID_MAPPING;
import static com.suddenfix.common.enums.ResultCodeEnum.PASSWORD_Missing;
import static com.suddenfix.common.enums.ResultCodeEnum.USERNAME_IS_EXIST;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserAuthServiceImpl implements IUserAuthService {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final UserAuthMapper userAuthMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;
    private final UserMappingUserNameMapper userMappingUserNameMapper;
    private final UserMappingPhoneMapper userMappingPhoneMapper;
    private final UserMappingEmailMapper userMappingEmailMapper;
    private final RabbitTemplate rabbitTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> registerUser(UserRegisterDTO userRegisterDTO) {
        if (StrUtil.isBlank(userRegisterDTO.getUsername()) || StrUtil.isBlank(userRegisterDTO.getPassword())) {
            return Result.fail(PASSWORD_Missing);
        }

        RLock lock = redissonClient.getLock(ALREADY_USED_NAME + userRegisterDTO.getUsername());
        lock.lock();
        try {
            Long existedUserId = userMappingUserNameMapper.selectUserIdByUsername(userRegisterDTO.getUsername());
            if (existedUserId != null) {
                return Result.fail(USERNAME_IS_EXIST);
            }

            if (StrUtil.isNotBlank(userRegisterDTO.getPhone())
                    && userMappingPhoneMapper.selectUserIdByPhone(userRegisterDTO.getPhone()) != null) {
                return Result.fail("手机号已注册");
            }

            if (StrUtil.isNotBlank(userRegisterDTO.getEmail())
                    && userMappingEmailMapper.selectUserIdByEmail(userRegisterDTO.getEmail()) != null) {
                return Result.fail("邮箱已注册");
            }

            userRegisterDTO.setId(IdUtil.getSnowflakeNextId());
            userRegisterDTO.setStatus(AccountStatus.NORMAL.getValue());
            userRegisterDTO.setRole(normalizeRole(userRegisterDTO.getRole()));
            userRegisterDTO.setPassword(PASSWORD_ENCODER.encode(userRegisterDTO.getPassword()));
            userRegisterDTO.setCreateTime(new Date());
            userRegisterDTO.setUpdateTime(new Date());

            int insertRow = userAuthMapper.insertUser(userRegisterDTO);
            if (insertRow <= 0) {
                throw new ServiceException("新增用户失败");
            }

            userMappingUserNameMapper.insertMapping(userRegisterDTO);
            if (StrUtil.isNotBlank(userRegisterDTO.getPhone())) {
                userMappingPhoneMapper.insertMapping(userRegisterDTO);
            }
            if (StrUtil.isNotBlank(userRegisterDTO.getEmail())) {
                userMappingEmailMapper.insertMapping(userRegisterDTO);
            }

            redisTemplate.opsForValue().set(USERNAME_ID_MAPPING.getValue() + userRegisterDTO.getUsername(), userRegisterDTO.getId());
            if (StrUtil.isNotBlank(userRegisterDTO.getPhone())) {
                redisTemplate.opsForValue().set(PHONE_ID_MAPPING.getValue() + userRegisterDTO.getPhone(), userRegisterDTO.getId());
            }
            if (StrUtil.isNotBlank(userRegisterDTO.getEmail())) {
                redisTemplate.opsForValue().set(EMAIL_ID_MAPPING.getValue() + userRegisterDTO.getEmail(), userRegisterDTO.getId());
            }
            return Result.success();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public Result<String> login(UserLoginDTO userLoginDTO) {
        if (userLoginDTO.getPassword() == null) {
            return Result.fail(PASSWORD_Missing);
        }

        int accountType = ValidatorUtils.getAccountType(userLoginDTO.getAccount());
        Long id = null;
        String account = userLoginDTO.getAccount();

        switch (accountType) {
            case 0:
                String userKey = USERNAME_ID_MAPPING.getValue() + account;
                id = (Long) redisTemplate.opsForValue().get(userKey);
                if (id == null) {
                    id = userMappingUserNameMapper.selectUserIdByUsername(account);
                    if (id != null) {
                        redisTemplate.opsForValue().set(userKey, id);
                    }
                }
                break;
            case 1:
                String phoneKey = PHONE_ID_MAPPING.getValue() + account;
                id = (Long) redisTemplate.opsForValue().get(phoneKey);
                if (id == null) {
                    id = userMappingPhoneMapper.selectUserIdByPhone(account);
                    if (id != null) {
                        redisTemplate.opsForValue().set(phoneKey, id);
                    }
                }
                break;
            case 2:
                String emailKey = EMAIL_ID_MAPPING.getValue() + account;
                id = (Long) redisTemplate.opsForValue().get(emailKey);
                if (id == null) {
                    id = userMappingEmailMapper.selectUserIdByEmail(account);
                    if (id != null) {
                        redisTemplate.opsForValue().set(emailKey, id);
                    }
                }
                break;
            default:
                return Result.fail("不支持的账号类型");
        }

        if (id == null) {
            return Result.fail("账号不存在或密码错误");
        }

        User user = userAuthMapper.selectUserName(id);
        if (user == null || !PASSWORD_ENCODER.matches(userLoginDTO.getPassword(), user.getPassword())) {
            return Result.fail("账号不存在或密码错误");
        }

        String token = JwtUtils.createToken(user.getId(), user.getUsername(), normalizeRole(user.getRole()));
        return Result.success(token);
    }

    @Override
    public void logout(String token) {
        try {
            Claims claims = JwtUtils.parseToken(token);
            if (claims == null) {
                return;
            }
            long expirationTime = claims.getExpiration().getTime();
            long systemTime = System.currentTimeMillis();
            long remainTime = expirationTime - systemTime;

            if (remainTime > 0) {
                redisTemplate.opsForValue().set(TOKEN_BLACKLIST.getValue() + token, 1, remainTime, TimeUnit.MILLISECONDS);
                log.info("Token 已加入黑名单，剩余存活时间 {} ms", remainTime);
            }
        } catch (Exception e) {
            log.warn("退出登录时解析 Token 失败或已过期", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long userId, String token) {
        User user = userAuthMapper.selectUserName(userId);

        if (user == null || user.getStatus() == Disable.getValue()) {
            throw new ServiceException("账号不存在或已注销");
        }

        int updateRow = userAuthMapper.updateAccountStatus(user);
        if (updateRow <= 0) {
            throw new ServiceException("账号注销失败");
        }

        if (user.getUsername() != null && !user.getUsername().isEmpty()) {
            userMappingUserNameMapper.deleteMapping(user.getUsername());
            redisTemplate.delete(USERNAME_ID_MAPPING.getValue() + user.getUsername());
            redisTemplate.delete(ALREADY_USED_NAME + user.getUsername());
        }

        if (user.getPhone() != null && !user.getPhone().isEmpty()) {
            userMappingPhoneMapper.deleteMapping(user.getPhone());
            redisTemplate.delete(PHONE_ID_MAPPING.getValue() + user.getPhone());
        }

        if (user.getEmail() != null && !user.getEmail().isEmpty()) {
            userMappingEmailMapper.deleteMapping(user.getEmail());
            redisTemplate.delete(EMAIL_ID_MAPPING.getValue() + user.getEmail());
        }

        this.logout(token);
        rabbitTemplate.convertAndSend(RabbitEventConstants.EVENT_EXCHANGE, TOPIC_USER_CANCELLED.getTopic(), String.valueOf(userId));

        log.info("【用户服务】用户 {} 注销成功，映射与缓存已清理，并已发送脱敏广播", userId);
    }

    private Integer normalizeRole(Integer role) {
        return role != null && role == 1 ? 1 : 0;
    }
}
