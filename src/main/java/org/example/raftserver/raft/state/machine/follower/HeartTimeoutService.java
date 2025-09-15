package org.example.raftserver.raft.state.machine.follower;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.raftserver.raft.conf.RaftConfig;
import org.example.raftserver.raft.enums.NodeType;
import org.example.raftserver.raft.state.event.StateTransitionEvent;
import org.example.raftserver.raft.util.event.EventManager;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class HeartTimeoutService {
    private final TaskScheduler taskScheduler;
    // 定时任务引用
    private volatile ScheduledFuture<?> electionFuture;
    private final Object lock = new Object();
    // 发布事件
    private final EventManager eventManager;
    private final RaftConfig raftConfig;

    public void startHeartTimeout() {
        synchronized(lock) {
            if(electionFuture != null && !electionFuture.isCancelled()) {
                electionFuture.cancel(false);
            }

            log.debug("启动选举超时定时器: {}ms", raftConfig.getHeartbeatTimeout());

            Runnable timeoutTask = () -> {
                log.info("选举超时，尝试成为 Candidate...");
                eventManager.publish(new StateTransitionEvent(NodeType.CANDIDATE));
                stopElectionTimeout();
            };

            // 调度任务（单词）
            electionFuture = taskScheduler.schedule(timeoutTask, Instant.now().plusMillis(raftConfig.getHeartbeatTimeout()));
        }
    }

    public void resetElectionTimeout() {
        synchronized(lock) {
            startHeartTimeout();
        }
    }

    public void stopElectionTimeout() {
        synchronized(lock) {
            if(electionFuture != null && !electionFuture.isCancelled()) {
                boolean cancel = electionFuture.cancel(false);
                log.debug("取消选举超时定时器: {}", cancel);
                electionFuture = null;
            }
        }
    }
}
