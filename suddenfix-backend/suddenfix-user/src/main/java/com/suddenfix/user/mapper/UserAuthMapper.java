package com.suddenfix.user.mapper;

import com.suddenfix.user.domain.dto.UserRegisterDTO;
import com.suddenfix.user.domain.pojo.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserAuthMapper {
    User selectUserName(@Param("id") Long id);

    int insertUser(UserRegisterDTO userRegisterDTO);

    List<String> selectAllNames();

    int updateAccountStatus(User user);
}
