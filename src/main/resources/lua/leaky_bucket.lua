local capacity  = tonumber(ARGV[1])
local leak_rate = tonumber(ARGV[2])
local now       = tonumber(ARGV[3])

local data       = redis.call('HMGET', KEYS[1], 'queue_size', 'last_leak_ms')
local queue_size = tonumber(data[1]) or 0
local last_leak  = tonumber(data[2]) or now

local elapsed_sec = math.max(0, (now - last_leak) / 1000.0)
local drained     = math.floor(elapsed_sec * leak_rate)
queue_size        = math.max(0, queue_size - drained)

if queue_size >= capacity then

    redis.call('HMSET', KEYS[1], 'queue_size', queue_size, 'last_leak_ms', now)
    return {0, queue_size, capacity, 0}
end

queue_size = queue_size + 1
redis.call('HMSET', KEYS[1], 'queue_size', queue_size, 'last_leak_ms', now)

return {1, queue_size, capacity, capacity - queue_size}