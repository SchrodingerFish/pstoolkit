package com.cn.pstoolkit.utils;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class LatchUtils {

    private static final ThreadLocal<List<TaskInfo>> THREAD_LOCAL =
            ThreadLocal.withInitial(LinkedList::new);

    /**
     * 提交任务到线程池
     */
    public static void submitTask(Executor executor, Runnable runnable) {
        THREAD_LOCAL.get().add(new TaskInfo(executor, runnable));
    }

    /**
     * 提交任务并指定任务名称（便于调试）
     */
    public static void submitTask(Executor executor, Runnable runnable, String taskName) {
        THREAD_LOCAL.get().add(new TaskInfo(executor, runnable, taskName));
    }

    /**
     * 清理当前线程的任务队列
     */
    private static List<TaskInfo> popTask() {
        List<TaskInfo> taskInfos = THREAD_LOCAL.get();
        THREAD_LOCAL.remove();
        return taskInfos;
    }

    /**
     * 等待所有任务完成（有超时）
     */
    public static boolean waitFor(long timeout, TimeUnit timeUnit) {
        return waitFor(timeout, timeUnit, false);
    }

    /**
     * 等待所有任务完成（无超时）
     */
    public static boolean waitFor() {
        return waitFor(0, null, true);
    }

    /**
     * 核心等待方法
     */
    private static boolean waitFor(long timeout, TimeUnit timeUnit, boolean noTimeout) {
        List<TaskInfo> taskInfos = popTask();
        if (taskInfos.isEmpty()) {
            return true;
        }

        CountDownLatch latch = new CountDownLatch(taskInfos.size());
        boolean[] hasError = {false};

        for (TaskInfo taskInfo : taskInfos) {
            Executor executor = taskInfo.executor;
            Runnable runnable = taskInfo.runnable;

            executor.execute(() -> {
                try {
                    runnable.run();
                } catch (Exception e) {
                    hasError[0] = true;
                    System.err.println("Task execution failed" +
                            (taskInfo.taskName != null ? " [" + taskInfo.taskName + "]" : "") +
                            ": " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            if (noTimeout) {
                latch.await();
                return !hasError[0];
            } else {
                boolean awaitResult = latch.await(timeout, timeUnit);
                return awaitResult && !hasError[0];
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 任务信息内部类
     */
    private static final class TaskInfo {
        private final Executor executor;
        private final Runnable runnable;
        private final String taskName;

        public TaskInfo(Executor executor, Runnable runnable) {
            this(executor, runnable, null);
        }

        public TaskInfo(Executor executor, Runnable runnable, String taskName) {
            this.executor = executor;
            this.runnable = runnable;
            this.taskName = taskName;
        }
    }
}