import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;

/**
 * ThreadPoolExecutor 最佳实践示例
 * 包含生产环境中的实际应用场景
 */
public class ThreadPoolBestPractices {
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== ThreadPoolExecutor 最佳实践 ===\n");
        
        // 1. 线程池工厂模式
        threadPoolFactoryDemo();
        
        // 2. 线程池监控和告警
        monitoringAndAlertingDemo();
        
        // 3. 优雅关闭最佳实践
        gracefulShutdownBestPractice();
        
        // 4. 线程池隔离
        threadPoolIsolationDemo();
    }
    
    /**
     * 线程池工厂模式演示
     */
    public static void threadPoolFactoryDemo() throws InterruptedException {
        System.out.println("1. 线程池工厂模式");
        System.out.println("==================");
        
        // 获取不同类型的线程池
        ThreadPoolExecutor cpuPool = ThreadPoolFactory.createCPUIntensivePool("CPU-Task");
        ThreadPoolExecutor ioPool = ThreadPoolFactory.createIOIntensivePool("IO-Task");
        ThreadPoolExecutor fastPool = ThreadPoolFactory.createFastTaskPool("Fast-Task");
        
        System.out.println("CPU密集型线程池配置:");
        printPoolConfig(cpuPool);
        
        System.out.println("IO密集型线程池配置:");
        printPoolConfig(ioPool);
        
        System.out.println("快速任务线程池配置:");
        printPoolConfig(fastPool);
        
        // 关闭线程池
        ThreadPoolFactory.shutdown(cpuPool);
        ThreadPoolFactory.shutdown(ioPool);
        ThreadPoolFactory.shutdown(fastPool);
        
        System.out.println();
    }
    
    /**
     * 线程池监控和告警演示
     */
    public static void monitoringAndAlertingDemo() throws InterruptedException {
        System.out.println("2. 线程池监控和告警");
        System.out.println("====================");
        
        MonitoredThreadPool monitoredPool = new MonitoredThreadPool(
            "BusinessPool",
            2, 4, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(10)
        );
        
        // 提交任务触发监控
        for (int i = 1; i <= 15; i++) {
            final int taskId = i;
            monitoredPool.submit(() -> {
                try {
                    Thread.sleep(1000);
                    System.out.printf("任务%d执行完成%n", taskId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            Thread.sleep(200);
        }
        
        Thread.sleep(8000); // 观察监控输出
        monitoredPool.shutdown();
        System.out.println();
    }
    
    /**
     * 优雅关闭最佳实践
     */
    public static void gracefulShutdownBestPractice() throws InterruptedException {
        System.out.println("3. 优雅关闭最佳实践");
        System.out.println("====================");
        
        GracefulShutdownThreadPool pool = new GracefulShutdownThreadPool();
        
        // 提交一些任务
        for (int i = 1; i <= 5; i++) {
            final int taskId = i;
            pool.submit(() -> {
                System.out.printf("任务%d开始执行%n", taskId);
                try {
                    Thread.sleep(2000);
                    System.out.printf("任务%d正常完成%n", taskId);
                } catch (InterruptedException e) {
                    System.out.printf("任务%d被中断%n", taskId);
                    Thread.currentThread().interrupt();
                }
            });
        }
        
        Thread.sleep(1000); // 让任务开始执行
        
        // 演示优雅关闭
        pool.gracefulShutdown();
        System.out.println();
    }
    
    /**
     * 线程池隔离演示
     */
    public static void threadPoolIsolationDemo() throws InterruptedException {
        System.out.println("4. 线程池隔离");
        System.out.println("==============");
        
        BusinessThreadPoolManager manager = new BusinessThreadPoolManager();
        
        // 不同业务使用不同的线程池
        for (int i = 1; i <= 5; i++) {
            final int taskId = i;
            
            // 用户相关任务
            manager.submitUserTask(() -> {
                System.out.printf("用户任务%d执行中%n", taskId);
                try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            
            // 订单相关任务
            manager.submitOrderTask(() -> {
                System.out.printf("订单任务%d执行中%n", taskId);
                try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            
            // 通知相关任务
            manager.submitNotificationTask(() -> {
                System.out.printf("通知任务%d执行中%n", taskId);
                try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
        }
        
        Thread.sleep(5000);
        manager.shutdown();
        System.out.println();
    }
    
    private static void printPoolConfig(ThreadPoolExecutor executor) {
        System.out.printf("  核心线程数: %d, 最大线程数: %d, 队列类型: %s, 队列容量: %d%n",
            executor.getCorePoolSize(),
            executor.getMaximumPoolSize(),
            executor.getQueue().getClass().getSimpleName(),
            executor.getQueue().size() + executor.getQueue().remainingCapacity()
        );
    }
}

/**
 * 线程池工厂类
 */
class ThreadPoolFactory {
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    
    /**
     * 创建CPU密集型任务线程池
     */
    public static ThreadPoolExecutor createCPUIntensivePool(String namePrefix) {
        return new ThreadPoolExecutor(
            CPU_COUNT + 1,
            CPU_COUNT + 1,
            0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(100),
            createThreadFactory(namePrefix),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
    
    /**
     * 创建IO密集型任务线程池
     */
    public static ThreadPoolExecutor createIOIntensivePool(String namePrefix) {
        return new ThreadPoolExecutor(
            CPU_COUNT * 2,
            CPU_COUNT * 4,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(200),
            createThreadFactory(namePrefix),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
    
    /**
     * 创建快速任务线程池（适合执行时间很短的任务）
     */
    public static ThreadPoolExecutor createFastTaskPool(String namePrefix) {
        return new ThreadPoolExecutor(
            CPU_COUNT,
            CPU_COUNT * 2,
            30L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            createThreadFactory(namePrefix),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
    
    /**
     * 创建自定义线程工厂
     */
    private static ThreadFactory createThreadFactory(String namePrefix) {
        return new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, namePrefix + "-" + counter.getAndIncrement());
                t.setDaemon(false);
                t.setPriority(Thread.NORM_PRIORITY);
                // 设置异常处理器
                t.setUncaughtExceptionHandler((thread, ex) -> {
                    System.err.printf("线程 %s 发生未捕获异常: %s%n", thread.getName(), ex.getMessage());
                    ex.printStackTrace();
                });
                return t;
            }
        };
    }
    
    /**
     * 优雅关闭线程池
     */
    public static void shutdown(ThreadPoolExecutor executor) {
        if (executor == null || executor.isShutdown()) {
            return;
        }
        
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    System.err.println("线程池未能正常关闭");
                }
            }
        } catch (InterruptedException ie) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

/**
 * 带监控的线程池
 */
class MonitoredThreadPool {
    private final ThreadPoolExecutor executor;
    private final String poolName;
    private final ScheduledExecutorService monitor;
    private final AtomicLong rejectedCount = new AtomicLong(0);
    private final AtomicLong submittedCount = new AtomicLong(0);
    
    // 告警阈值
    private static final double QUEUE_USAGE_THRESHOLD = 0.8;
    private static final double THREAD_USAGE_THRESHOLD = 0.8;
    
    public MonitoredThreadPool(String poolName, int corePoolSize, int maximumPoolSize,
                              long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        this.poolName = poolName;
        this.executor = new ThreadPoolExecutor(
            corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
            createMonitoredThreadFactory(poolName),
            new MonitoredRejectionHandler()
        );
        
        this.monitor = Executors.newScheduledThreadPool(1);
        startMonitoring();
    }
    
    private ThreadFactory createMonitoredThreadFactory(String namePrefix) {
        return new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, namePrefix + "-" + counter.getAndIncrement());
                t.setDaemon(false);
                return t;
            }
        };
    }
    
    private void startMonitoring() {
        monitor.scheduleAtFixedRate(() -> {
            collectMetrics();
            checkAlerts();
        }, 0, 5, TimeUnit.SECONDS);
    }
    
    private void collectMetrics() {
        int queueSize = executor.getQueue().size();
        int queueCapacity = queueSize + executor.getQueue().remainingCapacity();
        double queueUsage = queueCapacity > 0 ? (double) queueSize / queueCapacity : 0;
        
        int currentThreads = executor.getPoolSize();
        int maxThreads = executor.getMaximumPoolSize();
        double threadUsage = (double) currentThreads / maxThreads;
        
        System.out.printf("[%s] 监控指标 - 活跃线程:%d/%d(%.1f%%), 队列:%d/%d(%.1f%%), " +
                         "已完成:%d, 被拒绝:%d, 已提交:%d%n",
            poolName,
            executor.getActiveCount(), maxThreads, threadUsage * 100,
            queueSize, queueCapacity, queueUsage * 100,
            executor.getCompletedTaskCount(),
            rejectedCount.get(),
            submittedCount.get()
        );
    }
    
    private void checkAlerts() {
        // 检查队列使用率告警
        int queueSize = executor.getQueue().size();
        int queueCapacity = queueSize + executor.getQueue().remainingCapacity();
        if (queueCapacity > 0) {
            double queueUsage = (double) queueSize / queueCapacity;
            if (queueUsage > QUEUE_USAGE_THRESHOLD) {
                System.err.printf("[告警] %s 队列使用率过高: %.1f%% > %.1f%%%n", 
                    poolName, queueUsage * 100, QUEUE_USAGE_THRESHOLD * 100);
            }
        }
        
        // 检查线程使用率告警
        double threadUsage = (double) executor.getPoolSize() / executor.getMaximumPoolSize();
        if (threadUsage > THREAD_USAGE_THRESHOLD) {
            System.err.printf("[告警] %s 线程使用率过高: %.1f%% > %.1f%%%n", 
                poolName, threadUsage * 100, THREAD_USAGE_THRESHOLD * 100);
        }
        
        // 检查拒绝任务告警
        long currentRejected = rejectedCount.get();
        if (currentRejected > 0) {
            System.err.printf("[告警] %s 存在被拒绝的任务: %d个%n", poolName, currentRejected);
        }
    }
    
    public void submit(Runnable task) {
        submittedCount.incrementAndGet();
        executor.submit(task);
    }
    
    public void shutdown() {
        monitor.shutdown();
        ThreadPoolFactory.shutdown(executor);
    }
    
    private class MonitoredRejectionHandler implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            rejectedCount.incrementAndGet();
            System.err.printf("[%s] 任务被拒绝: %s%n", poolName, r.toString());
            
            // 可以实现更复杂的处理逻辑，如：
            // 1. 记录到日志文件
            // 2. 发送告警通知
            // 3. 尝试重新提交到备用线程池
            // 4. 降级处理
            
            // 这里简单地由调用者执行
            if (!executor.isShutdown()) {
                r.run();
            }
        }
    }
}

/**
 * 优雅关闭的线程池包装类
 */
class GracefulShutdownThreadPool {
    private final ThreadPoolExecutor executor;
    private volatile boolean shutdownRequested = false;
    
    public GracefulShutdownThreadPool() {
        this.executor = new ThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "Graceful-" + counter.getAndIncrement());
                    t.setDaemon(false);
                    return t;
                }
            }
        );
        
        // 注册JVM关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(this::gracefulShutdown));
    }
    
    public void submit(Runnable task) {
        if (shutdownRequested) {
            System.out.println("线程池正在关闭，拒绝新任务");
            return;
        }
        executor.submit(task);
    }
    
    public void gracefulShutdown() {
        if (shutdownRequested) {
            return;
        }
        
        shutdownRequested = true;
        System.out.println("开始优雅关闭线程池...");
        
        // 1. 停止接收新任务
        executor.shutdown();
        
        try {
            // 2. 等待已提交任务完成
            System.out.println("等待已提交任务完成...");
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                System.out.println("等待超时，尝试中断正在执行的任务...");
                
                // 3. 中断正在执行的任务
                List<Runnable> pendingTasks = executor.shutdownNow();
                System.out.printf("强制关闭，未执行的任务数: %d%n", pendingTasks.size());
                
                // 4. 再次等待
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    System.err.println("线程池未能在指定时间内关闭");
                } else {
                    System.out.println("线程池已强制关闭");
                }
            } else {
                System.out.println("线程池已优雅关闭");
            }
        } catch (InterruptedException ie) {
            System.out.println("关闭过程被中断，立即强制关闭");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

/**
 * 业务线程池管理器 - 演示线程池隔离
 */
class BusinessThreadPoolManager {
    private final ThreadPoolExecutor userTaskPool;
    private final ThreadPoolExecutor orderTaskPool;
    private final ThreadPoolExecutor notificationPool;
    
    public BusinessThreadPoolManager() {
        // 用户相关任务线程池 - 重要业务，资源充足
        this.userTaskPool = new ThreadPoolExecutor(
            4, 8, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100),
            createThreadFactory("User"),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        // 订单相关任务线程池 - 核心业务，资源充足
        this.orderTaskPool = new ThreadPoolExecutor(
            3, 6, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(50),
            createThreadFactory("Order"),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        // 通知任务线程池 - 非核心业务，资源有限
        this.notificationPool = new ThreadPoolExecutor(
            1, 2, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(20),
            createThreadFactory("Notification"),
            new ThreadPoolExecutor.DiscardPolicy() // 通知任务可以丢弃
        );
        
        startMonitoring();
    }
    
    private ThreadFactory createThreadFactory(String business) {
        return new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, business + "-Worker-" + counter.getAndIncrement());
                t.setDaemon(false);
                return t;
            }
        };
    }
    
    private void startMonitoring() {
        ScheduledExecutorService monitor = Executors.newScheduledThreadPool(1);
        monitor.scheduleAtFixedRate(() -> {
            System.out.println("=== 业务线程池状态 ===");
            printPoolStatus("用户任务池", userTaskPool);
            printPoolStatus("订单任务池", orderTaskPool);
            printPoolStatus("通知任务池", notificationPool);
            System.out.println();
        }, 0, 3, TimeUnit.SECONDS);
    }
    
    private void printPoolStatus(String name, ThreadPoolExecutor pool) {
        System.out.printf("%s - 活跃:%d, 线程数:%d/%d, 队列:%d, 完成:%d%n",
            name,
            pool.getActiveCount(),
            pool.getPoolSize(),
            pool.getMaximumPoolSize(),
            pool.getQueue().size(),
            pool.getCompletedTaskCount()
        );
    }
    
    public void submitUserTask(Runnable task) {
        userTaskPool.submit(task);
    }
    
    public void submitOrderTask(Runnable task) {
        orderTaskPool.submit(task);
    }
    
    public void submitNotificationTask(Runnable task) {
        notificationPool.submit(task);
    }
    
    public void shutdown() {
        System.out.println("关闭业务线程池...");
        ThreadPoolFactory.shutdown(userTaskPool);
        ThreadPoolFactory.shutdown(orderTaskPool);
        ThreadPoolFactory.shutdown(notificationPool);
    }
}