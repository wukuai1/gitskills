import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;

/**
 * 高级线程池使用示例
 * 包含实际项目中的常见场景和最佳实践
 */
public class AdvancedThreadPoolExamples {
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== 高级线程池使用示例 ===\n");
        
        // 1. CPU密集型任务处理
        cpuIntensiveTaskDemo();
        
        // 2. IO密集型任务处理
        ioIntensiveTaskDemo();
        
        // 3. 优先级任务处理
        priorityTaskDemo();
        
        // 4. 动态线程池
        dynamicThreadPoolDemo();
        
        // 5. 线程池异常处理
        exceptionHandlingDemo();
    }
    
    /**
     * CPU密集型任务处理示例
     */
    public static void cpuIntensiveTaskDemo() throws InterruptedException {
        System.out.println("1. CPU密集型任务处理");
        System.out.println("====================");
        
        int cpuCount = Runtime.getRuntime().availableProcessors();
        System.out.println("CPU核心数: " + cpuCount);
        
        ThreadPoolExecutor cpuPool = new ThreadPoolExecutor(
            cpuCount + 1,                    // 核心线程数 = CPU核心数 + 1
            cpuCount + 1,                    // 最大线程数
            0L, TimeUnit.MILLISECONDS,       // 不需要回收线程
            new ArrayBlockingQueue<>(100),   // 有界队列防止OOM
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "CPU-Worker-" + counter.getAndIncrement());
                    t.setDaemon(false);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        long startTime = System.currentTimeMillis();
        
        // 提交CPU密集型任务：计算斐波那契数列
        List<Future<Long>> futures = new ArrayList<>();
        for (int i = 35; i <= 40; i++) {
            final int n = i;
            Future<Long> future = cpuPool.submit(() -> {
                long result = fibonacci(n);
                System.out.printf("fibonacci(%d) = %d, 线程: %s%n", 
                    n, result, Thread.currentThread().getName());
                return result;
            });
            futures.add(future);
        }
        
        // 等待所有任务完成
        for (Future<Long> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
        
        long endTime = System.currentTimeMillis();
        System.out.printf("CPU密集型任务总耗时: %d ms%n", endTime - startTime);
        
        cpuPool.shutdown();
        cpuPool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println();
    }
    
    /**
     * IO密集型任务处理示例
     */
    public static void ioIntensiveTaskDemo() throws InterruptedException {
        System.out.println("2. IO密集型任务处理");
        System.out.println("===================");
        
        int cpuCount = Runtime.getRuntime().availableProcessors();
        
        ThreadPoolExecutor ioPool = new ThreadPoolExecutor(
            cpuCount * 2,                    // 核心线程数 = CPU核心数 * 2
            cpuCount * 4,                    // 最大线程数 = CPU核心数 * 4
            60L, TimeUnit.SECONDS,           // 空闲线程60秒后回收
            new LinkedBlockingQueue<>(200),  // 较大的队列
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "IO-Worker-" + counter.getAndIncrement());
                    t.setDaemon(false);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        long startTime = System.currentTimeMillis();
        
        // 提交IO密集型任务：模拟网络请求
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            final int taskId = i;
            Future<String> future = ioPool.submit(() -> {
                return simulateNetworkCall(taskId);
            });
            futures.add(future);
        }
        
        // 收集结果
        for (Future<String> future : futures) {
            try {
                String result = future.get();
                System.out.println(result);
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
        
        long endTime = System.currentTimeMillis();
        System.out.printf("IO密集型任务总耗时: %d ms%n", endTime - startTime);
        
        ioPool.shutdown();
        ioPool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println();
    }
    
    /**
     * 优先级任务处理示例
     */
    public static void priorityTaskDemo() throws InterruptedException {
        System.out.println("3. 优先级任务处理");
        System.out.println("==================");
        
        ThreadPoolExecutor priorityPool = new ThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS,
            new PriorityBlockingQueue<>(),
            Executors.defaultThreadFactory(),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        // 提交不同优先级的任务
        for (int i = 1; i <= 10; i++) {
            int priority = (i % 3) + 1; // 优先级1-3
            PriorityTask task = new PriorityTask(priority, "Task-" + i);
            priorityPool.submit(task);
        }
        
        Thread.sleep(5000); // 等待任务执行
        
        priorityPool.shutdown();
        priorityPool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println();
    }
    
    /**
     * 动态线程池示例
     */
    public static void dynamicThreadPoolDemo() throws InterruptedException {
        System.out.println("4. 动态线程池");
        System.out.println("==============");
        
        DynamicThreadPoolExecutor dynamicPool = new DynamicThreadPoolExecutor();
        
        // 模拟不同负载情况
        System.out.println("模拟低负载...");
        for (int i = 1; i <= 5; i++) {
            dynamicPool.submit(createSimpleTask(i, 1000));
        }
        Thread.sleep(3000);
        
        System.out.println("模拟高负载...");
        for (int i = 6; i <= 20; i++) {
            dynamicPool.submit(createSimpleTask(i, 500));
        }
        Thread.sleep(5000);
        
        System.out.println("负载降低...");
        Thread.sleep(5000);
        
        dynamicPool.shutdown();
        System.out.println();
    }
    
    /**
     * 线程池异常处理示例
     */
    public static void exceptionHandlingDemo() throws InterruptedException {
        System.out.println("5. 线程池异常处理");
        System.out.println("==================");
        
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>()
        );
        
        // 1. 使用execute提交任务 - 异常会被打印到stderr
        System.out.println("5.1 使用execute提交异常任务:");
        executor.execute(() -> {
            System.out.println("Execute任务开始执行");
            throw new RuntimeException("Execute任务异常");
        });
        
        Thread.sleep(1000);
        
        // 2. 使用submit提交任务 - 异常被包装在Future中
        System.out.println("\n5.2 使用submit提交异常任务:");
        Future<?> future = executor.submit(() -> {
            System.out.println("Submit任务开始执行");
            throw new RuntimeException("Submit任务异常");
        });
        
        try {
            future.get(); // 获取异常
        } catch (ExecutionException e) {
            System.out.println("捕获到Submit任务异常: " + e.getCause().getMessage());
        }
        
        // 3. 自定义异常处理
        System.out.println("\n5.3 自定义异常处理:");
        ThreadPoolExecutor customExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>()
        ) {
            @Override
            protected void afterExecute(Runnable r, Throwable t) {
                super.afterExecute(r, t);
                if (t != null) {
                    System.out.println("任务执行异常: " + t.getMessage());
                    // 可以记录日志、发送告警等
                }
            }
        };
        
        customExecutor.execute(() -> {
            throw new RuntimeException("自定义处理异常");
        });
        
        Thread.sleep(1000);
        
        executor.shutdown();
        customExecutor.shutdown();
        executor.awaitTermination(2, TimeUnit.SECONDS);
        customExecutor.awaitTermination(2, TimeUnit.SECONDS);
        System.out.println();
    }
    
    // 工具方法
    private static long fibonacci(int n) {
        if (n <= 1) return n;
        return fibonacci(n - 1) + fibonacci(n - 2);
    }
    
    private static String simulateNetworkCall(int taskId) {
        try {
            // 模拟网络延迟
            Thread.sleep(500 + (int)(Math.random() * 1000));
            return String.format("网络请求%d完成，线程: %s", taskId, Thread.currentThread().getName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "网络请求被中断";
        }
    }
    
    private static Runnable createSimpleTask(int taskId, long sleepTime) {
        return () -> {
            try {
                System.out.printf("任务%d开始执行，线程: %s%n", taskId, Thread.currentThread().getName());
                Thread.sleep(sleepTime);
                System.out.printf("任务%d执行完成%n", taskId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }
}

/**
 * 优先级任务
 */
class PriorityTask implements Runnable, Comparable<PriorityTask> {
    private final int priority;
    private final String taskName;
    private final long createTime;
    
    public PriorityTask(int priority, String taskName) {
        this.priority = priority;
        this.taskName = taskName;
        this.createTime = System.currentTimeMillis();
    }
    
    @Override
    public void run() {
        System.out.printf("优先级%d任务 %s 开始执行，线程: %s%n", 
            priority, taskName, Thread.currentThread().getName());
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.printf("优先级%d任务 %s 执行完成%n", priority, taskName);
    }
    
    @Override
    public int compareTo(PriorityTask other) {
        // 优先级高的排在前面，优先级相同则按创建时间排序
        int result = Integer.compare(other.priority, this.priority);
        if (result == 0) {
            result = Long.compare(this.createTime, other.createTime);
        }
        return result;
    }
    
    @Override
    public String toString() {
        return String.format("PriorityTask{priority=%d, name='%s'}", priority, taskName);
    }
}

/**
 * 动态调整的线程池
 */
class DynamicThreadPoolExecutor {
    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService monitor;
    private final AtomicLong lastAdjustTime = new AtomicLong(System.currentTimeMillis());
    
    public DynamicThreadPoolExecutor() {
        this.executor = new ThreadPoolExecutor(
            2, 10, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(50),
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "Dynamic-" + counter.getAndIncrement());
                    t.setDaemon(false);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        this.monitor = Executors.newScheduledThreadPool(1);
        startMonitoring();
    }
    
    private void startMonitoring() {
        monitor.scheduleAtFixedRate(() -> {
            adjustPoolSize();
            printStatus();
        }, 0, 2, TimeUnit.SECONDS);
    }
    
    private void adjustPoolSize() {
        int queueSize = executor.getQueue().size();
        int activeCount = executor.getActiveCount();
        int corePoolSize = executor.getCorePoolSize();
        long currentTime = System.currentTimeMillis();
        
        // 避免频繁调整
        if (currentTime - lastAdjustTime.get() < 5000) {
            return;
        }
        
        // 队列积压较多，增加核心线程
        if (queueSize > 20 && corePoolSize < 8) {
            executor.setCorePoolSize(corePoolSize + 1);
            lastAdjustTime.set(currentTime);
            System.out.printf("增加核心线程数至: %d (队列积压: %d)%n", corePoolSize + 1, queueSize);
        }
        // 队列较空且活跃线程少，减少核心线程
        else if (queueSize < 5 && activeCount < corePoolSize - 1 && corePoolSize > 2) {
            executor.setCorePoolSize(corePoolSize - 1);
            lastAdjustTime.set(currentTime);
            System.out.printf("减少核心线程数至: %d (队列大小: %d, 活跃线程: %d)%n", 
                corePoolSize - 1, queueSize, activeCount);
        }
    }
    
    private void printStatus() {
        System.out.printf("动态线程池状态 - 核心:%d, 最大:%d, 当前:%d, 活跃:%d, 队列:%d%n",
            executor.getCorePoolSize(),
            executor.getMaximumPoolSize(),
            executor.getPoolSize(),
            executor.getActiveCount(),
            executor.getQueue().size()
        );
    }
    
    public void submit(Runnable task) {
        executor.submit(task);
    }
    
    public void shutdown() {
        monitor.shutdown();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}