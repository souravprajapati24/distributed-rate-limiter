local now       = tonumber(ARGV[3])
local window_ms = tonumber(ARGV[2])
local limit     = tonumber(ARGV[1])
local cutoff    = now - window_ms

redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', cutoff)

local count = redis.call('ZCARD', KEYS[1])

if count >= limit then
    return {0, count, limit, 0}
end

local seq_key = KEYS[1] .. ':seq'
local seq = redis.call('INCR', seq_key)

redis.call('PEXPIRE', seq_key, window_ms)

local member = now .. "-" .. seq

redis.call('ZADD', KEYS[1], now, member)
redis.call('PEXPIRE', KEYS[1], window_ms)

return {1, count + 1, limit, limit - count - 1}