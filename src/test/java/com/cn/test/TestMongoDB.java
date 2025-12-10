package com.cn.test;

import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;

import java.util.Arrays;
import java.util.List;

public class TestMongoDB {
    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> collection;

    public static void main(String[] args) {
        TestMongoDB example = new TestMongoDB();

        try {
            // 1. 连接数据库
            example.connect();

            // 2. 插入数据
            example.insertData();

            // 3. 查询数据
            example.queryData();

            // 4. 更新数据
            example.updateData();

            // 5. 删除数据
            example.deleteData();

        } catch (Exception e) {
            System.err.println("操作失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 6. 关闭连接
            example.close();
        }
    }

    // 1. 连接数据库
    public void connect() {
        try {
            System.out.println("正在连接MongoDB...");

            // 连接到MongoDB (替换为你的连接字符串)
            mongoClient = MongoClients.create("mongodb://admin:Qcj8813818@172.16.130.127:27017");

            // 获取数据库
            database = mongoClient.getDatabase("mine");

            // 获取集合
            collection = database.getCollection("users");

            System.out.println("连接成功！");
        } catch (Exception e) {
            System.err.println("连接失败: " + e.getMessage());
            throw e;
        }
    }

    // 2. 插入数据
    public void insertData() {
        System.out.println("\n=== 插入数据 ===");

        try {
            // 插入单个文档
            Document user1 = new Document("name", "张三")
                    .append("age", 25)
                    .append("email", "zhangsan@example.com")
                    .append("city", "北京")
                    .append("hobbies", Arrays.asList("读书", "游泳", "编程"));

            InsertOneResult result1 = collection.insertOne(user1);
            System.out.println("插入用户1成功，ID: " + result1.getInsertedId());

            // 插入多个文档
            List<Document> users = Arrays.asList(
                    new Document("name", "李四")
                            .append("age", 30)
                            .append("email", "lisi@example.com")
                            .append("city", "上海")
                            .append("hobbies", Arrays.asList("音乐", "电影")),
                    new Document("name", "王五")
                            .append("age", 28)
                            .append("email", "wangwu@example.com")
                            .append("city", "广州")
                            .append("hobbies", Arrays.asList("旅游", "摄影")),
                    new Document("name", "赵六")
                            .append("age", 22)
                            .append("email", "zhaoliu@example.com")
                            .append("city", "深圳")
                            .append("hobbies", Arrays.asList("游戏", "美食"))
            );

            collection.insertMany(users);
            System.out.println("插入多个用户成功");

        } catch (Exception e) {
            System.err.println("插入数据失败: " + e.getMessage());
        }
    }

    // 3. 查询数据
    public void queryData() {
        System.out.println("\n=== 查询数据 ===");

        try {
            // 查询所有文档
            System.out.println("所有用户:");
            collection.find().forEach(doc -> System.out.println("  " + doc.toJson()));

            // 查询单个文档
            System.out.println("\n查询姓名为'张三'的用户:");
            Document zhangsan = collection.find(Filters.eq("name", "张三")).first();
            if (zhangsan != null) {
                System.out.println("  " + zhangsan.toJson());
            }

            // 查询年龄大于25的用户
            System.out.println("\n年龄大于25的用户:");
            collection.find(Filters.gt("age", 25)).forEach(doc ->
                    System.out.println("  " + doc.toJson())
            );

            // 查询特定城市的用户
            System.out.println("\n来自北京或上海的用户:");
            collection.find(Filters.in("city", "北京", "上海")).forEach(doc ->
                    System.out.println("  " + doc.toJson())
            );

            // 计算文档总数
            long count = collection.countDocuments();
            System.out.println("\n用户总数: " + count);

        } catch (Exception e) {
            System.err.println("查询数据失败: " + e.getMessage());
        }
    }

    // 4. 更新数据
    public void updateData() {
        System.out.println("\n=== 更新数据 ===");

        try {
            // 更新单个文档
            System.out.println("更新张三的年龄:");
            UpdateResult result1 = collection.updateOne(
                    Filters.eq("name", "张三"),
                    Updates.combine(
                            Updates.set("age", 26),
                            Updates.set("salary", 8000)
                    )
            );
            System.out.println("  匹配文档数: " + result1.getMatchedCount());
            System.out.println("  修改文档数: " + result1.getModifiedCount());

            // 更新多个文档
            System.out.println("\n给所有年龄大于25的用户添加状态字段:");
            UpdateResult result2 = collection.updateMany(
                    Filters.gt("age", 25),
                    Updates.set("status", "active")
            );
            System.out.println("  匹配文档数: " + result2.getMatchedCount());
            System.out.println("  修改文档数: " + result2.getModifiedCount());

            // 添加爱好
            System.out.println("\n给张三添加新的爱好:");
            UpdateResult result3 = collection.updateOne(
                    Filters.eq("name", "张三"),
                    Updates.addToSet("hobbies", "健身")
            );
            System.out.println("  修改文档数: " + result3.getModifiedCount());

            // 查看更新后的结果
            System.out.println("\n更新后的张三信息:");
            Document updated = collection.find(Filters.eq("name", "张三")).first();
            if (updated != null) {
                System.out.println("  " + updated.toJson());
            }

        } catch (Exception e) {
            System.err.println("更新数据失败: " + e.getMessage());
        }
    }

    // 5. 删除数据
    public void deleteData() {
        System.out.println("\n=== 删除数据 ===");

        try {
            // 删除单个文档
            System.out.println("删除年龄小于23的用户:");
            DeleteResult result1 = collection.deleteOne(Filters.lt("age", 23));
            System.out.println("  删除文档数: " + result1.getDeletedCount());

            // 删除多个文档
            System.out.println("\n删除状态为'inactive'的用户 (如果有):");
            DeleteResult result2 = collection.deleteMany(Filters.eq("status", "inactive"));
            System.out.println("  删除文档数: " + result2.getDeletedCount());

            // 显示剩余数据
            System.out.println("\n删除后剩余用户:");
            collection.find().forEach(doc -> System.out.println("  " + doc.toJson()));

        } catch (Exception e) {
            System.err.println("删除数据失败: " + e.getMessage());
        }
    }

    // 6. 聚合查询示例
    public void aggregationExample() {
        System.out.println("\n=== 聚合查询示例 ===");

        try {
            // 简单聚合：按城市分组统计用户数
            System.out.println("按城市分组统计用户数:");
            List<Document> pipeline = Arrays.asList(
                    new Document("$group", new Document("_id", "$city")
                            .append("count", new Document("$sum", 1))
                            .append("avgAge", new Document("$avg", "$age"))),
                    new Document("$sort", new Document("count", -1))
            );

            collection.aggregate(pipeline).forEach(doc ->
                    System.out.println("  " + doc.toJson())
            );

        } catch (Exception e) {
            System.err.println("聚合查询失败: " + e.getMessage());
        }
    }

    // 7. 关闭连接
    public void close() {
        System.out.println("\n=== 关闭连接 ===");
        try {
            if (mongoClient != null) {
                mongoClient.close();
                System.out.println("连接已关闭");
            }
        } catch (Exception e) {
            System.err.println("关闭连接时出错: " + e.getMessage());
        }
    }
}

