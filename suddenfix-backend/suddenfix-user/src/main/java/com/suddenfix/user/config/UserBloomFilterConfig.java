package com.suddenfix.user.config;

import cn.hutool.core.collection.CollUtil;
import com.suddenfix.common.bloom.BloomFilterInitService;
import com.suddenfix.common.enums.BloomFilterInit;
import com.suddenfix.user.mapper.UserAuthMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserBloomFilterConfig implements BloomFilterInitService<String> {

    private final UserAuthMapper userAuthMapper;
    private final ThreadPoolExecutor threadPoolExecutor;

    @Override
    public String getFilterName() {
        return BloomFilterInit.USER_BLOOM_FILTER.getValue();
    }

    @Override
    public long expectedInsertions() {
        return Long.parseLong(BloomFilterInit.EXPECTED_INSERTIONS.getValue());
    }

    @Override
    public double falseProbability() {
        return Double.parseDouble(BloomFilterInit.FALSE_PROBABILITY.getValue());
    }

    @Override
    public void preloadData(RBloomFilter<String> bloomFilter) {

        List<String> names = userAuthMapper.selectAllNames();
        log.info("开始从数据库拉取全量用户 username...");

        if(CollUtil.isEmpty(names)){
            log.info("数据库暂无用户数据，预热结束。");
            return;
        }

        log.info("成功拉取 {} 条用户 ID，准备多线程写入布隆过滤器...", names.size());

        // 数据分片：将海量 ID 拆分成多个批次
        List<List<String>> batches = CollUtil.split(names,Integer.parseInt(BloomFilterInit.DATA_SPLIT_NUMBER.getValue()));

        // 并发执行：将每个批次包装成一个异步任务，丢入线程池执行
        List<CompletableFuture<Void>> futures = batches.stream().map(batch -> CompletableFuture.runAsync(() -> {
            for (String name : batch) {
                bloomFilter.add(name);
            }
        }, threadPoolExecutor)).toList();

        // 屏障等待：阻塞当前进程，直到所有子线程任务执行完毕
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        log.info("多线程写入布隆过滤器完毕！共拆分为 {} 个批次执行。", batches.size());
    }
}
