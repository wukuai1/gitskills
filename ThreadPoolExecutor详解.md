# ThreadPoolExecutor 参数详解与面试题

## 目录
- [ThreadPoolExecutor 参数详解](#threadpoolexecutor-参数详解)
- [核心参数说明](#核心参数说明)
- [工作原理](#工作原理)
- [拒绝策略](#拒绝策略)
- [常见面试题](#常见面试题)
- [实战代码示例](#实战代码示例)
- [最佳实践](#最佳实践)

## ThreadPoolExecutor 参数详解

### 构造函数
```java
public ThreadPoolExecutor(int corePoolSize,
                         int maximumPoolSize,
                         long keepAliveTime,
                         TimeUnit unit,
                         BlockingQueue<Runnable> workQueue,
                         ThreadFactory threadFactory,
                         RejectedExecutionHandler handler)
```

## 核心参数说明

### 1. corePoolSize (核心线程数)
- **定义**: 线程池中始终保持的线程数量
- **特点**: 
  - 即使这些线程处于空闲状态，也不会被回收
  - 线程池创建后，核心线程会逐渐创建，直到达到 corePoolSize
- **建议值**: CPU密集型任务 = CPU核心数 + 1，IO密集型任务 = 2 * CPU核心数

### 2. maximumPoolSize (最大线程数)
- **定义**: 线程池允许创建的最大线程数量
- **触发条件**: 当工作队列满了，且当前线程数 < maximumPoolSize 时，会创建新线程
- **注意**: 
  - 如果使用无界队列，此参数无效
  - 必须 >= corePoolSize

### 3. keepAliveTime (线程存活时间)
- **定义**: 非核心线程空闲时的存活时间
- **作用**: 超过此时间的空闲非核心线程将被回收
- **配合参数**: allowCoreThreadTimeOut(true) 可让核心线程也遵循此规则

### 4. unit (时间单位)
- **可选值**: 
  - `TimeUnit.NANOSECONDS` - 纳秒
  - `TimeUnit.MICROSECONDS` - 微秒
  - `TimeUnit.MILLISECONDS` - 毫秒
  - `TimeUnit.SECONDS` - 秒
  - `TimeUnit.MINUTES` - 分钟
  - `TimeUnit.HOURS` - 小时
  - `TimeUnit.DAYS` - 天

### 5. workQueue (工作队列)
线程池的核心组件，用于存储待执行的任务。

#### 5.1 ArrayBlockingQueue (有界队列)
```java
// 容量为100的有界队列
BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(100);
```
- **特点**: 基于数组的有界阻塞队列，FIFO
- **优点**: 防止资源耗尽
- **缺点**: 队列满时可能触发拒绝策略

#### 5.2 LinkedBlockingQueue (无界队列)
```java
// 默认容量为 Integer.MAX_VALUE
BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
// 指定容量
BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(1000);
```
- **特点**: 基于链表的阻塞队列
- **注意**: 默认无界，可能导致内存溢出

#### 5.3 SynchronousQueue (直接交接)
```java
BlockingQueue<Runnable> queue = new SynchronousQueue<>();
```
- **特点**: 不存储元素，每个插入操作必须等待对应的移除操作
- **适用**: 任务数量不确定的场景

#### 5.4 PriorityBlockingQueue (优先级队列)
```java
BlockingQueue<Runnable> queue = new PriorityBlockingQueue<>();
```
- **特点**: 支持优先级排序的无界队列
- **要求**: 任务必须实现 Comparable 接口

### 6. threadFactory (线程工厂)
用于创建新线程的工厂。

#### 默认实现
```java
ThreadFactory factory = Executors.defaultThreadFactory();
```

#### 自定义线程工厂
```java
ThreadFactory customFactory = new ThreadFactory() {
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    
    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, "CustomPool-" + threadNumber.getAndIncrement());
        t.setDaemon(false);  // 设置为非守护线程
        t.setPriority(Thread.NORM_PRIORITY);
        return t;
    }
};
```

### 7. handler (拒绝策略)
当线程池和队列都满时的处理策略。

## 拒绝策略

### 1. AbortPolicy (默认策略)
```java
RejectedExecutionHandler handler = new ThreadPoolExecutor.AbortPolicy();
```
- **行为**: 抛出 `RejectedExecutionException` 异常
- **适用**: 需要明确知道任务被拒绝的场景

### 2. CallerRunsPolicy
```java
RejectedExecutionHandler handler = new ThreadPoolExecutor.CallerRunsPolicy();
```
- **行为**: 由调用线程执行该任务
- **优点**: 不会丢失任务，提供降级机制
- **缺点**: 可能影响调用线程性能

### 3. DiscardPolicy
```java
RejectedExecutionHandler handler = new ThreadPoolExecutor.DiscardPolicy();
```
- **行为**: 静默丢弃任务，不抛异常
- **适用**: 任务丢失可接受的场景

### 4. DiscardOldestPolicy
```java
RejectedExecutionHandler handler = new ThreadPoolExecutor.DiscardOldestPolicy();
```
- **行为**: 丢弃队列头部最老的任务，然后重新提交当前任务
- **适用**: 新任务比老任务更重要的场景

### 5. 自定义拒绝策略
```java
RejectedExecutionHandler customHandler = new RejectedExecutionHandler() {
    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        // 记录日志
        System.err.println("Task " + r.toString() + " rejected from " + executor.toString());
        // 可以实现重试机制、存储到数据库等
    }
};
```

## 工作原理

### 任务提交流程
1. **当前线程数 < corePoolSize**: 创建新的核心线程执行任务
2. **当前线程数 >= corePoolSize**: 将任务加入工作队列
3. **队列已满 && 当前线程数 < maximumPoolSize**: 创建新的非核心线程
4. **队列已满 && 当前线程数 >= maximumPoolSize**: 执行拒绝策略

### 线程回收机制
- 非核心线程空闲超过 `keepAliveTime` 会被回收
- 核心线程默认不会被回收，除非设置 `allowCoreThreadTimeOut(true)`

## 常见面试题

### 1. 基础概念题

**Q: ThreadPoolExecutor 的核心参数有哪些？**

A: 七个核心参数：
- corePoolSize: 核心线程数
- maximumPoolSize: 最大线程数  
- keepAliveTime: 线程存活时间
- unit: 时间单位
- workQueue: 工作队列
- threadFactory: 线程工厂
- handler: 拒绝策略

**Q: 线程池的工作流程是什么？**

A: 
1. 提交任务时，如果当前线程数 < corePoolSize，创建核心线程
2. 如果核心线程都在工作，任务进入队列等待
3. 如果队列满了且线程数 < maximumPoolSize，创建非核心线程
4. 如果线程数达到最大值且队列满了，执行拒绝策略

### 2. 参数配置题

**Q: 如何合理设置线程池参数？**

A: 
- **CPU密集型**: corePoolSize = CPU核心数 + 1
- **IO密集型**: corePoolSize = 2 * CPU核心数
- **混合型**: 根据任务比例调整，可以通过压测确定最优值
- **队列选择**: 
  - 任务量稳定：ArrayBlockingQueue
  - 任务量不确定：SynchronousQueue
  - 需要优先级：PriorityBlockingQueue

**Q: 什么情况下 maximumPoolSize 参数无效？**

A: 使用无界队列时，如 `new LinkedBlockingQueue()` (不指定容量)，因为队列永远不会满，所以不会创建超过 corePoolSize 的线程。

### 3. 队列选择题

**Q: 不同工作队列的特点和适用场景？**

A:
- **ArrayBlockingQueue**: 有界，防止OOM，适合资源有限场景
- **LinkedBlockingQueue**: 可有界可无界，吞吐量高，适合任务较多场景
- **SynchronousQueue**: 直接交接，适合任务执行时间短的场景
- **PriorityBlockingQueue**: 支持优先级，适合有优先级需求的场景

### 4. 拒绝策略题

**Q: 四种拒绝策略的区别和使用场景？**

A:
- **AbortPolicy**: 抛异常，适合需要感知任务失败的场景
- **CallerRunsPolicy**: 调用者执行，适合不能丢失任务的场景
- **DiscardPolicy**: 静默丢弃，适合任务不重要的场景  
- **DiscardOldestPolicy**: 丢弃最老任务，适合新任务更重要的场景

### 5. 实际应用题

**Q: 线程池使用中需要注意哪些问题？**

A:
1. **资源泄露**: 必须调用 shutdown() 或 shutdownNow()
2. **任务异常**: 使用 submit() 时异常被包装，需要调用 get() 获取
3. **线程安全**: 确保任务本身是线程安全的
4. **监控**: 监控队列长度、活跃线程数等指标
5. **参数调优**: 根据业务特点和系统资源调整参数

**Q: 如何优雅关闭线程池？**

A:
```java
// 1. 停止接收新任务
executor.shutdown();

// 2. 等待已提交任务完成
try {
    if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
        // 3. 强制关闭
        executor.shutdownNow();
        // 4. 再次等待
        if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
            System.err.println("线程池未能正常关闭");
        }
    }
} catch (InterruptedException ie) {
    executor.shutdownNow();
    Thread.currentThread().interrupt();
}
```

### 6. 性能优化题

**Q: 如何监控线程池的运行状态？**

A:
```java
// 获取线程池状态信息
int corePoolSize = executor.getCorePoolSize();
int maximumPoolSize = executor.getMaximumPoolSize();
int currentPoolSize = executor.getPoolSize();
int activeCount = executor.getActiveCount();
long completedTaskCount = executor.getCompletedTaskCount();
long taskCount = executor.getTaskCount();
int queueSize = executor.getQueue().size();

// 计算队列使用率
double queueUsage = (double) queueSize / executor.getQueue().remainingCapacity();
```

**Q: 线程池参数如何动态调整？**

A:
```java
// 动态调整核心线程数
executor.setCorePoolSize(newCorePoolSize);

// 动态调整最大线程数
executor.setMaximumPoolSize(newMaximumPoolSize);

// 动态调整存活时间
executor.setKeepAliveTime(newKeepAliveTime, TimeUnit.SECONDS);

// 预启动核心线程
executor.prestartAllCoreThreads();
```

### 7. 高级应用题

**Q: 如何实现线程池的优雅降级？**

A:
```java
public class GracefulThreadPool {
    private final ThreadPoolExecutor executor;
    private final AtomicBoolean degraded = new AtomicBoolean(false);
    
    public void submit(Runnable task) {
        if (degraded.get()) {
            // 降级处理：同步执行或丢弃
            handleDegradedTask(task);
        } else {
            try {
                executor.submit(task);
            } catch (RejectedExecutionException e) {
                degraded.set(true);
                handleDegradedTask(task);
            }
        }
    }
    
    private void handleDegradedTask(Runnable task) {
        // 降级策略：可以选择同步执行、缓存或丢弃
        task.run(); // 同步执行
    }
}
```

**Q: 如何实现带有优先级的线程池？**

A:
```java
public class PriorityTask implements Runnable, Comparable<PriorityTask> {
    private final int priority;
    private final Runnable task;
    
    public PriorityTask(int priority, Runnable task) {
        this.priority = priority;
        this.task = task;
    }
    
    @Override
    public void run() {
        task.run();
    }
    
    @Override
    public int compareTo(PriorityTask other) {
        return Integer.compare(other.priority, this.priority); // 高优先级排前面
    }
}

// 使用优先级队列
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    2, 4, 60L, TimeUnit.SECONDS,
    new PriorityBlockingQueue<>()
);
```

## 实战代码示例

### 示例1: CPU密集型任务线程池
```java
public class CPUIntensivePool {
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
        CPU_COUNT + 1,                    // 核心线程数
        CPU_COUNT + 1,                    // 最大线程数
        0L,                               // 存活时间
        TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(100),    // 有界队列
        new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "CPU-Pool-" + counter.getAndIncrement());
                t.setDaemon(false);
                return t;
            }
        },
        new ThreadPoolExecutor.CallerRunsPolicy() // 调用者执行策略
    );
    
    public void submitCPUTask(Runnable task) {
        executor.submit(task);
    }
    
    public void shutdown() {
        executor.shutdown();
    }
}
```

### 示例2: IO密集型任务线程池
```java
public class IOIntensivePool {
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
        CPU_COUNT * 2,                    // 核心线程数
        CPU_COUNT * 4,                    // 最大线程数
        60L,                              // 存活时间
        TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(200),   // 有界队列
        Executors.defaultThreadFactory(),
        new RejectedExecutionHandler() {  // 自定义拒绝策略
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                System.err.println("任务被拒绝: " + r.toString());
                // 可以实现重试、记录日志等逻辑
            }
        }
    );
    
    public Future<?> submitIOTask(Callable<?> task) {
        return executor.submit(task);
    }
}
```

### 示例3: 可监控的线程池
```java
public class MonitorableThreadPool {
    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService monitor;
    
    public MonitorableThreadPool() {
        this.executor = new ThreadPoolExecutor(
            5, 10, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100)
        );
        
        this.monitor = Executors.newScheduledThreadPool(1);
        startMonitoring();
    }
    
    private void startMonitoring() {
        monitor.scheduleAtFixedRate(() -> {
            System.out.println("=== 线程池状态 ===");
            System.out.println("核心线程数: " + executor.getCorePoolSize());
            System.out.println("当前线程数: " + executor.getPoolSize());
            System.out.println("活跃线程数: " + executor.getActiveCount());
            System.out.println("队列大小: " + executor.getQueue().size());
            System.out.println("已完成任务数: " + executor.getCompletedTaskCount());
            System.out.println("总任务数: " + executor.getTaskCount());
            System.out.println("==================");
        }, 0, 10, TimeUnit.SECONDS);
    }
    
    public void submit(Runnable task) {
        executor.submit(task);
    }
    
    public void shutdown() {
        executor.shutdown();
        monitor.shutdown();
    }
}
```

### 示例4: 动态调整参数的线程池
```java
public class DynamicThreadPool {
    private final ThreadPoolExecutor executor;
    private volatile boolean autoAdjust = true;
    
    public DynamicThreadPool() {
        this.executor = new ThreadPoolExecutor(
            2, 10, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(50)
        );
        
        // 启动自动调整线程
        startAutoAdjustment();
    }
    
    private void startAutoAdjustment() {
        ScheduledExecutorService adjuster = Executors.newScheduledThreadPool(1);
        adjuster.scheduleAtFixedRate(() -> {
            if (!autoAdjust) return;
            
            int queueSize = executor.getQueue().size();
            int activeCount = executor.getActiveCount();
            int corePoolSize = executor.getCorePoolSize();
            
            // 根据队列使用情况动态调整
            if (queueSize > 30 && corePoolSize < 8) {
                executor.setCorePoolSize(corePoolSize + 1);
                System.out.println("增加核心线程数至: " + (corePoolSize + 1));
            } else if (queueSize < 10 && activeCount < corePoolSize - 1 && corePoolSize > 2) {
                executor.setCorePoolSize(corePoolSize - 1);
                System.out.println("减少核心线程数至: " + (corePoolSize - 1));
            }
        }, 0, 30, TimeUnit.SECONDS);
    }
    
    public void setAutoAdjust(boolean autoAdjust) {
        this.autoAdjust = autoAdjust;
    }
}
```

## 最佳实践

### 1. 参数设置原则
- **明确任务类型**: CPU密集型 vs IO密集型
- **评估系统资源**: CPU核心数、内存大小
- **考虑业务特点**: 任务执行时间、并发量、优先级需求
- **压测验证**: 通过实际压测确定最优参数

### 2. 队列选择策略
- **有界队列**: 防止内存溢出，适合生产环境
- **队列大小**: 一般设置为核心线程数的2-4倍
- **监控队列**: 监控队列使用率，及时调整

### 3. 异常处理
```java
// 使用 submit 时要处理异常
Future<?> future = executor.submit(() -> {
    // 可能抛异常的任务
});

try {
    future.get(); // 获取异常
} catch (ExecutionException e) {
    Throwable cause = e.getCause();
    // 处理实际异常
}
```

### 4. 资源管理
```java
// 使用 try-with-resources 或确保关闭
public class ThreadPoolManager implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    
    @Override
    public void close() {
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
```

### 5. 监控指标
- **队列使用率**: 队列大小 / 队列容量
- **线程使用率**: 活跃线程数 / 最大线程数  
- **任务完成率**: 已完成任务数 / 总任务数
- **拒绝任务数**: 监控拒绝策略执行次数
- **平均执行时间**: 监控任务执行效率

### 6. 常见陷阱
1. **使用 Executors 创建线程池**: 可能导致OOM
2. **忘记关闭线程池**: 导致资源泄露
3. **线程池参数设置不当**: 影响性能
4. **异常处理不当**: 任务异常被吞掉
5. **共享可变状态**: 线程安全问题

这份文档涵盖了 ThreadPoolExecutor 的核心概念、参数详解、常见面试题和实战示例，希望对你的学习和面试有帮助！