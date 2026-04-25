-- KEYS[1]: 库存的 Redis Key
-- ARGV[1]: 本次购买的数量

local stockStr = redis.call('get', KEYS[1])

-- 如果 key 不存在，redis 会返回 false
if (stockStr == false or stockStr == nil) then
    return -1
end

-- 核心修复：使用 string.gsub 强行去除 Java 序列化可能带来的字面双引号
local stockClean = string.gsub(tostring(stockStr), '"', '')
local countClean = string.gsub(tostring(ARGV[1]), '"', '')

local stock = tonumber(stockClean)
local count = tonumber(countClean)

-- 如果解析后仍然是 nil，说明数据格式彻底错了，直接返回失败
if (stock == nil or count == nil) then
    return 0
end

if (stock >= count) then
    -- 执行扣减
    redis.call('incrby', KEYS[1], 0 - count)
    return 1 -- 返回 1: 扣减成功
end

-- 库存不足
return 0