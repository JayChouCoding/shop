package com.suddenfix.user.mapper;

import com.suddenfix.user.domain.dto.UserRegisterDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMappingEmailMapper {
    Long selectUserIdByEmail(@Param("email") String email);

    int insertMapping(UserRegisterDTO userRegisterDTO);

    void deleteMapping(@Param("email") String email);
}
