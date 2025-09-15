package org.example.raftserver.raft.conf;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.*;


@Configuration
@EnableScheduling
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
        return Executors.newFixedThreadPool(cpus, r -> {
            Thread thread = new Thread(r, "raft-rpc-" + System.currentTimeMillis() % 10000);
            thread.setUncaughtExceptionHandler((t, e) -> System.err.println("Uncaught exception in " + t.getName() + ": " + e));
            return thread;
        });
    }

    /**
     * 用于处理选举超时（最多执行一次的延迟任务）
     *
     * @return
     */
    @Bean("raftElectionScheduler")
    public ScheduledExecutorService raftElectionScheduler() {
        return Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "raft-election-timer"));
    }

}