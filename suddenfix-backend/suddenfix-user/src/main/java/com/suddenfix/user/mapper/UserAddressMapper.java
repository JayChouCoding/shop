package com.suddenfix.user.mapper;

import com.suddenfix.user.domain.pojo.UserAddress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserAddressMapper {
    void clearDefaultAddress(Long userId);

    void insertAddress(UserAddress userAddress);

    int deleteAddress(@Param("id") Long id,@Param("userId") Long userId);

    int updateAddress(UserAddress userAddress);

    List<UserAddress> listAddress(Long userId);

    void deleteAllAddress(long userId);
}
