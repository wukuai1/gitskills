import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Lettuce使用示例
 * 展示Lettuce的主要功能和使用方法
 */
public class LettuceExample {
    
    private RedisClient client;
    private StatefulRedisConnection<String, String> connection;
    
    public LettuceExample() {
        // 创建Redis客户端
        this.client = RedisClient.create("redis://127.0.0.1:6379");
        this.connection = client.connect();
    }
    
    /**
     * 基本同步操作示例
     */
    public void basicSyncOperations() {
        RedisCommands<String, String> syncCommands = connection.sync();
        
        // 基本字符串操作
        syncCommands.set("key1", "value1");
        String value = syncCommands.get("key1");
        System.out.println("key1的值: " + value);
        
        // 带过期时间的设置
        syncCommands.setex("key2", 60, "value2");
        Long ttl = syncCommands.ttl("key2");
        System.out.println("key2的TTL: " + ttl + "秒");
        
        // 数值操作
        syncCommands.set("counter", "0");
        Long newValue = syncCommands.incr("counter");
        System.out.println("计数器值: " + newValue);
        
        // 列表操作
        syncCommands.lpush("mylist", "item1", "item2", "item3");
        List<String> list = syncCommands.lrange("mylist", 0, -1);
        System.out.println("列表内容: " + list);
        
        // 集合操作
        syncCommands.sadd("myset", "member1", "member2", "member3");
        Long setSize = syncCommands.scard("myset");
        System.out.println("集合大小: " + setSize);
        
        // 哈希操作
        syncCommands.hset("myhash", "field1", "value1");
        syncCommands.hset("myhash", "field2", "value2");
        String hashValue = syncCommands.hget("myhash", "field1");
        System.out.println("哈希字段值: " + hashValue);
    }
    
