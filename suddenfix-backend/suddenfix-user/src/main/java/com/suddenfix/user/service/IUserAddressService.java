package com.suddenfix.user.service;

import com.suddenfix.common.result.Result;
import com.suddenfix.user.domain.pojo.UserAddress;

import java.util.List;

public interface IUserAddressService {
    Result<Void> addAddress(UserAddress userAddress, Long userId);

    Result<Void> deleteAddress(Long id, Long userId);

    Result<Void> updateAddress(UserAddress userAddress, Long userId);

    Result<List<UserAddress>> listAddress(Long userId);
}
