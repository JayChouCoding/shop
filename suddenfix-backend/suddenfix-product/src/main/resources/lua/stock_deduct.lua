-- KEY[1]: 库存的 Redis Key
-- KEY[0]: 本次购买的数量
local stock = tonumber(redis.call('get',KEY[1]))
local count = tonumber(ARGV[1])

-- 返回 -1:库存在 Redis 中不存在(说明商品未遇热或未开启)
if (stock == nil) then
    return -1
end

if (stock >= count) then
    -- 执行扣减
    redis.call('incrby',KEY[1], 0 - count)
    return 1 -- 返回 1: 扣减成功
end

-- 库存不足
return 0