    /**
     * 异步操作示例
     */
    public void asyncOperations() {
        RedisAsyncCommands<String, String> asyncCommands = connection.async();
        
        // 异步设置值
        RedisFuture<String> setFuture = asyncCommands.set("async_key", "async_value");
        
        // 异步获取值
        RedisFuture<String> getFuture = asyncCommands.get("async_key");
        
        // 处理异步结果
        getFuture.thenAccept(value -> 
            System.out.println("异步获取的值: " + value)
        ).thenRun(() -> 
            System.out.println("异步操作完成")
        );
        
        // 等待异步操作完成
        try {
            setFuture.get(5, TimeUnit.SECONDS);
            getFuture.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 响应式操作示例
     */
    public void reactiveOperations() {
        RedisReactiveCommands<String, String> reactiveCommands = connection.reactive();
        
        // 响应式操作链
        Mono<String> result = reactiveCommands
            .set("reactive_key", "reactive_value")
            .then(reactiveCommands.get("reactive_key"))
            .doOnNext(value -> System.out.println("响应式获取的值: " + value));
        
        // 订阅并等待完成
        result.block(Duration.ofSeconds(5));
        
        // 批量操作
        reactiveCommands.mset("key1", "value1", "key2", "value2", "key3", "value3")
            .then(reactiveCommands.mget("key1", "key2", "key3"))
            .doOnNext(values -> System.out.println("批量获取的值: " + values))
            .block(Duration.ofSeconds(5));
    }
    
    /**
     * 事务操作示例
     */
    public void transactionOperations() {
        RedisCommands<String, String> syncCommands = connection.sync();
        
        // 开始事务
        syncCommands.multi();
        
        try {
            // 事务中的操作
            syncCommands.set("tx_key1", "tx_value1");
            syncCommands.set("tx_key2", "tx_value2");
            syncCommands.incr("tx_counter");
            
            // 执行事务
            TransactionResult result = syncCommands.exec();
            System.out.println("事务执行结果: " + result);
            
        } catch (Exception e) {
            // 回滚事务
            syncCommands.discard();
            System.out.println("事务回滚");
        }
    }
    
    /**
     * 管道操作示例
     */
    public void pipelineOperations() {
        RedisAsyncCommands<String, String> asyncCommands = connection.async();
        
        // 禁用自动刷新以启用管道
        asyncCommands.setAutoFlushCommands(false);
        
        try {
            // 批量操作
            RedisFuture<String> f1 = asyncCommands.set("pipe_key1", "pipe_value1");
            RedisFuture<String> f2 = asyncCommands.set("pipe_key2", "pipe_value2");
            RedisFuture<String> f3 = asyncCommands.set("pipe_key3", "pipe_value3");
            
            // 刷新命令
            asyncCommands.flushCommands();
            
            // 等待所有操作完成
            CompletableFuture.allOf(f1.toCompletableFuture(), 
                                  f2.toCompletableFuture(), 
                                  f3.toCompletableFuture())
                .thenRun(() -> System.out.println("管道操作完成"))
                .get(5, TimeUnit.SECONDS);
                
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 重新启用自动刷新
            asyncCommands.setAutoFlushCommands(true);
        }
    }
    
    /**
     * Lua脚本示例
     */
    public void luaScriptOperations() {
        RedisCommands<String, String> syncCommands = connection.sync();
        
        // 原子递增脚本
        String script = """
            local current = redis.call('get', KEYS[1])
            if current == false then
                current = 0
            end
            local new_value = current + ARGV[1]
            redis.call('set', KEYS[1], new_value)
            return new_value
            """;
        
        // 执行脚本
        Object result = syncCommands.eval(script, ScriptOutputType.INTEGER, 
            new String[]{"lua_counter"}, "5");
        System.out.println("Lua脚本执行结果: " + result);
        
        // 分布式锁实现（使用Lua脚本）
        String lockScript = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;
        
        String lockKey = "distributed_lock";
        String lockValue = UUID.randomUUID().toString();
        
        // 尝试获取锁
        String lockResult = syncCommands.set(lockKey, lockValue, 
            SetArgs.Builder.nx().ex(30));
        
        if ("OK".equals(lockResult)) {
            System.out.println("获得分布式锁");
            try {
                // 模拟业务逻辑
                Thread.sleep(2000);
                System.out.println("执行业务逻辑");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                // 释放锁
                Object unlockResult = syncCommands.eval(lockScript, 
                    ScriptOutputType.INTEGER, new String[]{lockKey}, lockValue);
                System.out.println("释放锁结果: " + unlockResult);
            }
        } else {
            System.out.println("获取锁失败");
        }
    }
    
    /**
     * 发布订阅示例
     */
    public void pubSubOperations() throws InterruptedException {
        // 创建发布订阅连接
        StatefulRedisPubSubConnection<String, String> pubSubConnection = 
            client.connectPubSub();
        
        // 添加监听器
        pubSubConnection.addListener(new RedisPubSubListener<String, String>() {
            @Override
            public void message(String channel, String message) {
                System.out.println("收到消息 [" + channel + "]: " + message);
            }
            
            @Override
            public void message(String pattern, String channel, String message) {
                System.out.println("模式匹配消息 [" + pattern + "][" + channel + "]: " + message);
            }
            
            @Override
            public void subscribed(String channel, long count) {
                System.out.println("订阅频道: " + channel + ", 订阅数: " + count);
            }
            
            @Override
            public void psubscribed(String pattern, long count) {
                System.out.println("模式订阅: " + pattern + ", 订阅数: " + count);
            }
            
            @Override
            public void unsubscribed(String channel, long count) {
                System.out.println("取消订阅频道: " + channel + ", 订阅数: " + count);
            }
            
            @Override
            public void punsubscribed(String pattern, long count) {
                System.out.println("取消模式订阅: " + pattern + ", 订阅数: " + count);
            }
        });
        
        // 订阅频道
        RedisPubSubCommands<String, String> pubSubCommands = pubSubConnection.sync();
        pubSubCommands.subscribe("news", "updates");
        
        // 在另一个线程中发布消息
        new Thread(() -> {
            RedisCommands<String, String> publishCommands = connection.sync();
            try {
                Thread.sleep(1000);
                publishCommands.publish("news", "重要新闻1");
                Thread.sleep(1000);
                publishCommands.publish("updates", "系统更新1");
                Thread.sleep(1000);
                publishCommands.publish("news", "重要新闻2");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
        
        // 等待消息处理
        Thread.sleep(5000);
        
        // 取消订阅
        pubSubCommands.unsubscribe("news", "updates");
        pubSubConnection.close();
    }
    
    /**
     * 连接池示例（需要额外的依赖）
     */
    public void connectionPoolExample() {
        // 注意：这需要添加 commons-pool2 依赖
        System.out.println("连接池示例需要额外的commons-pool2依赖");
        
        /*
        GenericObjectPoolConfig<StatefulRedisConnection<String, String>> config = 
            new GenericObjectPoolConfig<>();
        config.setMaxTotal(20);
        config.setMaxIdle(10);
        config.setMinIdle(5);
        
        GenericObjectPool<StatefulRedisConnection<String, String>> pool = 
            ConnectionPoolSupport.createGenericObjectPool(() -> client.connect(), config);
        
        try (StatefulRedisConnection<String, String> pooledConnection = pool.borrowObject()) {
            RedisCommands<String, String> commands = pooledConnection.sync();
            commands.set("pooled_key", "pooled_value");
            System.out.println("使用连接池设置值: " + commands.get("pooled_key"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        */
    }
    
    public void close() {
        if (connection != null) {
            connection.close();
        }
        if (client != null) {
            client.shutdown();
        }
    }
    
    public static void main(String[] args) {
        LettuceExample example = new LettuceExample();
        
        try {
            System.out.println("=== 基本同步操作示例 ===");
            example.basicSyncOperations();
            
            System.out.println("\n=== 异步操作示例 ===");
            example.asyncOperations();
            
            System.out.println("\n=== 响应式操作示例 ===");
            example.reactiveOperations();
            
            System.out.println("\n=== 事务操作示例 ===");
            example.transactionOperations();
            
            System.out.println("\n=== 管道操作示例 ===");
            example.pipelineOperations();
            
            System.out.println("\n=== Lua脚本示例 ===");
            example.luaScriptOperations();
            
            System.out.println("\n=== 发布订阅示例 ===");
            example.pubSubOperations();
            
            System.out.println("\n=== 连接池示例 ===");
            example.connectionPoolExample();
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            example.close();
        }
    }
}