-- Stream消息队列
-- 参数列表
local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]

local stockKey = "seckill:stock:" .. voucherId
local orderKey = "seckill:order:" .. userId

if (tonumber(redis.call("get", stockKey)) <= 0) then
    return 1
end

if (redis.call("sismember",orderKey,userId)==1) then
    return 2
end

redis.call("incrby",stockKey,-1)

redis.call("sadd",orderKey,userId)

redis.call("xadd","stream.orders","*","voucherId",voucherId,"userId",userId,"id",orderId)

return 0
--[[ 阻塞队列做法
-- 参数列表
local voucherId = ARGV[1]
local userId = ARGV[2]

local stockKey = "seckill:stock:" .. voucherId
local orderKey = "seckill:order:" .. userId

if (tonumber(redis.call("get", stockKey)) <= 0) then
    return 1
end

if (redis.call("sismember",orderKey,userId)==1) then
    return 2
end

redis.call("incrby",stockKey,-1)

redis.call("sadd",orderKey,userId)

return 0]]
