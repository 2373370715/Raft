package org.example.raftserver.raft.conf;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.*;


@Configuration
@EnableScheduling
@Slf4j
public class SchedulerConfig {

    /**
     * 用于处理心跳（不断执行的任务）
     *
     * @return
     */
    @Bean("raftTaskScheduler")
    public TaskScheduler raftTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(6);
        scheduler.setThreadNamePrefix("raft-heartbeat-scheduler-");
        // 确保任务取消后线程池能清理任务
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.initialize(); // 必须调用
        return scheduler;
    }

    /**
     * 并发执行rpc请求
     *
     * @return
     */
    @Bean("raftRpcExecutor")
    public ExecutorService raftRpcExecutor() {
        int cpus = Runtime.getRuntime().availableProcessors();
        return new ThreadPoolExecutor(cpus,                           // 核心线程数
                                      cpus * 2,                       // 最大线程数
                                      0L,                            // 空闲线程存活时间
                                      TimeUnit.SECONDS,               // 时间单位
                                      new LinkedBlockingQueue<>(1000), // 任务队列
                                      r -> {
                                          Thread thread = new Thread(r, "raft-rpc-thread-" + System.currentTimeMillis() % 10000);
                                          thread.setUncaughtExceptionHandler((t, e) -> log.error("Uncaught exception in {}", t.getName(), e));
                                          return thread;
                                      }, new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略
        );
    }


    /**
     * 用于处理选举超时（最多执行一次的延迟任务）
     *
     * @return
     */
    @Bean("raftElectionScheduler")
    public ScheduledExecutorService raftElectionScheduler() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, r -> {
            Thread thread = new Thread(r, "raft-election-timer");
            thread.setUncaughtExceptionHandler((t, e) -> log.error("Uncaught exception in {}", t.getName(), e));
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        // 设置核心线程不会超时销毁
        executor.setKeepAliveTime(0L, TimeUnit.SECONDS);
        executor.allowCoreThreadTimeOut(false);
        return executor;
    }
}