import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ThreadPoolExecutor 实战演示代码
 * 包含各种场景下的线程池使用示例
 */
public class ThreadPoolDemo {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== ThreadPoolExecutor 实战演示 ===\n");
        
        // 1. 基础线程池演示
        basicThreadPoolDemo();
        
        // 2. 不同队列类型演示
        queueTypesDemo();
        
        // 3. 拒绝策略演示
        rejectionPolicyDemo();
        
        // 4. 线程池监控演示
        monitoringDemo();
        
        // 5. 优雅关闭演示
        gracefulShutdownDemo();
    }
    
    /**
     * 基础线程池使用演示
     */
    public static void basicThreadPoolDemo() throws InterruptedException {
        System.out.println("1. 基础线程池演示");
        System.out.println("==================");
        
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2,                              // 核心线程数
            4,                              // 最大线程数
            60L,                            // 存活时间
            TimeUnit.SECONDS,               // 时间单位
            new ArrayBlockingQueue<>(3),    // 工作队列容量为3
            new CustomThreadFactory("Demo"), // 自定义线程工厂
            new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略
        );
        
        // 提交8个任务，观察线程池行为
        for (int i = 1; i <= 8; i++) {
            final int taskId = i;
            executor.submit(() -> {
                System.out.printf("任务%d 开始执行，线程: %s%n", 
                    taskId, Thread.currentThread().getName());
                try {
                    Thread.sleep(2000); // 模拟任务执行
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.out.printf("任务%d 执行完成%n", taskId);
            });
            
            // 打印当前线程池状态
            printThreadPoolStatus(executor, "提交任务" + i + "后");
            Thread.sleep(500);
        }
        
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println();
    }
    
    /**
     * 不同队列类型演示
     */
    public static void queueTypesDemo() throws InterruptedException {
        System.out.println("2. 不同队列类型演示");
        System.out.println("==================");
        
        // 2.1 ArrayBlockingQueue - 有界队列
        System.out.println("2.1 ArrayBlockingQueue (有界队列):");
        ThreadPoolExecutor arrayQueuePool = new ThreadPoolExecutor(
            1, 2, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(2),
            new ThreadPoolExecutor.AbortPolicy()
        );
        
        testQueueBehavior(arrayQueuePool, "ArrayBlockingQueue");
        
        // 2.2 LinkedBlockingQueue - 有界队列
        System.out.println("\n2.2 LinkedBlockingQueue (有界队列):");
        ThreadPoolExecutor linkedQueuePool = new ThreadPoolExecutor(
            1, 2, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(2),
            new ThreadPoolExecutor.AbortPolicy()
        );
        
        testQueueBehavior(linkedQueuePool, "LinkedBlockingQueue");
        
        // 2.3 SynchronousQueue - 直接交接
        System.out.println("\n2.3 SynchronousQueue (直接交接):");
        ThreadPoolExecutor syncQueuePool = new ThreadPoolExecutor(
            1, 3, 60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new ThreadPoolExecutor.AbortPolicy()
        );
        
        testQueueBehavior(syncQueuePool, "SynchronousQueue");
        
        System.out.println();
    }
    
    private static void testQueueBehavior(ThreadPoolExecutor executor, String queueType) 
            throws InterruptedException {
        try {
            for (int i = 1; i <= 5; i++) {
                final int taskId = i;
                try {
                    executor.submit(() -> {
                        System.out.printf("[%s] 任务%d执行中，线程: %s%n", 
                            queueType, taskId, Thread.currentThread().getName());
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                    System.out.printf("[%s] 任务%d提交成功，队列大小: %d, 线程数: %d%n", 
                        queueType, taskId, executor.getQueue().size(), executor.getPoolSize());
                } catch (RejectedExecutionException e) {
                    System.out.printf("[%s] 任务%d被拒绝！%n", queueType, taskId);
                }
                Thread.sleep(200);
            }
        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
    
    /**
     * 拒绝策略演示
     */
    public static void rejectionPolicyDemo() throws InterruptedException {
        System.out.println("3. 拒绝策略演示");
        System.out.println("================");
        
        // 3.1 AbortPolicy - 抛异常
        System.out.println("3.1 AbortPolicy (抛异常):");
        testRejectionPolicy(new ThreadPoolExecutor.AbortPolicy(), "AbortPolicy");
        
        // 3.2 CallerRunsPolicy - 调用者执行
        System.out.println("\n3.2 CallerRunsPolicy (调用者执行):");
        testRejectionPolicy(new ThreadPoolExecutor.CallerRunsPolicy(), "CallerRunsPolicy");
        
        // 3.3 DiscardPolicy - 静默丢弃
        System.out.println("\n3.3 DiscardPolicy (静默丢弃):");
        testRejectionPolicy(new ThreadPoolExecutor.DiscardPolicy(), "DiscardPolicy");
        
        // 3.4 DiscardOldestPolicy - 丢弃最老任务
        System.out.println("\n3.4 DiscardOldestPolicy (丢弃最老任务):");
        testRejectionPolicy(new ThreadPoolExecutor.DiscardOldestPolicy(), "DiscardOldestPolicy");
        
        // 3.5 自定义拒绝策略
        System.out.println("\n3.5 CustomRejectionPolicy (自定义策略):");
        testRejectionPolicy(new CustomRejectionHandler(), "CustomRejectionPolicy");
        
        System.out.println();
    }
    
    private static void testRejectionPolicy(RejectedExecutionHandler handler, String policyName) 
            throws InterruptedException {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1),
            handler
        );
        
        try {
            for (int i = 1; i <= 4; i++) {
                final int taskId = i;
                try {
                    executor.submit(() -> {
                        System.out.printf("[%s] 任务%d执行中，线程: %s%n", 
                            policyName, taskId, Thread.currentThread().getName());
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                    System.out.printf("[%s] 任务%d提交成功%n", policyName, taskId);
                } catch (RejectedExecutionException e) {
                    System.out.printf("[%s] 任务%d被拒绝: %s%n", policyName, taskId, e.getMessage());
                }
                Thread.sleep(100);
            }
        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
    
    /**
     * 线程池监控演示
     */
    public static void monitoringDemo() throws InterruptedException {
        System.out.println("4. 线程池监控演示");
        System.out.println("==================");
        
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(5)
        );
        
        // 启动监控线程
        ScheduledExecutorService monitor = Executors.newScheduledThreadPool(1);
        monitor.scheduleAtFixedRate(() -> {
            printDetailedStatus(executor);
        }, 0, 1, TimeUnit.SECONDS);
        
        // 提交任务
        for (int i = 1; i <= 10; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            Thread.sleep(300);
        }
        
        Thread.sleep(8000); // 等待任务完成
        
        monitor.shutdown();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println();
    }
    
    /**
     * 优雅关闭演示
     */
    public static void gracefulShutdownDemo() throws InterruptedException {
        System.out.println("5. 优雅关闭演示");
        System.out.println("================");
        
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>()
        );
        
        // 提交一些长时间运行的任务
        for (int i = 1; i <= 5; i++) {
            final int taskId = i;
            executor.submit(() -> {
                System.out.printf("长任务%d开始执行%n", taskId);
                try {
                    Thread.sleep(3000);
                    System.out.printf("长任务%d执行完成%n", taskId);
                } catch (InterruptedException e) {
                    System.out.printf("长任务%d被中断%n", taskId);
                    Thread.currentThread().interrupt();
                }
            });
        }
        
        Thread.sleep(1000); // 让任务开始执行
        
        // 优雅关闭
        System.out.println("开始优雅关闭线程池...");
        executor.shutdown(); // 停止接收新任务
        
        try {
            // 等待已提交任务完成
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                System.out.println("等待超时，强制关闭...");
                executor.shutdownNow(); // 强制关闭
                
                // 再次等待
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    System.err.println("线程池未能正常关闭");
                }
            }
        } catch (InterruptedException ie) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        System.out.println("线程池已关闭");
    }
    
    // 工具方法
    private static void printThreadPoolStatus(ThreadPoolExecutor executor, String moment) {
        System.out.printf("%s - 核心:%d, 最大:%d, 当前:%d, 活跃:%d, 队列:%d%n",
            moment,
            executor.getCorePoolSize(),
            executor.getMaximumPoolSize(),
            executor.getPoolSize(),
            executor.getActiveCount(),
            executor.getQueue().size()
        );
    }
    
    private static void printDetailedStatus(ThreadPoolExecutor executor) {
        System.out.printf("监控 - 核心线程:%d, 最大线程:%d, 当前线程:%d, 活跃线程:%d, " +
                         "队列大小:%d, 已完成:%d, 总任务:%d%n",
            executor.getCorePoolSize(),
            executor.getMaximumPoolSize(),
            executor.getPoolSize(),
            executor.getActiveCount(),
            executor.getQueue().size(),
            executor.getCompletedTaskCount(),
            executor.getTaskCount()
        );
    }
}

/**
 * 自定义线程工厂
 */
class CustomThreadFactory implements ThreadFactory {
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix;
    
    public CustomThreadFactory(String namePrefix) {
        this.namePrefix = namePrefix;
    }
    
    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, namePrefix + "-Thread-" + threadNumber.getAndIncrement());
        t.setDaemon(false);
        t.setPriority(Thread.NORM_PRIORITY);
        return t;
    }
}

/**
 * 自定义拒绝策略
 */
class CustomRejectionHandler implements RejectedExecutionHandler {
    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        System.err.println("自定义拒绝策略: 任务 " + r.toString() + " 被拒绝");
        System.err.println("当前线程池状态 - 线程数: " + executor.getPoolSize() + 
                          ", 队列大小: " + executor.getQueue().size());
        
        // 可以实现重试机制、记录日志、发送告警等
        // 这里简单地尝试重新提交到队列
        if (!executor.getQueue().offer(r)) {
            System.err.println("重新提交失败，任务彻底丢弃");
        } else {
            System.out.println("任务重新提交成功");
        }
    }
}