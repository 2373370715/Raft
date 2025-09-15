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

import java.util.ArrayList;
import java.util.List;
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
            // 获取要发送给该peer的下一个日志索引
            int nextIndex = metaData.getNextSend(peer);

            // prevLogIndex是下一个日志索引的前一个索引
            int prevLogIndex = nextIndex - 1;

            // 获取prevLogIndex对应日志条目的任期号
            LogEntity prevLog = null;
            if(prevLogIndex >= 0) {
                prevLog = metaData.getLog(prevLogIndex);
            }

            // 如果prevLogIndex为0或更小，表示这是初始日志条目
            int prevLogTerm = 0;
            if(prevLog != null) {
                prevLogTerm = prevLog.getTerm();
            }

            // 准备要发送的日志条目列表
            List<LogEntity> logsToSend = new ArrayList<>();
            // 从nextIndex开始发送日志，直到当前leader的最后一条日志
            for(int i = nextIndex; i <= metaData.getLogs().getLastLogIndex(); i++) {
                LogEntity log = metaData.getLog(i);
                if(log != null) {
                    logsToSend.add(log);
                }
            }

            // 构造AppendEntries请求
            AppendEntriesRequest request = new AppendEntriesRequest(metaData.getCurrentTerm(),           // 当前leader的任期
                                                                    raftConfig.getNodeId(),              // leader的ID
                                                                    prevLogIndex,                        // prevLogIndex
                                                                    prevLogTerm,                         // prevLogTerm
                                                                    logsToSend,                          // 要发送的日志条目列表
                                                                    metaData.getLogs().getCommitIndex()  // leader的commitIndex
            );

            // 发送RPC请求
            AppendEntriesResponse response = rpcClient.sendAppendEntries(raftConfig.getPeerAddress().get(peer), request);

            // 处理响应
            if(response != null) {
                handleHeartbeatSuccess(peer, response, nextIndex);
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
    private void handleHeartbeatSuccess(String peer, AppendEntriesResponse response, int nextIndex) {
        log.debug("{}: 收到响应 term={}, type={}", peer, response.term(), response.type());

        // 如果响应中的任期更大，说明当前节点已经过时
        if(response.term() > metaData.getCurrentTerm()) {
            log.warn("{}: leader的任期太小，准备退位", peer);
            metaData.setCurrentTerm(response.term());
            eventManager.publish(new StateTransitionEvent(NodeType.FOLLOWER));
            stopHeart(); // 主动停止
            return;
        }

        switch(response.type()) {
            case SUCCESS:
                // 更新下一个发送给peer的索引
                // 如果发送了日志，更新nextIndex和matchIndex
                int lastLogIndex = metaData.getLogs().getLastLogIndex();
                if(lastLogIndex >= nextIndex) {
                    log.info("{}: 日志追加成功", peer);
                    metaData.increaseNextCommit(peer);
                    metaData.increaseAlreadyCommit(peer);
                }

                // 检查是否可以提交更多日志
                checkAndCommitLogs();
                break;

            case LOG_MISMATCH:
                log.warn("{}: 日志不一致，准备回退 nextIndex", peer);
                // 减少nextIndex，下次发送更早的日志
                metaData.reduceNextCommit(peer);
                break;

            case TERM_TOO_SMALL:
                log.warn("{}: leader的任期太小，准备退位", peer);
                metaData.setCurrentTerm(response.term());
                eventManager.publish(new StateTransitionEvent(NodeType.FOLLOWER));
                stopHeart(); // 主动停止
                break;

            default:
                log.warn("{}: 未知响应类型: {}", peer, response.type());
        }
    }

    /**
     * 检查并提交可以安全提交的日志
     */
    private void checkAndCommitLogs() {
        int commitIndex = metaData.getLogs().getCommitIndex();
        int lastLogIndex = metaData.getLogs().getLastLogIndex();

        // 从当前提交索引的下一个开始检查
        for(int i = commitIndex + 1; i <= lastLogIndex; i++) {
            // 统计有多少个节点已经复制了这个日志条目
            int replicationCount = 1; // 包括自己

            for(String peer : raftConfig.getPeers()) {
                if(!peer.equals(raftConfig.getNodeId())) {
                    if(metaData.getAlreadyCopy(peer) >= i) {
                        replicationCount++;
                    }
                }
            }

            // 如果复制数量超过半数，可以提交该日志
            if(replicationCount > raftConfig.getPeerCount() / 2) {
                LogEntity logToCommit = metaData.getLog(i);
                if(logToCommit != null && logToCommit.getTerm() == metaData.getCurrentTerm()) {
                    metaData.getLogs().commitTo(i);
                    log.info("日志索引 {} 已提交，已复制到 {} 个节点", i, replicationCount);
                }
            }
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
