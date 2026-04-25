package com.suddenfix.user.mapper;

import com.suddenfix.user.domain.dto.UserRegisterDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMappingUserNameMapper {
    Long selectUserIdByUsername(@Param("username") String username);

    int insertMapping(UserRegisterDTO userRegisterDTO);

    void deleteMapping(@Param("username") String username);
}
