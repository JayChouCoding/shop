package com.suddenfix.user.service.impl;

import com.suddenfix.common.result.Result;
import com.suddenfix.common.utils.GeneIdGenerator;
import com.suddenfix.user.domain.pojo.UserAddress;
import com.suddenfix.user.mapper.UserAddressMapper;
import com.suddenfix.user.service.IUserAddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.suddenfix.common.enums.RedisPreMessage.USER_ADDRESS;

@Service
@RequiredArgsConstructor
public class UserAddressServiceImpl implements IUserAddressService {

    private final UserAddressMapper userAddressMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    // 一键删除用户地址缓存
    private void clearAddressCache(Long userId){
        redisTemplate.delete(USER_ADDRESS.getValue() + userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> addAddress(UserAddress userAddress, Long userId) {
        userAddress.setUserId(userId);
        long id = GeneIdGenerator.generatorId(userId);
        userAddress.setId(id);

        if(userAddress.getIsDefault() != null && userAddress.getIsDefault() == 1){
            userAddressMapper.clearDefaultAddress(userId);
        }

        userAddressMapper.insertAddress(userAddress);

        clearAddressCache(userId);
        return Result.success();
    }

    @Override
    public Result<Void> deleteAddress(Long id, Long userId) {
        int deleteRow = userAddressMapper.deleteAddress(id,userId);
        if(deleteRow > 0){
            clearAddressCache(userId);
        }
        return Result.success();
    }

    @Override
    public Result<Void> updateAddress(UserAddress userAddress, Long userId) {
        userAddress.setUserId(userId);
        
        int updateRow = userAddressMapper.updateAddress(userAddress);
        if(updateRow > 0){
            if(userAddress.getIsDefault() != null && userAddress.getIsDefault() == 1){
                userAddressMapper.clearDefaultAddress(userId);
            }
            clearAddressCache(userId);
        }
        return Result.success();
    }

    @Override
    public Result<List<UserAddress>> listAddress(Long userId) {
        List<UserAddress> userAddresses = (List<UserAddress>) redisTemplate.opsForValue().get(USER_ADDRESS.getValue() + userId);
        if(userAddresses != null){
            return Result.success(userAddresses);
        }

        List<UserAddress> list = userAddressMapper.listAddress(userId);

        redisTemplate.opsForValue().set(USER_ADDRESS.getValue() + userId, list,1, TimeUnit.DAYS);
        return Result.success(list);
    }
}
