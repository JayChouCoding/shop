-- KEYS[1]: 商品预热 hash key
-- KEYS[2]: 兼容旧逻辑的库存 value key
-- KEYS[3]: 兼容旧逻辑的存在标记 key
-- ARGV[1]: 本次购买的数量

local stockStr = redis.call('hget', KEYS[1], 'stock')

if (stockStr == false or stockStr == nil) then
    return -1
end

local stockClean = string.gsub(tostring(stockStr), '"', '')
local countClean = string.gsub(tostring(ARGV[1]), '"', '')

local stock = tonumber(stockClean)
local count = tonumber(countClean)

if (stock == nil or count == nil) then
    return 0
end

if (stock >= count) then
    local remain = stock - count

    redis.call('hset', KEYS[1], 'stock', tostring(remain))
    redis.call('hset', KEYS[1], 'exists', remain > 0 and '1' or '0')

    if (KEYS[2] ~= nil and KEYS[2] ~= '') then
        redis.call('set', KEYS[2], tostring(remain))
    end

    if (KEYS[3] ~= nil and KEYS[3] ~= '') then
        redis.call('set', KEYS[3], remain > 0 and '1' or '0')
    end

    return 1
end

return 0