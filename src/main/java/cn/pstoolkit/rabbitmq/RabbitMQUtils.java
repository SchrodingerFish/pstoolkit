package cn.pstoolkit.rabbitmq;

import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public final class RabbitMQUtils {
    private static final Logger log = LoggerFactory.getLogger(RabbitMQUtils.class);

    private RabbitMQUtils() {}

    public static Channel createChannel() throws IOException {
        try {
            return RabbitMQManager.getConnection().createChannel();
        } catch (IOException e) {
            throw e;
        }
    }

    public static void declareExchange(Channel channel, String exchange, BuiltinExchangeType type, boolean durable) throws IOException {
        channel.exchangeDeclare(exchange, type, durable);
    }

    public static void declareQueue(Channel channel, String queue, boolean durable) throws IOException {
        boolean exclusive = false;
        boolean autoDelete = false;
        Map<String, Object> args = new HashMap<>();
        channel.queueDeclare(queue, durable, exclusive, autoDelete, args);
    }

    public static void bindQueue(Channel channel, String queue, String exchange, String routingKey) throws IOException {
        channel.queueBind(queue, exchange, routingKey);
    }

    public static void publish(Channel channel, String exchange, String routingKey, byte[] body, AMQP.BasicProperties props, boolean mandatory) throws IOException {
        channel.basicPublish(exchange, routingKey, mandatory, props, body);
    }

    public static void enableConfirms(Channel channel) throws IOException {
        channel.confirmSelect();
    }

    public static void waitForConfirms(Channel channel) throws IOException, InterruptedException {
        channel.waitForConfirmsOrDie();
    }

    public static String consume(Channel channel, String queue, boolean autoAck, DeliverCallback deliverCallback, CancelCallback cancelCallback) throws IOException {
        return channel.basicConsume(queue, autoAck, deliverCallback, cancelCallback);
    }

    public static void ack(Channel channel, long deliveryTag, boolean multiple) throws IOException {
        channel.basicAck(deliveryTag, multiple);
    }

    public static void nack(Channel channel, long deliveryTag, boolean multiple, boolean requeue) throws IOException {
        channel.basicNack(deliveryTag, multiple, requeue);
    }

    public static void closeQuietly(Channel channel) {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException | TimeoutException e) {
                log.warn("Error closing RabbitMQ channel", e);
            }
        }
    }
}
