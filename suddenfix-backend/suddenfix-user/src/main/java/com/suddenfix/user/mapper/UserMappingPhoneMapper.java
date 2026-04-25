package com.suddenfix.user.mapper;

import com.suddenfix.user.domain.dto.UserRegisterDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMappingPhoneMapper {
    Long selectUserIdByPhone(@Param("phone") String phone);

    int insertMapping(UserRegisterDTO userRegisterDTO);

    void deleteMapping(@Param("phone") String phone);
}
