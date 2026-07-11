local max_tokens  = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now         = tonumber(ARGV[3])

local data        = redis.call('HMGET', KEYS[1], 'tokens', 'last_refill_ms')
local tokens       = tonumber(data[1]) or max_tokens
local last_refill  = tonumber(data[2]) or now

local elapsed_sec = math.max(0, (now - last_refill) / 1000.0)
local earned      = elapsed_sec * refill_rate
tokens            = math.min(max_tokens, tokens + earned)

if tokens < 1.0 then

    redis.call('HMSET', KEYS[1], 'tokens', tokens, 'last_refill_ms', now)
    local seconds_to_next_token = math.ceil((1.0 - tokens) / refill_rate)
    return {0, math.floor(tokens), max_tokens,math.floor(tokens), seconds_to_next_token}
end

tokens = tokens - 1.0
redis.call('HMSET', KEYS[1], 'tokens', tokens, 'last_refill_ms', now)
local seconds_to_next_token = tokens < 1.0
    and math.ceil((1.0 - tokens) / refill_rate)
    or 0

return {1, math.floor(tokens), max_tokens, math.floor(tokens), seconds_to_next_token}