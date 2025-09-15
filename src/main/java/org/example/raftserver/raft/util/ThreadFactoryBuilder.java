package org.example.raftserver.raft.util;

import org.springframework.context.annotation.Bean;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadFactoryBuilder {
    private static final AtomicInteger threadNumber = new AtomicInteger(1);

    @Bean("raftRpcExecutor")
    public ExecutorService raftRpcExecutor() {
        int cpus = Runtime.getRuntime().availableProcessors();
        return new ThreadPoolExecutor(cpus,
                                      cpus,
                                      60L,
                                      TimeUnit.SECONDS,
                                      new LinkedBlockingQueue<>(1000),
                                      r -> new Thread(r, "raft-rpc-thread-" + threadNumber.getAndIncrement()),
                                      new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
