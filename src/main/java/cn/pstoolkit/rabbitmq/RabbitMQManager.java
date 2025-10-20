package cn.pstoolkit.rabbitmq;

import cn.pstoolkit.common.ConfigLoader;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RabbitMQManager {
    private static final Logger log = LoggerFactory.getLogger(RabbitMQManager.class);
    private static volatile Connection CONNECTION;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(RabbitMQManager::close));
    }

    private RabbitMQManager() {}

    private static synchronized void initConnection() {
        if (CONNECTION != null && CONNECTION.isOpen()) return;
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(ConfigLoader.getString("rabbitmq.host", "127.0.0.1"));
            factory.setPort(ConfigLoader.getInt("rabbitmq.port", 5672));
            factory.setUsername(ConfigLoader.getString("rabbitmq.username", "guest"));
            factory.setPassword(ConfigLoader.getString("rabbitmq.password", "guest"));
            factory.setVirtualHost(ConfigLoader.getString("rabbitmq.vhost", "/"));
            factory.setRequestedHeartbeat(ConfigLoader.getInt("rabbitmq.requestedHeartbeat", 30));
            factory.setConnectionTimeout(ConfigLoader.getInt("rabbitmq.connectionTimeout", 30000));
            factory.setHandshakeTimeout(ConfigLoader.getInt("rabbitmq.handshakeTimeout", 10000));
            factory.setAutomaticRecoveryEnabled(ConfigLoader.getBoolean("rabbitmq.automaticRecovery", true));
            factory.setNetworkRecoveryInterval(ConfigLoader.getInt("rabbitmq.networkRecoveryInterval", 5000));

            CONNECTION = factory.newConnection();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize RabbitMQ connection", e);
        }
    }

    public static Connection getConnection() {
        Connection conn = CONNECTION;
        if (conn == null || !conn.isOpen()) {
            synchronized (RabbitMQManager.class) {
                if (CONNECTION == null || !CONNECTION.isOpen()) {
                    initConnection();
                }
                conn = CONNECTION;
            }
        }
        return conn;
    }

    public static void close() {
        Connection conn = CONNECTION;
        if (conn != null) {
            try {
                conn.close();
            } catch (Exception e) {
                log.warn("Error closing RabbitMQ connection", e);
            } finally {
                CONNECTION = null;
            }
        }
    }
}
