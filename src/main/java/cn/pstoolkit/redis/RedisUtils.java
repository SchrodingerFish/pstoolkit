package cn.pstoolkit.redis;

import redis.clients.jedis.Jedis;

import java.util.*;

/**
 * Production-ready Redis utility wrapping a thread-safe JedisPool.
 *
 * Usage:
 * - Configure via application.properties, env vars, or -D system properties (see RedisConfigLoader)
 * - Call static methods. Connections are auto-managed via try-with-resources.
 */
public final class RedisUtils {

    private RedisUtils() {}

    // --------------- Key/Value ---------------

    public static String get(String key) {
        try (Jedis jedis = RedisPoolManager.getResource()) {
            return jedis.get(key);
        }
    }

    public static String set(String key, String value) {
        try (Jedis jedis = RedisPoolManager.getResource()) {
            return jedis.set(key, value);
        }
    }

    public static String setEx(String key, int seconds, String value) {
        try (Jedis jedis = RedisPoolManager.getResource()) {
            return jedis.setex(key, seconds, value);
        }
    }

    public static Long del(String... keys) {
        try (Jedis jedis = RedisPoolManager.getResource()) {
            return jedis.del(keys);
        }
    }

    public static Boolean exists(String key) {
        try (Jedis jedis = RedisPoolManager.getResource()) {
            return jedis.exists(key);
        }
    }

    public static Long expire(String key, int seconds) {
        try (Jedis jedis = RedisPoolManager.getResource()) {
            return jedis.expire(key, seconds);
        }
    }

    public static Long ttl(String key) {
        try (Jedis jedis = RedisPoolManager.getResource()) {
            return jedis.ttl(key);
        }
    }

    public static Long incrBy(String key, long delta) {
        try (Jedis jedis = RedisPoolManager.getResource()) {
            return jedis.incrBy(key, delta);
        }
    }

    public static Long decrBy(String key, long delta) {
        try (Jedis jedis = RedisPoolManager.getResource()) {
            return jedis.decrBy(key, delta);
        }
    }

    // --------------- Hashes ---------------

    public static Long hset(String key, String field, String value) {
        try (Jedis jedis = RedisPoolManager.getResource()) {
            return jedis.hset(key, field, value);
        }
    }

    public static String hget(String key, String field) {
        try (Jedis jedis = RedisPoolManager.getResource()) {
            return jedis.hget(key, field);
        }
    }

    public static String hmset(String key, Map<String, String> map) {
        try (Jedis jedis = RedisPoolManager.getResource()) {
            return jedis.hmset(key, map);
        }
    }

    public static List<String> hmget(String key, String... fields) {
        try (Jedis jedis = RedisPoolManager.getResource()) {
            return jedis.hmget(key, fields);
        }
    }

    public static Map<String, String> hgetAll(String key) {
        try (Jedis jedis = RedisPoolManager.getResource()) {
            return jedis.hgetAll(key);
        }
    }

    public static Long hdel(String key, String... fields) {
        try (Jedis jedis = RedisPoolManager.getResource()) {
            return jedis.hdel(key, fields);
        }
    }

    // --------------- Lists ---------------

    public static Long lpush(String key, String... values) {
        try (Jedis jedis = RedisPoolManager.getResource()) {
            return jedis.lpush(key, values);
        }
    }

    public static String rpop(String key) {
        try (Jedis jedis = RedisPoolManager.getResource()) {
            return jedis.rpop(key);
        }
    }

    public static List<String> blpop(int timeoutSeconds, String... keys) {
        try (Jedis jedis = RedisPoolManager.getResource()) {
            return jedis.blpop(timeoutSeconds, keys);
        }
    }

    // --------------- Sets ---------------

    public static Long sadd(String key, String... members) {
        try (Jedis jedis = RedisPoolManager.getResource()) {
            return jedis.sadd(key, members);
        }
    }

    public static Long srem(String key, String... members) {
        try (Jedis jedis = RedisPoolManager.getResource()) {
            return jedis.srem(key, members);
        }
    }

    public static Set<String> smembers(String key) {
        try (Jedis jedis = RedisPoolManager.getResource()) {
            return jedis.smembers(key);
        }
    }

    // --------------- Pub/Sub (basic publish) ---------------

    public static Long publish(String channel, String message) {
        try (Jedis jedis = RedisPoolManager.getResource()) {
            return jedis.publish(channel, message);
        }
    }

    // --------------- Scripting ---------------

    public static Object eval(String script, List<String> keys, List<String> args) {
        try (Jedis jedis = RedisPoolManager.getResource()) {
            return jedis.eval(script, keys, args);
        }
    }

    // --------------- Health check ---------------

    public static boolean ping() {
        try (Jedis jedis = RedisPoolManager.getResource()) {
            String pong = jedis.ping();
            return "PONG".equalsIgnoreCase(pong);
        }
    }

    // --------------- Distributed lock (SET NX EX) ---------------

    /**
     * Acquire a distributed lock.
     * @param lockKey redis key for the lock
     * @param requestId unique identifier for owner (e.g., UUID)
     * @param expireSeconds TTL to avoid deadlocks
     * @return true if acquired
     */
    public static boolean acquireLock(String lockKey, String requestId, int expireSeconds) {
        if (expireSeconds <= 0) expireSeconds = 30;
        try (Jedis jedis = RedisPoolManager.getResource()) {
            // Equivalent to: SET key value EX <seconds> NX
            redis.clients.jedis.params.SetParams params = new redis.clients.jedis.params.SetParams().nx().ex(expireSeconds);
            String res = jedis.set(lockKey, requestId, params);
            return "OK".equalsIgnoreCase(res);
        }
    }

    /**
     * Release a distributed lock by verifying ownership using a Lua script.
     */
    public static boolean releaseLock(String lockKey, String requestId) {
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        try (Jedis jedis = RedisPoolManager.getResource()) {
            Object result = jedis.eval(script, Collections.singletonList(lockKey), Collections.singletonList(requestId));
            if (result instanceof Long) {
                return (Long) result > 0L;
            }
            return false;
        }
    }
}
