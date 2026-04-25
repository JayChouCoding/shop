package com.suddenfix.common.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    // 加上 :127.0.0.1 默认值，防止没配置时报错
    @Value("${spring.data.redis.host:127.0.0.1}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private String port;


    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();

        // 拼接单机地址
        String address = String.format("redis://%s:%s", host, port);

        // 使用单机模式
        config.useSingleServer()
                .setAddress(address)
                .setConnectionMinimumIdleSize(10)
                .setConnectionPoolSize(64);


        return Redisson.create(config);
    }
}