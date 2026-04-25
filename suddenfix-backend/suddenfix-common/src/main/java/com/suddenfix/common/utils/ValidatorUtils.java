package com.suddenfix.common.utils;


import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.StrUtil;
import com.suddenfix.common.enums.UserAccountTypeEnum;

public class ValidatorUtils {

    public static boolean isMobile(String value){
        return Validator.isMobile(value);
    }

    public static boolean isEmail(String value){
        return Validator.isEmail(value);
    }

    public static int getAccountType(String account){
        if(StrUtil.isBlank(account)){
            return UserAccountTypeEnum.USERNAME.getCode();
        }
        if(isMobile(account)){
            return UserAccountTypeEnum.MOBILE.getCode();
        }
        if(isEmail(account)){
            return UserAccountTypeEnum.EMAIL.getCode();
        }
        return UserAccountTypeEnum.USERNAME.getCode();
    }
}
