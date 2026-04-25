package com.suddenfix.gateway.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suddenfix.common.result.Result;
import com.suddenfix.common.enums.ResultCodeEnum;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 网关统一异常处理器
 * 作用：拦截网关层抛出的所有异常，并将其转换为项目统一的 Result JSON 格式返回给前端
 */
@Component
public class JsonExceptionHandler implements ErrorWebExceptionHandler {
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        // 检查响应是否已经提交
        // 如果已经提交，则直接抛出异常，不再重复处理
        if(response.isCommitted()){
            return Mono.error(ex);
        }

        // 设置响应头为 JSON 格式，防止浏览器将其解析为普通文本或 HTML
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // 使用 WebFlux 的 writeWith 异步写入响应体
        return response.writeWith(Mono.fromSupplier(() -> {
            DataBufferFactory factory = response.bufferFactory();
            try {
                // 构建统一返回对象 Result
                Result<Object> fail = Result.fail();
                fail.setCode(ResultCodeEnum.GATEWAY_ERROR.getCode());
                fail.setMessage(ResultCodeEnum.UNAUTHORIZED.getMessage() + ex.getMessage());

                // 使用 Jackson 将 Java 对象序列化为字节数组
                ObjectMapper objectMapper = new ObjectMapper();
                byte[] bytes = objectMapper.writeValueAsBytes(fail);

                // 将字节数组包装进 DataBuffer 返回给前端
                return factory.wrap(bytes);
            } catch (Exception e) {
                // 万一序列化 Result 对象都失败了，再进行最后的硬编码保底
                String fallback = String.format(
                        "{\"code\":%d,\"message\":\"%s\",\"data\":null,\"timestamp\":%d}",
                        ResultCodeEnum.FAIL.getCode(),
                        ResultCodeEnum.FAIL.getMessage(),
                        System.currentTimeMillis()
                );
                return factory.wrap(fallback.getBytes(StandardCharsets.UTF_8));
            }
        }));
    }
}
