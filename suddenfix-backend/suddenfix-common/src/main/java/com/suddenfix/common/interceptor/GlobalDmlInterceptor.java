package com.suddenfix.common.interceptor;

import cn.hutool.core.util.ReflectUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Properties;

@Slf4j
@Component
@Intercepts({
        // 拦截 Executor 的 update 方法（MyBatis 底层 Insert/Update/Delete 均走此方法）
        @Signature(type = Executor.class,method = "update",args = {MappedStatement.class, Object.class}),
})
public class GlobalDmlInterceptor implements Interceptor {
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];
        Object parameter = args[1];

        if(parameter != null){
            SqlCommandType commandType = ms.getSqlCommandType();
            Date now = new Date();

            switch (commandType){
                case INSERT:
                    // 新增操作：自动填充createTime 和 updateTime
                    fillField(parameter,"createTime",now);
                    fillField(parameter,"updateTime",now);
                    break;
                case DELETE:
                case UPDATE:
                    // 更新操作：自动填充updateTime
                    fillField(parameter,"updateTime",now);
                    break;
                default:
                    break;
            }
        }
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }

    /**
     * 使用 Hutool 的反射工具安全填充字段
     */
    private void fillField(Object target,String fieldName,Object value){
        // 判断该对象是否包含指定的字段，防止抛出 NoSuchFieldException
        if(ReflectUtil.hasField(target.getClass(),fieldName)){
            // 获取字段原本的值
            Object originalValue = ReflectUtil.getFieldValue(target, fieldName);

            // 策略：如果原本为空，或者是 updateTime (每次必须刷新)，才进行填充
            if(originalValue == null || "updateTime".equals(fieldName)){
                ReflectUtil.setFieldValue(target,fieldName,value);
            }
        }
    }
}
