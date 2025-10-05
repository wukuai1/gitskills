import org.redisson.Redisson;
import org.redisson.api.*;
import org.redisson.config.Config;
import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redisson与Lettuce对比示例
 * 通过相同功能的不同实现来展示两者的差异
 */
public class ComparisonExample {
    
    private RedissonClient redissonClient;
    private RedisClient lettuceClient;
    private StatefulRedisConnection<String, String> lettuceConnection;
    
    public ComparisonExample() {
        // 初始化Redisson
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        this.redissonClient = Redisson.create(config);
        
        // 初始化Lettuce
        this.lettuceClient = RedisClient.create("redis://127.0.0.1:6379");
        this.lettuceConnection = lettuceClient.connect();
    }
    
    /**
     * 分布式锁实现对比
     */
    public void distributedLockComparison() {
        System.out.println("=== 分布式锁实现对比 ===");
        
        // Redisson实现 - 简单直观
        System.out.println("\n--- Redisson分布式锁 ---");
        RLock redissonLock = redissonClient.getLock("comparison_lock");
        
        try {
            if (redissonLock.tryLock(10, 30, TimeUnit.SECONDS)) {
                System.out.println("Redisson: 获得锁");
                Thread.sleep(2000); // 模拟业务处理
                System.out.println("Redisson: 业务处理完成");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (redissonLock.isHeldByCurrentThread()) {
                redissonLock.unlock();
                System.out.println("Redisson: 释放锁");
            }
        }
        
        // Lettuce实现 - 需要手动实现锁逻辑
        System.out.println("\n--- Lettuce分布式锁 ---");
        RedisCommands<String, String> commands = lettuceConnection.sync();
        String lockKey = "lettuce_comparison_lock";
        String lockValue = UUID.randomUUID().toString();
        
        // 获取锁的Lua脚本
        String acquireLockScript = """
            if redis.call('set', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2]) then
                return 1
            else
                return 0
            end
            """;
        
        // 释放锁的Lua脚本
        String releaseLockScript = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;
        
        try {
            // 尝试获取锁
            Object result = commands.eval(acquireLockScript, ScriptOutputType.INTEGER,
                new String[]{lockKey}, lockValue, "30");
            
            if (Long.valueOf(1).equals(result)) {
                System.out.println("Lettuce: 获得锁");
                Thread.sleep(2000); // 模拟业务处理
                System.out.println("Lettuce: 业务处理完成");
                
                // 释放锁
                Object releaseResult = commands.eval(releaseLockScript, ScriptOutputType.INTEGER,
                    new String[]{lockKey}, lockValue);
                System.out.println("Lettuce: 释放锁，结果: " + releaseResult);
            } else {
                System.out.println("Lettuce: 获取锁失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 分布式计数器实现对比
     */
    public void distributedCounterComparison() {
        System.out.println("\n=== 分布式计数器实现对比 ===");
        
        // Redisson实现
        System.out.println("\n--- Redisson分布式计数器 ---");
        RAtomicLong redissonCounter = redissonClient.getAtomicLong("redisson_counter");
        redissonCounter.set(0);
        
        long value1 = redissonCounter.incrementAndGet();
        long value2 = redissonCounter.addAndGet(5);
        boolean casResult = redissonCounter.compareAndSet(6, 10);
        
        System.out.println("Redisson: 递增后值: " + value1);
        System.out.println("Redisson: 加5后值: " + value2);
        System.out.println("Redisson: CAS操作结果: " + casResult + ", 当前值: " + redissonCounter.get());
        
        // Lettuce实现
        System.out.println("\n--- Lettuce分布式计数器 ---");
        RedisCommands<String, String> commands = lettuceConnection.sync();
        String counterKey = "lettuce_counter";
        
        commands.set(counterKey, "0");
        
        Long lettuceValue1 = commands.incr(counterKey);
        Long lettuceValue2 = commands.incrby(counterKey, 5);
        
        // CAS操作需要使用Lua脚本
        String casScript = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                redis.call('set', KEYS[1], ARGV[2])
                return 1
            else
                return 0
            end
            """;
        
        Object lettuceCasResult = commands.eval(casScript, ScriptOutputType.INTEGER,
            new String[]{counterKey}, "6", "10");
        String currentValue = commands.get(counterKey);
        
        System.out.println("Lettuce: 递增后值: " + lettuceValue1);
        System.out.println("Lettuce: 加5后值: " + lettuceValue2);
        System.out.println("Lettuce: CAS操作结果: " + lettuceCasResult + ", 当前值: " + currentValue);
    }
    
    /**
     * 分布式队列实现对比
     */
    public void distributedQueueComparison() throws InterruptedException {
        System.out.println("\n=== 分布式队列实现对比 ===");
        
        // Redisson实现
        System.out.println("\n--- Redisson分布式队列 ---");
        RBlockingQueue<String> redissonQueue = redissonClient.getBlockingQueue("redisson_queue");
        
        // 清空队列
        redissonQueue.clear();
        
        // 生产消息
        redissonQueue.offer("redisson_message_1");
        redissonQueue.offer("redisson_message_2");
        System.out.println("Redisson: 生产了2条消息");
        
        // 消费消息
        String msg1 = redissonQueue.poll(1, TimeUnit.SECONDS);
        String msg2 = redissonQueue.poll(1, TimeUnit.SECONDS);
        System.out.println("Redisson: 消费消息1: " + msg1);
        System.out.println("Redisson: 消费消息2: " + msg2);
        
        // Lettuce实现 - 使用Redis的List结构
        System.out.println("\n--- Lettuce分布式队列 ---");
        RedisCommands<String, String> commands = lettuceConnection.sync();
        String queueKey = "lettuce_queue";
        
        // 清空队列
        commands.del(queueKey);
        
        // 生产消息（右进左出，FIFO）
        commands.rpush(queueKey, "lettuce_message_1", "lettuce_message_2");
        System.out.println("Lettuce: 生产了2条消息");
        
        // 消费消息
        String lettuceMsg1 = commands.lpop(queueKey);
        String lettuceMsg2 = commands.lpop(queueKey);
        System.out.println("Lettuce: 消费消息1: " + lettuceMsg1);
        System.out.println("Lettuce: 消费消息2: " + lettuceMsg2);
        
        // 阻塞消费（Lettuce）
        System.out.println("\n--- 阻塞消费对比 ---");
        
        // Redisson阻塞消费
        new Thread(() -> {
            try {
                Thread.sleep(2000); // 延迟2秒后生产消息
                redissonQueue.offer("delayed_redisson_message");
                System.out.println("Redisson: 延迟生产消息");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
        
        String delayedMsg = redissonQueue.take(); // 阻塞等待
        System.out.println("Redisson: 阻塞消费到消息: " + delayedMsg);
        
        // Lettuce阻塞消费
        new Thread(() -> {
            try {
                Thread.sleep(2000); // 延迟2秒后生产消息
                commands.rpush(queueKey, "delayed_lettuce_message");
                System.out.println("Lettuce: 延迟生产消息");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
        
        KeyValue<String, String> delayedLettuceMsg = commands.blpop(5, queueKey); // 阻塞等待5秒
        if (delayedLettuceMsg != null) {
            System.out.println("Lettuce: 阻塞消费到消息: " + delayedLettuceMsg.getValue());
        }
    }
    
    /**
     * 发布订阅实现对比
     */
    public void pubSubComparison() throws InterruptedException {
        System.out.println("\n=== 发布订阅实现对比 ===");
        
        // Redisson实现
        System.out.println("\n--- Redisson发布订阅 ---");
        RTopic redissonTopic = redissonClient.getTopic("redisson_topic");
        
        // 订阅
        redissonTopic.addListener(String.class, (channel, message) -> {
            System.out.println("Redisson订阅者收到: [" + channel + "] " + message);
        });
        
        // 发布
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                redissonTopic.publish("Hello from Redisson!");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
        
        Thread.sleep(2000);
        
        // Lettuce实现
        System.out.println("\n--- Lettuce发布订阅 ---");
        // 注意：Lettuce的发布订阅实现较为复杂，需要单独的连接
        // 这里简化展示发布功能
        RedisCommands<String, String> commands = lettuceConnection.sync();
        
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                Long subscribers = commands.publish("lettuce_topic", "Hello from Lettuce!");
                System.out.println("Lettuce: 发布消息给 " + subscribers + " 个订阅者");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
        
        Thread.sleep(2000);
    }
    
    /**
     * 性能对比示例
     */
    public void performanceComparison() {
        System.out.println("\n=== 性能对比示例 ===");
        
        int iterations = 1000;
        
        // Redisson性能测试
        long redissonStart = System.currentTimeMillis();
        RMap<String, String> redissonMap = redissonClient.getMap("perf_test_redisson");
        for (int i = 0; i < iterations; i++) {
            redissonMap.put("key" + i, "value" + i);
        }
        long redissonEnd = System.currentTimeMillis();
        System.out.println("Redisson: " + iterations + "次PUT操作耗时: " + (redissonEnd - redissonStart) + "ms");
        
        // Lettuce性能测试
        long lettuceStart = System.currentTimeMillis();
        RedisCommands<String, String> commands = lettuceConnection.sync();
        for (int i = 0; i < iterations; i++) {
            commands.hset("perf_test_lettuce", "key" + i, "value" + i);
        }
        long lettuceEnd = System.currentTimeMillis();
        System.out.println("Lettuce: " + iterations + "次HSET操作耗时: " + (lettuceEnd - lettuceStart) + "ms");
        
        // 清理测试数据
        redissonMap.clear();
        commands.del("perf_test_lettuce");
    }
    
    public void close() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
        if (lettuceConnection != null) {
            lettuceConnection.close();
        }
        if (lettuceClient != null) {
            lettuceClient.shutdown();
        }
    }
    
    public static void main(String[] args) {
        ComparisonExample example = new ComparisonExample();
        
        try {
            example.distributedLockComparison();
            example.distributedCounterComparison();
            example.distributedQueueComparison();
            example.pubSubComparison();
            example.performanceComparison();
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            example.close();
        }
    }
}