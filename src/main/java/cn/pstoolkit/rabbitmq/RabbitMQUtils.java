package cn.pstoolkit.rabbitmq;

import cn.pstoolkit.gson.GsonUtils;
import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Production-focused RabbitMQ utility methods covering common producer/consumer
 * operations while keeping the API simple and explicit.
 */
public final class RabbitMQUtils {
    private static final Logger log = LoggerFactory.getLogger(RabbitMQUtils.class);

    private RabbitMQUtils() {}

    // --------------- Channel / QoS ---------------

    public static Channel createChannel() throws IOException {
        return RabbitMQManager.getConnection().createChannel();
    }

    public static Channel createChannel(boolean confirmSelect) throws IOException {
        Channel ch = createChannel();
        if (confirmSelect) {
            ch.confirmSelect();
        }
        return ch;
    }

    public static void setQos(Channel channel, int prefetchCount) throws IOException {
        if (prefetchCount > 0) {
            channel.basicQos(prefetchCount);
        }
    }

    // --------------- Topology (exchanges / queues / bindings) ---------------

    public static void declareExchange(Channel channel, String exchange, BuiltinExchangeType type, boolean durable) throws IOException {
        channel.exchangeDeclare(exchange, type, durable);
    }

    public static void declareExchange(Channel channel, String exchange, BuiltinExchangeType type,
                                       boolean durable, boolean autoDelete, Map<String, Object> args) throws IOException {
        channel.exchangeDeclare(exchange, type, durable, autoDelete, args == null ? new HashMap<>() : args);
    }

    public static void deleteExchange(Channel channel, String exchange) throws IOException {
        channel.exchangeDelete(exchange);
    }

    public static void declareQueue(Channel channel, String queue, boolean durable) throws IOException {
        boolean exclusive = false;
        boolean autoDelete = false;
        Map<String, Object> args = new HashMap<>();
        channel.queueDeclare(queue, durable, exclusive, autoDelete, args);
    }

    public static void declareQueue(Channel channel, String queue, boolean durable, boolean exclusive,
                                    boolean autoDelete, Map<String, Object> args) throws IOException {
        channel.queueDeclare(queue, durable, exclusive, autoDelete, args == null ? new HashMap<>() : args);
    }

    /**
     * Declare a queue with common advanced arguments like DLX, TTL, priority, and max length.
     */
    public static void declareQueueWithArgs(Channel channel,
                                            String queue,
                                            boolean durable,
                                            boolean exclusive,
                                            boolean autoDelete,
                                            String deadLetterExchange,
                                            String deadLetterRoutingKey,
                                            Integer messageTtlMs,
                                            Integer maxPriority,
                                            Integer maxLength,
                                            Integer maxLengthBytes,
                                            Map<String, Object> extraArgs) throws IOException {
        Map<String, Object> args = new HashMap<>();
        if (extraArgs != null) args.putAll(extraArgs);
        if (deadLetterExchange != null && !deadLetterExchange.isEmpty()) {
            args.put("x-dead-letter-exchange", deadLetterExchange);
        }
        if (deadLetterRoutingKey != null && !deadLetterRoutingKey.isEmpty()) {
            args.put("x-dead-letter-routing-key", deadLetterRoutingKey);
        }
        if (messageTtlMs != null && messageTtlMs > 0) {
            args.put("x-message-ttl", messageTtlMs);
        }
        if (maxPriority != null && maxPriority > 0) {
            args.put("x-max-priority", maxPriority);
        }
        if (maxLength != null && maxLength > 0) {
            args.put("x-max-length", maxLength);
        }
        if (maxLengthBytes != null && maxLengthBytes > 0) {
            args.put("x-max-length-bytes", maxLengthBytes);
        }
        channel.queueDeclare(queue, durable, exclusive, autoDelete, args);
    }

    public static void bindQueue(Channel channel, String queue, String exchange, String routingKey) throws IOException {
        channel.queueBind(queue, exchange, routingKey);
    }

    public static void unbindQueue(Channel channel, String queue, String exchange, String routingKey) throws IOException {
        channel.queueUnbind(queue, exchange, routingKey, null);
    }

    public static void purgeQueue(Channel channel, String queue) throws IOException {
        channel.queuePurge(queue);
    }

    public static void deleteQueue(Channel channel, String queue) throws IOException {
        channel.queueDelete(queue);
    }

    // --------------- Publish ---------------

    public static void publish(Channel channel, String exchange, String routingKey, byte[] body,
                               AMQP.BasicProperties props, boolean mandatory) throws IOException {
        channel.basicPublish(exchange, routingKey, mandatory, props, body);
    }

    public static void publishString(Channel channel, String exchange, String routingKey, String body,
                                     AMQP.BasicProperties props, boolean mandatory) throws IOException {
        byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        publish(channel, exchange, routingKey, bytes, props, mandatory);
    }

    public static void publishText(Channel channel, String exchange, String routingKey, String text,
                                   boolean persistent) throws IOException {
        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .contentType("text/plain")
                .contentEncoding("UTF-8")
                .deliveryMode(persistent ? 2 : 1)
                .build();
        publishString(channel, exchange, routingKey, text, props, false);
    }

