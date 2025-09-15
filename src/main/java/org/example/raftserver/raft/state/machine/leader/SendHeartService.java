package org.example.raftserver.raft.state.machine.leader;

import lombok.extern.slf4j.Slf4j;
import org.example.raftserver.raft.MetaData;
import org.example.raftserver.raft.conf.RaftConfig;
import org.example.raftserver.raft.enums.NodeType;
import org.example.raftserver.raft.log.LogEntity;
import org.example.raftserver.raft.rpc.AppendEntriesRequest;
import org.example.raftserver.raft.rpc.AppendEntriesResponse;
import org.example.raftserver.raft.rpc.RpcClient;
import org.example.raftserver.raft.state.event.StateTransitionEvent;
import org.example.raftserver.raft.util.event.EventManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;

@Service
@Slf4j
public class SendHeartService {
    private final TaskScheduler taskScheduler;
    private final ExecutorService rpcExecutor;
    private final MetaData metaData;
    private final EventManager eventManager;
    private final RpcClient rpcClient;
    private final RaftConfig raftConfig;

    private final Object lock = new Object();
    private volatile ScheduledFuture<?> heartbeatFuture; // 用于取消任务

    public SendHeartService(RaftConfig raftConfig, TaskScheduler taskScheduler, MetaData metaData, EventManager eventManager, RpcClient rpcClient,
                            @Qualifier("raftRpcExecutor") ExecutorService rpcExecutor) {
        this.taskScheduler = taskScheduler;
        this.raftConfig = raftConfig;
        this.metaData = metaData;
        this.eventManager = eventManager;
        this.rpcClient = rpcClient;
        this.rpcExecutor = rpcExecutor;
    }

    /**
     * 启动周期性心跳/日志复制
     */
    public void startHeart() {
        synchronized(lock) {
            if(heartbeatFuture != null && !heartbeatFuture.isCancelled()) {
                log.warn("心跳任务已在运行，无需重复启动");
                return;
            }

            Runnable heartbeatJob = () -> {
                for(String peer : raftConfig.getPeers()) {
                    if(peer.equals(raftConfig.getNodeId())) {
                        continue; // 跳过自己
                    }

                    // 提交每个 peer 的发送任务到线程池
                    rpcExecutor.submit(() -> sendAppendEntriesToPeer(peer));
                }
            };

            long interval = raftConfig.getHeartbeatInterval();
            try {
                this.heartbeatFuture = taskScheduler.scheduleAtFixedRate(heartbeatJob, interval);
                log.info("✅ 心跳任务已启动，周期 {}ms", interval);
            }catch(Exception e) {
                log.error("❌ 启动心跳任务失败", e);
            }
        }
    }

    /**
     * 向指定 peer 发送 AppendEntries 请求（包含心跳）
     */
    private void sendAppendEntriesToPeer(String peer) {
        try {
            int nextIndex = metaData.getNextSend(peer);
            int prevLogIndex = nextIndex - 1;
            LogEntity prevLog = metaData.getLogs().getLog(prevLogIndex);
            int prevLogTerm = (prevLog != null) ? prevLog.getTerm() : 0;

            LogEntity logToAppend = null;
            if(metaData.getLogs().getLog(nextIndex) != null) {
                log.info("{}的nextIndex为{}", peer, nextIndex);
                logToAppend = metaData.getLogs().getLog(nextIndex);
                log.info("广播日志：{} {}", logToAppend.getTerm(), logToAppend.getIndex());
            }

            AppendEntriesRequest request = new AppendEntriesRequest(metaData.getCurrentTerm(),
                                                                    raftConfig.getNodeId(),
                                                                    prevLogIndex,
                                                                    prevLogTerm,
                                                                    logToAppend,
                                                                    metaData.getLogs().getCommitIndex());
            // 直接发送 RPC
            AppendEntriesResponse response = rpcClient.sendAppendEntries(raftConfig.getPeerAddress().get(peer), request);

            // 处理响应
            if(response != null) {
                handleHeartbeatSuccess(peer, response, logToAppend);
            }else {
                handleHeartbeatFailure(peer, new RuntimeException("收到空响应"));
            }

        }catch(Exception e) {
            handleHeartbeatFailure(peer, e);
        }
    }

    /**
     * 处理成功响应
     */
    private void handleHeartbeatSuccess(String peer, AppendEntriesResponse response, LogEntity logEntity) {
        log.debug("{}: 收到响应 success=true, term={}, type={}", peer, response.term(), response.type());
        switch(response.type()) {
            case TERM_TOO_SMALL:
                log.warn("{}: leader的任期太小，准备退位", peer);
                metaData.setCurrentTerm(response.term());
                eventManager.publish(new StateTransitionEvent(NodeType.FOLLOWER));
                stopHeart(); // 主动停止
                return;
            case APPEND_SUCCESS:
                log.info("{}: 日志追加成功，准备更新 nextIndex和alreadyIndex", peer);
                // 更新下一个发送给peer的索引
                metaData.increaseNextCommit(peer);
                // 更新peer已经同步的索引
                metaData.increaseAlreadyCommit(peer);
                // 检查当前日志是否可以提交（多数复制）
                int logCopyCount = metaData.getLogCopyCount(logEntity.getIndex());
                if(logCopyCount>=raftConfig.getPeerCount()/2+1){
                    log.info("{}日志已满足多数复制，可以进行提交", logEntity.getIndex());
                    metaData.getLogs().commitLog(logEntity.getIndex());
                }

                break;
            case HEART_SUCCESS:
                // 心跳成功，无需操作
                break;
            case APPEND_FAIL, HEART_FAIL:
                log.warn("{}: 日志不一致，准备回退 nextIndex", peer);
                metaData.reduceNextCommit(peer);
                break;
            default:
                log.warn("{}: 未知响应类型: {}", peer, response.type());
        }
    }

    /**
     * 处理失败（网络异常、超时等）
     */
    private void handleHeartbeatFailure(String peer, Throwable throwable) {
        log.warn("向 {} 发送 AppendEntries 失败: {}", peer, throwable.getMessage());
    }

    /**
     * 停止心跳任务
     */
    public void stopHeart() {
        synchronized(lock) {
            if(heartbeatFuture != null) {
                boolean cancelled = heartbeatFuture.cancel(true);
                log.debug(cancelled ? "✅ 心跳任务已取消" : "⚠️ 心跳任务取消失败");
                heartbeatFuture = null;
            }else {
                log.warn("ℹ️ 无运行中的心跳任务");
            }
        }
    }
}