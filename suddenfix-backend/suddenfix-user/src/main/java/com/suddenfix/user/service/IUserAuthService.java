package com.suddenfix.user.service;

import com.suddenfix.common.result.Result;
import com.suddenfix.user.domain.dto.UserLoginDTO;
import com.suddenfix.user.domain.dto.UserRegisterDTO;

public interface IUserAuthService {
    Result<Void> registerUser(UserRegisterDTO userRegisterDTO);

    Result<String> login(UserLoginDTO userLoginDTO);

    void logout(String token);

    void cancel(Long userId, String token);
}
