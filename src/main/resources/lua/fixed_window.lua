local current = redis.call('INCR', KEYS[1])

if current == 1 then
    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
end

local limit = tonumber(ARGV[1])

if current > limit then
    return {0, current, limit, 0}
else
    return {1, current, limit, limit - current}
end