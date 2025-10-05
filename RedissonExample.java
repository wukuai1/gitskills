import org.redisson.Redisson;
import org.redisson.api.*;
import org.redisson.config.Config;

import java.util.concurrent.TimeUnit;

/**
 * Redisson使用示例
 * 展示Redisson的主要功能和使用方法
 */
public class RedissonExample {
    
    private RedissonClient redissonClient;
    
    public RedissonExample() {
        // 配置Redisson
        Config config = new Config();
        config.useSingleServer()
              .setAddress("redis://127.0.0.1:6379")
              .setConnectionMinimumIdleSize(10)
              .setConnectionPoolSize(64)
              .setIdleConnectionTimeout(10000)
              .setConnectTimeout(10000)
              .setTimeout(3000);
        
        this.redissonClient = Redisson.create(config);
    }
    
    /**
     * 分布式锁示例
     */
    public void distributedLockExample() {
        RLock lock = redissonClient.getLock("myLock");
        
        try {
            // 尝试加锁，最多等待10秒，锁定后30秒自动解锁
            boolean isLocked = lock.tryLock(10, 30, TimeUnit.SECONDS);
            
            if (isLocked) {
                System.out.println("获得锁，执行业务逻辑");
                // 模拟业务处理
                Thread.sleep(5000);
                System.out.println("业务逻辑执行完成");
            } else {
                System.out.println("获取锁失败");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // 释放锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                System.out.println("锁已释放");
            }
        }
    }
    
    /**
     * 分布式Map示例
     */
    public void distributedMapExample() {
        RMap<String, String> map = redissonClient.getMap("myMap");
        
        // 基本操作
        map.put("key1", "value1");
        map.put("key2", "value2");
        
        System.out.println("key1的值: " + map.get("key1"));
        System.out.println("Map大小: " + map.size());
        
        // 批量操作
        map.putAll(java.util.Map.of(
            "key3", "value3",
            "key4", "value4"
        ));
        
        // 遍历
        map.entrySet().forEach(entry -> 
            System.out.println(entry.getKey() + " = " + entry.getValue())
        );
        
        // 过期时间
        map.expire(60, TimeUnit.SECONDS);
    }
    
    /**
     * 分布式队列示例
     */
    public void distributedQueueExample() throws InterruptedException {
        RBlockingQueue<String> queue = redissonClient.getBlockingQueue("myQueue");
        
        // 生产者线程
        new Thread(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    queue.offer("message-" + i);
                    System.out.println("生产消息: message-" + i);
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
        
        // 消费者线程
        new Thread(() -> {
            try {
                while (true) {
                    String message = queue.take(); // 阻塞等待
                    System.out.println("消费消息: " + message);
                    
                    if ("message-4".equals(message)) {
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
        
        Thread.sleep(10000); // 等待示例完成
    }
    
    /**
     * 分布式信号量示例
     */
    public void distributedSemaphoreExample() {
        RSemaphore semaphore = redissonClient.getSemaphore("mySemaphore");
        
        try {
            // 设置许可数量
            semaphore.trySetPermits(3);
            
            // 获取许可
            semaphore.acquire();
            System.out.println("获得许可，当前可用许可数: " + semaphore.availablePermits());
            
            // 模拟业务处理
            Thread.sleep(2000);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // 释放许可
            semaphore.release();
            System.out.println("释放许可，当前可用许可数: " + semaphore.availablePermits());
        }
    }
    
    /**
     * 发布订阅示例
     */
    public void pubSubExample() throws InterruptedException {
        RTopic topic = redissonClient.getTopic("myTopic");
        
        // 订阅者
        topic.addListener(String.class, (channel, message) -> {
            System.out.println("收到消息 [" + channel + "]: " + message);
        });
        
        // 发布消息
        new Thread(() -> {
            try {
                for (int i = 0; i < 3; i++) {
                    topic.publish("Hello World " + i);
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
        
        Thread.sleep(5000); // 等待消息处理
    }
    
    /**
     * 分布式原子操作示例
     */
    public void atomicOperationExample() {
        RAtomicLong atomicLong = redissonClient.getAtomicLong("myCounter");
        
        // 设置初始值
        atomicLong.set(0);
        
        // 原子操作
        long value1 = atomicLong.incrementAndGet();
        System.out.println("递增后的值: " + value1);
        
        long value2 = atomicLong.addAndGet(10);
        System.out.println("加10后的值: " + value2);
        
        // CAS操作
        boolean success = atomicLong.compareAndSet(11, 100);
        System.out.println("CAS操作结果: " + success + ", 当前值: " + atomicLong.get());
    }
    
    /**
     * 分布式Set示例
     */
    public void distributedSetExample() {
        RSet<String> set = redissonClient.getSet("mySet");
        
        // 添加元素
        set.add("element1");
        set.add("element2");
        set.add("element3");
        set.add("element1"); // 重复元素不会被添加
        
        System.out.println("Set大小: " + set.size());
        System.out.println("是否包含element1: " + set.contains("element1"));
        
        // 遍历
        set.forEach(System.out::println);
        
        // 集合操作
        RSet<String> otherSet = redissonClient.getSet("otherSet");
        otherSet.add("element2");
        otherSet.add("element4");
        
        // 交集
        set.retainAll(otherSet);
        System.out.println("交集后的元素:");
        set.forEach(System.out::println);
    }
    
    public void close() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }
    
    public static void main(String[] args) {
        RedissonExample example = new RedissonExample();
        
        try {
            System.out.println("=== 分布式锁示例 ===");
            example.distributedLockExample();
            
            System.out.println("\n=== 分布式Map示例 ===");
            example.distributedMapExample();
            
            System.out.println("\n=== 分布式队列示例 ===");
            example.distributedQueueExample();
            
            System.out.println("\n=== 分布式信号量示例 ===");
            example.distributedSemaphoreExample();
            
            System.out.println("\n=== 发布订阅示例 ===");
            example.pubSubExample();
            
            System.out.println("\n=== 原子操作示例 ===");
            example.atomicOperationExample();
            
            System.out.println("\n=== 分布式Set示例 ===");
            example.distributedSetExample();
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            example.close();
        }
    }
}