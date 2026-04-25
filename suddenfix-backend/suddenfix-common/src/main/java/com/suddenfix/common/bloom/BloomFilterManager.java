package com.suddenfix.common.bloom;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 全局布隆过滤器管理器 (预热引擎)
 * 会在微服务启动时自动运行
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BloomFilterManager implements ApplicationRunner {

    private final RedissonClient redissonClient;

    // Spring 会自动把当前微服务里所有实现了 BloomFilterInitService 接口的 Bean 收集到这个 List 中
    private final List<BloomFilterInitService<?>> initServices;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if(initServices == null || initServices.isEmpty()){
            log.info("当前微服务未检测到需要初始化的布隆过滤器任务");
            return;
        }

        for (BloomFilterInitService<?> service : initServices) {
            String filterName = service.getFilterName();

            // 获取并初始化布隆过滤器
            RBloomFilter<Object> bloomFilter = redissonClient.getBloomFilter(filterName);
            boolean isInit = bloomFilter.tryInit(service.expectedInsertions(), service.falseProbability());
            log.info("布隆过滤器 [{}] 结构初始化状态: {}", filterName, isInit ? "首次创建" : "已存在");

            // 如果数据为空，调用微服务自定义的 preloadData 方法进行预热
            if(bloomFilter.count() == 0){
                log.info("布隆过滤器 [{}] 数据为空，开始从数据库加载预热...", filterName);
                long startTime = System.currentTimeMillis();

                // 执行各个微服务自己写的查库逻辑
                // 这里需要强转一下泛型以匹配接口定义
                ((BloomFilterInitService<Object>) service).preloadData(bloomFilter);

                long endTime = System.currentTimeMillis();
                log.info("布隆过滤器 [{}] 预热完成，目前元素数量: {}, 耗时: {} ms",
                        filterName, bloomFilter.count(), (endTime - startTime));
            }else {
                log.info("布隆过滤器 [{}] 中已有 {} 条数据，跳过预热", filterName, bloomFilter.count());
            }
        }
    }
}
