package cn.pstoolkit.sample;

import cn.pstoolkit.gson.GsonUtils;
import cn.pstoolkit.poi.ExcelReader;
import cn.pstoolkit.poi.ExcelWriter;
import cn.pstoolkit.rabbitmq.RabbitMQUtils;
import cn.pstoolkit.redis.RedisUtils;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Paths;
import java.util.*;

public class SampleApp {
    public static void main(String[] args) throws Exception {
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("id", 1);
        row1.put("name", "Alice");
        row1.put("score", 95.5);
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("id", 2);
        row2.put("name", "Bob");
        row2.put("score", 88);
        List<Map<String, Object>> rows = Arrays.asList(row1, row2);
        LinkedHashMap<String, String> header = new LinkedHashMap<>();
        header.put("id", "ID");
        header.put("name", "Name");
        header.put("score", "Score");

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ExcelWriter.writeXlsx(bout, "Demo", rows, header);
        byte[] bytes = bout.toByteArray();

        List<Map<String, String>> parsed = ExcelReader.readFirstSheet(new ByteArrayInputStream(bytes), true);
        System.out.println("Excel first row: " + parsed.get(0));

        String json = GsonUtils.toJson(rows);
        System.out.println("JSON: " + json);

        String runRedis = System.getenv("RUN_REDIS_SAMPLE");
        if ("1".equals(runRedis)) {
            RedisUtils.set("pstoolkit:hello", "world");
            String v = RedisUtils.get("pstoolkit:hello");
            System.out.println("Redis value: " + v);
        }

        String runRabbit = System.getenv("RUN_RABBITMQ_SAMPLE");
        if ("1".equals(runRabbit)) {
            Channel ch = null;
            try {
                ch = RabbitMQUtils.createChannel();
                String exchange = "pstoolkit.demo";
                String queue = "pstoolkit.q1";
                String routingKey = "demo.key";
                RabbitMQUtils.declareExchange(ch, exchange, BuiltinExchangeType.DIRECT, true);
                RabbitMQUtils.declareQueue(ch, queue, true);
                RabbitMQUtils.bindQueue(ch, queue, exchange, routingKey);
                AMQP.BasicProperties props = new AMQP.BasicProperties.Builder().deliveryMode(2).contentType("text/plain").build();
                RabbitMQUtils.publish(ch, exchange, routingKey, "hello pstoolkit".getBytes("UTF-8"), props, false);
                System.out.println("RabbitMQ message published");
            } finally {
                RabbitMQUtils.closeQuietly(ch);
            }
        }

        ExcelWriter.writeXlsxToFile(Paths.get("target/sample.xlsx"), "Demo", rows, header);
        System.out.println("Excel written to target/sample.xlsx");
    }
}
