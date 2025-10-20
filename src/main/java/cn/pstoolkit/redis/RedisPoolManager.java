package cn.pstoolkit.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Thread-safe singleton managing Jedis connection pool.
 */
public final class RedisPoolManager {

    private static volatile JedisPool POOL;

    static {
        initPool();
        // Ensure pool is closed gracefully on JVM shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(RedisPoolManager::close));
    }

    private RedisPoolManager() {
    }

    private static synchronized void initPool() {
        if (POOL != null) return;

        JedisPoolConfig cfg = new JedisPoolConfig();
        cfg.setMaxTotal(RedisConfigLoader.getInt("redis.pool.maxTotal", 64));
        cfg.setMaxIdle(RedisConfigLoader.getInt("redis.pool.maxIdle", 16));
        cfg.setMinIdle(RedisConfigLoader.getInt("redis.pool.minIdle", 4));
        cfg.setMaxWaitMillis(RedisConfigLoader.getLong("redis.pool.maxWaitMillis", 3000));

        cfg.setTestOnBorrow(RedisConfigLoader.getBoolean("redis.pool.testOnBorrow", true));
        cfg.setTestOnReturn(RedisConfigLoader.getBoolean("redis.pool.testOnReturn", false));
        cfg.setTestWhileIdle(RedisConfigLoader.getBoolean("redis.pool.testWhileIdle", true));
        cfg.setBlockWhenExhausted(RedisConfigLoader.getBoolean("redis.pool.blockWhenExhausted", true));

        cfg.setMinEvictableIdleTimeMillis(RedisConfigLoader.getLong("redis.pool.minEvictableIdleTimeMillis", 60_000));
        cfg.setTimeBetweenEvictionRunsMillis(RedisConfigLoader.getLong("redis.pool.timeBetweenEvictionRunsMillis", 30_000));
        cfg.setNumTestsPerEvictionRun(RedisConfigLoader.getInt("redis.pool.numTestsPerEvictionRun", -1));

        String host = RedisConfigLoader.getString("redis.host", "127.0.0.1");
        int port = RedisConfigLoader.getInt("redis.port", 6379);
        String password = RedisConfigLoader.getString("redis.password", "");
        int database = RedisConfigLoader.getInt("redis.database", 0);
        int timeout = RedisConfigLoader.getInt("redis.timeout", 2000);
        String clientName = RedisConfigLoader.getString("redis.clientName", null);
        boolean ssl = RedisConfigLoader.getBoolean("redis.ssl", false);

        if (password != null && password.trim().isEmpty()) {
            password = null; // Jedis expects null if no password
        }
        if (clientName != null && clientName.trim().isEmpty()) {
            clientName = null;
        }

        if (ssl) {
            // SSL enabled; use constructor with SSL flag. Advanced SSL params can be wired if needed.
            POOL = new JedisPool(cfg, host, port, timeout, password, database, clientName, true, null, null, null);
        } else {
            // Non-SSL
            POOL = new JedisPool(cfg, host, port, timeout, password, database, clientName);
        }

        // Optional warmup: borrow and return one connection to validate pool creation
        try (Jedis jedis = POOL.getResource()) {
            jedis.ping();
        } catch (Exception ignored) {
            // If Redis is down during startup, the pool will still be able to recover later.
        }
    }

    public static Jedis getResource() {
        JedisPool pool = POOL;
        if (pool == null) {
            synchronized (RedisPoolManager.class) {
                if (POOL == null) initPool();
                pool = POOL;
            }
        }
        return pool.getResource();
    }

    public static void close() {
        JedisPool pool = POOL;
        if (pool != null) {
            try {
                pool.close();
            } finally {
                POOL = null;
            }
        }
    }
}
