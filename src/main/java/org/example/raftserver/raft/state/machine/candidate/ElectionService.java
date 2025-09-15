package org.example.raftserver.raft.state.machine.candidate;

import lombok.extern.log4j.Log4j2;
import org.example.raftserver.raft.MetaData;
import org.example.raftserver.raft.conf.RaftConfig;
import org.example.raftserver.raft.enums.NodeType;
import org.example.raftserver.raft.log.LogEntity;
import org.example.raftserver.raft.rpc.RequestVoteRequest;
import org.example.raftserver.raft.rpc.RequestVoteResponse;
import org.example.raftserver.raft.state.event.StateTransitionEvent;
import org.example.raftserver.raft.util.event.EventManager;
import org.example.raftserver.raft.rpc.RpcClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 选举服务：负责在 Candidate 状态下发起并管理选举流程
 */
@Service
@Log4j2
public class ElectionService {
    private final RpcClient rpcClient;
    private final EventManager eventManager;
    private final MetaData metaData;
    // 记录票数
    private final AtomicInteger voteCount = new AtomicInteger(0);
    // 用于并发执行 RPC 请求
    private final ExecutorService rpcExecutor;
    // 用于调度选举超时任务
    private final ScheduledExecutorService schedule;
    private final RaftConfig raftConfig;
    private volatile ScheduledFuture<?> currentElectionTimeout;

    public ElectionService(RpcClient rpcClient, EventManager eventManager, MetaData metaData,
                           @Qualifier("raftRpcExecutor") ExecutorService rpcExecutor, ScheduledExecutorService schedule, RaftConfig raftConfig) {
        this.rpcClient = rpcClient;
        this.eventManager = eventManager;
        this.metaData = metaData;
        this.rpcExecutor = rpcExecutor;
        this.schedule = schedule;
        this.raftConfig = raftConfig;
    }

    /**
     * 开始选举
     */
    public void startElection() {
        log.info("🗳️ 节点 {} 开始新一轮选举", raftConfig.getNodeId());

        // 1. 任期 +1
        int newTerm = metaData.getCurrentTerm() + 1;
        metaData.setCurrentTerm(newTerm);
        log.debug("🚀 节点 {} 发起选举，新任期为 {}", raftConfig.getNodeId(), newTerm);

        // 2. 先给自己投票
        addVote();

        // 3. 获取最后一条日志信息
        LogEntity lastLog = metaData.getLogs().getLast();
        int lastLogIndex = lastLog != null ? lastLog.getIndex() : 0;
        int lastLogTerm = lastLog != null ? lastLog.getTerm() : 0;

        // 4. 随机化选举超时时间（避免脑裂）
        long timeoutMs =
                raftConfig.getElectionTimeoutMin() + new Random().nextInt(raftConfig.getElectionTimeoutMax() - raftConfig.getElectionTimeoutMin());

        // 5. 提交超时任务：检查选举结果
        currentElectionTimeout = schedule.schedule(() -> {
            try {
                int totalVotes = getVoteCount();
                int majority = raftConfig.getPeerCount() / 2 + 1; // 多数派

                if(totalVotes >= majority) {
                    log.info("✅ 获得 {} 票，超过多数（{}），成为 Leader！", totalVotes, majority);
                    eventManager.publish(new StateTransitionEvent(NodeType.LEADER));
                }else {
                    log.info("❌ 仅获得 {} 票，未达到多数（{}），重新进入 Follower 状态", totalVotes, majority);
                    eventManager.publish(new StateTransitionEvent(NodeType.FOLLOWER));
                }
            }catch(Exception e) {
                log.error("处理选举超时任务失败", e);
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);

        // 将超时任务引用暴露给 CandidateState（可选：用于提前取消）
        // 如果你需要，可以在 CandidateState 中加一个 setElectionTimeoutFuture 方法

        // 6. 并发向所有 Follower 发送 RequestVote 请求
        for(String peerId : raftConfig.getPeers()) {
            if(peerId.equals(raftConfig.getNodeId())) {
                continue; // 跳过自己
            }

            rpcExecutor.submit(() -> {
                try {
                    RequestVoteRequest request =
                            new RequestVoteRequest(metaData.getCurrentTerm(), raftConfig.getNodeId(), lastLogIndex, lastLogTerm);

                    log.debug("📤 向节点 {} 发送投票请求: {}", peerId, request);

                    RequestVoteResponse response = rpcClient.sendRequestVote(raftConfig.getPeerAddress().get(peerId), request);

                    if(response != null && response.voteGranted()) {
                        log.debug("📨 收到节点 {} 的投票", peerId);
                        addVote(); // 原子操作，线程安全
                    }else {
                        log.debug("❌ 节点 {} 拒绝投票", peerId);
                    }
                }catch(Exception e) {
                    log.warn("向节点 {} 发起投票请求失败: {}", peerId, e.getMessage());
                }
            });
        }
    }

    /**
     * 关闭服务（可用于优雅停机）
     */
    public void stopElection() {
        currentElectionTimeout.cancel(true);
    }

    private void addVote() {
        voteCount.incrementAndGet();
    }

    private int getVoteCount() {
        return voteCount.get();
    }
}