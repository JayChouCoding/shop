package com.suddenfix.user.controller;

import com.suddenfix.common.result.Result;
import com.suddenfix.user.domain.pojo.UserAddress;
import com.suddenfix.user.service.IUserAddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/address")
@RequiredArgsConstructor
public class UserAddressController {

    private final IUserAddressService userAddressService;

    @PostMapping("/add")
    public Result<Void> addAddress(@RequestBody UserAddress userAddress, @RequestHeader("userId") Long userId) {
        return userAddressService.addAddress(userAddress,userId);
    }

    @DeleteMapping("/delete/{id}")
    public Result<Void> deleteAddress(@PathVariable Long id,@RequestHeader("userId") Long userId){
        return userAddressService.deleteAddress(id,userId);
    }

    @PutMapping("/update")
    public Result<Void> updateAddress(@RequestBody UserAddress userAddress,@RequestHeader("userId") Long userId){
        return userAddressService.updateAddress(userAddress,userId);
    }

    @GetMapping("/list")
    public Result<List<UserAddress>> listAddress(@RequestHeader("userId") Long userId){
        return userAddressService.listAddress(userId);
    }
}
