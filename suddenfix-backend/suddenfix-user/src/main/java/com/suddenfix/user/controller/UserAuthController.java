package com.suddenfix.user.controller;

import com.suddenfix.common.result.Result;
import com.suddenfix.user.domain.dto.UserLoginDTO;
import com.suddenfix.user.domain.dto.UserRegisterDTO;
import com.suddenfix.user.service.IUserAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserAuthController {

    private final IUserAuthService userAuthService;

    @PostMapping("/register")
    public Result<Void> registerUser(@Valid @RequestBody UserRegisterDTO userRegisterDTO) {
        return userAuthService.registerUser(userRegisterDTO);
    }

    @PostMapping("/login")
    public Result<String> login(@RequestBody UserLoginDTO userLoginDTO) {
        return userAuthService.login(userLoginDTO);
    }

    @PostMapping("/logout")
    public Result<String> logout(HttpServletRequest request) {
        String token = extractBearerToken(request);
        if (token != null && !token.isBlank()) {
            userAuthService.logout(token);
        }
        return Result.success("已安全退出登录");
    }

    @PostMapping({"/deregister", "/cancel"})
    public Result<String> deregister(HttpServletRequest request) {
        Long userId = resolveUserId(request);
        if (userId == null) {
            return Result.fail("缺少用户身份信息");
        }
        userAuthService.cancel(userId, extractBearerToken(request));
        return Result.success("账号已注销");
    }

    private String extractBearerToken(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token == null || token.isBlank()) {
            return null;
        }
        return token.startsWith("Bearer ") ? token.substring(7) : token;
    }

    private Long resolveUserId(HttpServletRequest request) {
        String userId = request.getHeader("user_id");
        if (userId == null || userId.isBlank()) {
            userId = request.getHeader("userId");
        }
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return Long.valueOf(userId);
    }
}
