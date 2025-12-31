package com.cn.test;

import com.cn.pstoolkit.utils.LatchUtils;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TestLatchUtils {

    @Test
    public void testLatchUtils() {
        Executor executor = Executors.newFixedThreadPool(3);

        // 提交多个任务
        LatchUtils.submitTask(executor, () -> {
            System.out.println("Task 1 starting...");
            sleep(1000);
            System.out.println("Task 1 completed");
        });

        LatchUtils.submitTask(executor, () -> {
            System.out.println("Task 2 starting...");
            sleep(2000);
            System.out.println("Task 2 completed");
        });

        LatchUtils.submitTask(executor, () -> {
            System.out.println("Task 3 starting...");
            sleep(1500);
            System.out.println("Task 3 completed");
        });

        // 等待所有任务完成
        boolean success = LatchUtils.waitFor(5, TimeUnit.SECONDS);

        if (success) {
            System.out.println("All tasks completed successfully!");
        } else {
            System.out.println("Tasks timeout or failed!");
        }

        // 关闭线程池
        if (executor instanceof ExecutorService) {
            ((ExecutorService) executor).shutdown();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
