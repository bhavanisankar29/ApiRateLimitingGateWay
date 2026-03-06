package com.application.apiratelimiter.service;

import com.application.apiratelimiter.config.RateLimiterProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterServiceImpl implements RateLimiterService {

    private final JedisPool jedisPool;
    private final RateLimiterProperties properties;

    private static final String TOKENS_KEY_PREFIX = "rate_limiter:tokens:";
    private static final String LAST_REFILL_KEY_PREFIX = "rate_limiter:last_refill:";

    // Lua Script -- Performs refill calculations and token consumption in single atomic operation
    // Returns [allowed tokens(0 or 1), remaining tokens]
    // 0 represents the request is not allowed & 1 represents the request is allowed.
    private static final String RATE_LIMIT_LUA_SCRIPT = """
        local tokens_key = KEYS[1]
        local last_refill_key = KEYS[2]
        local capacity = tonumber(ARGV[1])
        local refill_rate = tonumber(ARGV[2])
        local now = tonumber(ARGV[3])
        
        -- Get current state
        local tokens = tonumber(redis.call('GET', tokens_key))
        local last_refill = tonumber(redis.call('GET', last_refill_key))
        
        -- Initialize if first request
        if tokens == nil or last_refill == nil then
            tokens = capacity
            last_refill = now
            redis.call('SET', tokens_key, tokens)
            redis.call('SET', last_refill_key, last_refill)
        end
        
        -- Calculate token refill (avoid time drift by only advancing by consumed time)
        local elapsed = now - last_refill
        if elapsed > 0 then
            local tokens_to_add = math.floor((elapsed * refill_rate) / 1000)
            if tokens_to_add > 0 then
                tokens = math.min(capacity, tokens + tokens_to_add)
                -- Only update last_refill by the time actually consumed (prevents drift)
                local time_consumed = math.floor((tokens_to_add * 1000) / refill_rate)
                redis.call('SET', tokens_key, tokens)
                redis.call('SET', last_refill_key, last_refill + time_consumed)
            end
        end
        
        -- Try to consume a token
        if tokens > 0 then
            tokens = tokens - 1
            redis.call('SET', tokens_key, tokens)
            return {1, tokens}
        else
            return {0, 0}
        end
        """;

    // Lua Script for getting available tokens(with refill calculation)
    // Return the remaining tokens in the bucket
    private static final String GET_TOKENS_LUA_SCRIPT = """
        local tokens_key = KEYS[1]
        local last_refill_key = KEYS[2]
        local capacity = tonumber(ARGV[1])
        local refill_rate = tonumber(ARGV[2])
        local now = tonumber(ARGV[3])
        
        local tokens = tonumber(redis.call('GET', tokens_key))
        local last_refill = tonumber(redis.call('GET', last_refill_key))
        
        if tokens == nil or last_refill == nil then
            return capacity
        end
        
        local elapsed = now - last_refill
        if elapsed > 0 then
            local tokens_to_add = math.floor((elapsed * refill_rate) / 1000)
            if tokens_to_add > 0 then
                tokens = math.min(capacity, tokens + tokens_to_add)
                local time_consumed = math.floor((tokens_to_add*1000) / refill_rate)
                redis.call('SET', tokens_key, tokens)
                redis.call('SET', last_refill_key, last_refill + time_consumed)
            end
        end
        
        return tokens
        """;

    @Override
    public boolean isAllowed(String clientId) {
        String tokenKey = TOKENS_KEY_PREFIX + clientId;
        String lastRefillKey = LAST_REFILL_KEY_PREFIX + clientId;

        try (Jedis jedis = jedisPool.getResource()) {
            @SuppressWarnings("unchecked")
            List<Long> result = (List<Long>) jedis.eval(
                    RATE_LIMIT_LUA_SCRIPT,
                    2,
                    tokenKey, lastRefillKey,
                    String.valueOf(properties.getCapacity()),
                    String.valueOf(properties.getRefillRate()),
                    String.valueOf(System.currentTimeMillis())
            );

            return result.get(0) == 1;
        } catch (JedisException e) {
            log.error("Redis error during rate limit check for client {}: {}", clientId, e.getMessage());
            // Fail open: allow request if Redis is unavailable
            return true;
        }
    }

    @Override
    public long getCapacity(String clientId) {
        return properties.getCapacity();
    }

    @Override
    public long getAvailableTokens(String clientId) {
        String tokenKey = TOKENS_KEY_PREFIX + clientId;
        String lastRefillKey = LAST_REFILL_KEY_PREFIX + clientId;

        try (Jedis jedis = jedisPool.getResource()) {
            Object result = jedis.eval(
                    GET_TOKENS_LUA_SCRIPT,
                    2,
                    tokenKey, lastRefillKey,
                    String.valueOf(properties.getCapacity()),
                    String.valueOf(properties.getRefillRate()),
                    String.valueOf(System.currentTimeMillis())
            );

            return ((Long) result);
        } catch (JedisException e) {
            log.error("Redis error getting available tokens for client {}: {}", clientId, e.getMessage());
            // Return capacity as fallback
            return properties.getCapacity();
        }
    }
}
