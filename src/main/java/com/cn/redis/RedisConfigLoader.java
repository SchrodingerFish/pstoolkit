package com.cn.redis;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Lightweight configuration loader with sensible precedence for production use.
 *
 * Precedence (highest first):
 * 1) JVM system properties (e.g. -Dredis.host=10.0.0.5)
 * 2) Environment variables (e.g. REDIS_HOST=10.0.0.5)
 * 3) application.properties on classpath
 * 4) Provided default value
 */
public final class RedisConfigLoader {

    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in != null) {
                PROPS.load(in);
            }
        } catch (IOException ignored) {
            // Silently ignore; defaults and other sources will be used
        }
    }

    private RedisConfigLoader() {
    }

    public static String getString(String key, String defaultValue) {
        String fromSys = System.getProperty(key);
        if (fromSys != null) return fromSys;
        String envKey = key.toUpperCase().replace('.', '_');
        String fromEnv = System.getenv(envKey);
        if (fromEnv != null) return fromEnv;
        String fromProps = PROPS.getProperty(key);
        return fromProps != null ? fromProps : defaultValue;
    }

    public static int getInt(String key, int defaultValue) {
        String v = getString(key, null);
        if (v == null) return defaultValue;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static long getLong(String key, long defaultValue) {
        String v = getString(key, null);
        if (v == null) return defaultValue;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        String v = getString(key, null);
        if (v == null) return defaultValue;
        return "true".equalsIgnoreCase(v) || "1".equals(v);
    }
}
