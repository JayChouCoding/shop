package com.suddenfix.common.utils;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicLong;

public final class GeneIdGenerator {

    /**
     * 自定义纪元，控制时间位长度并保证 sign bit 始终为 0。
     */
    private static final long CUSTOM_EPOCH = 1704067200000L;

    /**
     * 低 4 位固定作为分片槽位，直接兼容 `% 16` 的分表策略。
     */
    private static final long SHARD_BITS = 4L;
    private static final long SHARD_MASK = (1L << SHARD_BITS) - 1;

    /**
     * 单节点单毫秒内的序列号。
     */
    private static final long SEQUENCE_BITS = 13L;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;

    /**
     * 节点编号，避免多实例碰撞。
     */
    private static final long WORKER_BITS = 5L;
    private static final long MAX_WORKER_ID = (1L << WORKER_BITS) - 1;

    private static final long SEQUENCE_SHIFT = SHARD_BITS;
    private static final long WORKER_SHIFT = SEQUENCE_SHIFT + SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = WORKER_SHIFT + WORKER_BITS;

    private static final long WORKER_ID = resolveWorkerId();
    private static final AtomicLong SHARD_COUNTER = new AtomicLong(System.nanoTime());

    private static long lastTimestamp = -1L;
    private static long sequence = 0L;

    private GeneIdGenerator() {
    }

    /**
     * 63 bit 正数 ID：
     * 41 bit timestamp + 5 bit worker + 13 bit sequence + 4 bit shardSlot
     */
    public static synchronized long generatorId(long routeSeed) {
        long currentTimestamp = System.currentTimeMillis();
        if (currentTimestamp < lastTimestamp) {
            currentTimestamp = waitUntilNextMillis(lastTimestamp);
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                currentTimestamp = waitUntilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = currentTimestamp;

        long delta = currentTimestamp - CUSTOM_EPOCH;
        long shardSlot = computeShardSlot(routeSeed);

        return (delta << TIMESTAMP_SHIFT)
                | (WORKER_ID << WORKER_SHIFT)
                | (sequence << SEQUENCE_SHIFT)
                | shardSlot;
    }

    public static int extractShardSlot(long id) {
        return (int) (id & SHARD_MASK);
    }

    public static int extractDatabaseIndex(long id) {
        return extractShardSlot(id) & 1;
    }

    public static int extractTableIndex(long id) {
        return extractShardSlot(id);
    }

    private static long computeShardSlot(long routeSeed) {
        long mixedSeed = mix64(routeSeed);
        long slot = mixedSeed + SHARD_COUNTER.getAndIncrement();
        return slot & SHARD_MASK;
    }

    private static long mix64(long value) {
        long result = value;
        result ^= (result >>> 33);
        result *= 0xff51afd7ed558ccdL;
        result ^= (result >>> 33);
        result *= 0xc4ceb9fe1a85ec53L;
        result ^= (result >>> 33);
        return result;
    }

    private static long waitUntilNextMillis(long targetTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= targetTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }

    private static long resolveWorkerId() {
        String configured = System.getProperty("suddenfix.id.worker");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("SUDDENFIX_ID_WORKER");
        }
        if (configured != null && !configured.isBlank()) {
            try {
                return Math.floorMod(Long.parseLong(configured.trim()), MAX_WORKER_ID + 1);
            } catch (NumberFormatException ignored) {
                // fallback to hostname hash
            }
        }

        try {
            String hostName = InetAddress.getLocalHost().getHostName();
            String workerIdentity = hostName + "-" + ProcessHandle.current().pid();
            return Math.floorMod(workerIdentity.hashCode(), (int) (MAX_WORKER_ID + 1));
        } catch (Exception ignored) {
            String workerIdentity = "fallback-" + ProcessHandle.current().pid();
            return Math.floorMod(workerIdentity.hashCode(), (int) (MAX_WORKER_ID + 1));
        }
    }
}