    public static void publishJson(Channel channel, String exchange, String routingKey, Object payload,
                                   boolean persistent) throws IOException {
        String json = GsonUtils.toJson(payload);
        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .contentType("application/json")
                .contentEncoding("UTF-8")
                .deliveryMode(persistent ? 2 : 1)
                .build();
        publishString(channel, exchange, routingKey, json, props, false);
    }

    public static void publishWithHeaders(Channel channel, String exchange, String routingKey, byte[] body,
                                          Map<String, Object> headers, boolean persistent, boolean mandatory) throws IOException {
        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .headers(headers)
                .deliveryMode(persistent ? 2 : 1)
                .build();
        publish(channel, exchange, routingKey, body, props, mandatory);
    }

    public static void enableConfirms(Channel channel) throws IOException {
        channel.confirmSelect();
    }

    public static void waitForConfirms(Channel channel) throws IOException, InterruptedException {
        channel.waitForConfirmsOrDie();
    }

    public static void publishAndConfirm(Channel channel, String exchange, String routingKey, byte[] body,
                                         AMQP.BasicProperties props, boolean mandatory) throws IOException, InterruptedException {
        if (!channel.waitForConfirms()) { // ensure confirm mode if not already
            channel.confirmSelect();
        }
        channel.basicPublish(exchange, routingKey, mandatory, props, body);
        channel.waitForConfirmsOrDie();
    }

    /**
     * Simple RPC helper: publish a request and wait for a single reply on a temporary reply queue.
     * Returns null on timeout.
     */
    public static byte[] rpcCall(Channel channel, String exchange, String routingKey, byte[] requestBody,
                                 int timeoutMs) throws IOException, InterruptedException {
        String replyQueue = channel.queueDeclare().getQueue();
        String correlationId = UUID.randomUUID().toString();

        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .correlationId(correlationId)
                .replyTo(replyQueue)
                .build();

        final BlockingQueue<byte[]> response = new ArrayBlockingQueue<>(1);
        String ctag = channel.basicConsume(replyQueue, true, (consumerTag, message) -> {
            AMQP.BasicProperties p = message.getProperties();
            if (p != null && correlationId.equals(p.getCorrelationId())) {
                response.offer(message.getBody());
            }
        }, consumerTag -> {});

        channel.basicPublish(exchange, routingKey, false, props, requestBody);

        try {
            byte[] resp = response.poll(timeoutMs <= 0 ? 30000 : timeoutMs, TimeUnit.MILLISECONDS);
            return resp;
        } finally {
            try { channel.basicCancel(ctag); } catch (Exception ignored) {}
            // replyQueue is auto-delete (server-named) so no explicit delete needed
        }
    }

    // --------------- Consume / Ack ---------------

    public static String consume(Channel channel, String queue, boolean autoAck,
                                 DeliverCallback deliverCallback, CancelCallback cancelCallback) throws IOException {
        return channel.basicConsume(queue, autoAck, deliverCallback, cancelCallback);
    }

    public static String consumeManualAck(Channel channel, String queue, int prefetch,
                                          DeliverCallback deliverCallback, CancelCallback cancelCallback) throws IOException {
        setQos(channel, prefetch);
        return channel.basicConsume(queue, false, deliverCallback, cancelCallback);
    }

    public static GetResponse get(Channel channel, String queue, boolean autoAck) throws IOException {
        return channel.basicGet(queue, autoAck);
    }

    public static String getString(Channel channel, String queue) throws IOException {
        GetResponse r = get(channel, queue, true);
        if (r == null) return null;
        return new String(r.getBody(), StandardCharsets.UTF_8);
    }

    public static <T> T getJson(Channel channel, String queue, Class<T> clazz) throws IOException {
        GetResponse r = get(channel, queue, true);
        if (r == null) return null;
        String s = new String(r.getBody(), StandardCharsets.UTF_8);
        return GsonUtils.fromJson(s, clazz);
    }

    public static void ack(Channel channel, long deliveryTag, boolean multiple) throws IOException {
        channel.basicAck(deliveryTag, multiple);
    }

    public static void nack(Channel channel, long deliveryTag, boolean multiple, boolean requeue) throws IOException {
        channel.basicNack(deliveryTag, multiple, requeue);
    }

    public static void reject(Channel channel, long deliveryTag, boolean requeue) throws IOException {
        channel.basicReject(deliveryTag, requeue);
    }

    // --------------- Properties helpers ---------------

    public static AMQP.BasicProperties.Builder newProperties() {
        return new AMQP.BasicProperties.Builder();
    }

    public static AMQP.BasicProperties jsonProperties(boolean persistent, String correlationId, String replyTo,
                                                      Map<String, Object> headers) {
        return newProperties()
                .contentType("application/json")
                .contentEncoding("UTF-8")
                .deliveryMode(persistent ? 2 : 1)
                .correlationId(correlationId)
                .replyTo(replyTo)
                .headers(headers)
                .build();
    }

    public static AMQP.BasicProperties textProperties(boolean persistent) {
        return newProperties()
                .contentType("text/plain")
                .contentEncoding("UTF-8")
                .deliveryMode(persistent ? 2 : 1)
                .build();
    }

    // --------------- Close ---------------

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
