package com.suddenfix.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

/**
 * 全局通用线程池配置类
 */
@Configuration
public class ThreadPoolConfig {

    @Value("${thread.core-size:10}")
    private int coreSize;

    @Value("${thread.max-size:50}")
    private int maxSize;

    @Value("${thread.keep-alive:60}")
    private int keepAlive;

    @Value("${thread.queue-capacity:10000}")
    private int queueCapacity;

    @Bean
    public ThreadPoolExecutor threadPoolExecutor(){
        return new ThreadPoolExecutor(
                coreSize,
                maxSize,
                keepAlive,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
