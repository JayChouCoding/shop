package com.suddenfix.common.bloom;

import org.redisson.api.RBloomFilter;

public interface BloomFilterInitService<T>{

    /**
     * 定义该布隆过滤器在 Redis 中的 Key 名字
     */
    String getFilterName();

    /**
     * 预计插入的数据量 (决定底层位图的大小)
     */
    long expectedInsertions();

    /**
     * 期望的误判率
     */
    double falseProbability();

    /**
     * 具体的数据加载逻辑 (交由各个微服务自己去查数据库)
     * @param bloomFilter 已经初始化好的布隆过滤器实例
     */
    void preloadData(RBloomFilter<T> bloomFilter);
}
