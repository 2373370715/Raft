package org.example.raftserver.raft;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.example.raftserver.raft.conf.RaftConfig;
import org.example.raftserver.raft.enums.NodeType;
import org.example.raftserver.raft.log.LogEntity;
import org.example.raftserver.raft.log.MyLogList;
import org.example.raftserver.raft.rpc.*;
import org.example.raftserver.raft.state.event.*;
import org.example.raftserver.raft.state.machine.candidate.CandidateState;
import org.example.raftserver.raft.state.machine.follower.FollowerState;
import org.example.raftserver.raft.state.machine.leader.LeaderState;
import org.example.raftserver.raft.state.machine.NodeState;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Raft节点主类
 */
@Log4j2
@Component
@Getter
public class RaftNode {
    private final RaftConfig raftConfig;
    // 当前节点状态
    private NodeState state;
    private final MetaData metaData;
    // 延迟容器，调用时才会创建bean
    private final ObjectProvider<LeaderState> leaderStateProvider;
    private final ObjectProvider<FollowerState> followerStateProvider;
    private final ObjectProvider<CandidateState> candidateStateProvider;


    public RaftNode(ObjectProvider<LeaderState> leaderStateProvider, ObjectProvider<FollowerState> followerStateProvider,
                    ObjectProvider<CandidateState> candidateStateProvider, MetaData mateData, RaftConfig raftConfig) {
        this.leaderStateProvider = leaderStateProvider;
        this.followerStateProvider = followerStateProvider;
        this.candidateStateProvider = candidateStateProvider;
        this.metaData = mateData;
        // 刚启动时是follower
        this.state = followerStateProvider.getObject();
        this.state.onEnter();
        this.raftConfig = raftConfig;
    }

    /**
     * 转换为 Candidate
     */
    private void switchToCandidate() {
        transitionTo(candidateStateProvider.getObject());
    }

    /**
     * 转化为leader
     */
    private void switchToLeader() {
        transitionTo(leaderStateProvider.getObject());
    }

    /**
     * 转化为follower
     */
    private void switchToFollower() {
        transitionTo(followerStateProvider.getObject());
    }

    private void transitionTo(NodeState newState) {
        if(state != null) {
            state.onExit();
        }
        state = newState;
        state.onEnter();
    }

    public AppendEntriesResponse handleAppendEntries(AppendEntriesRequest request) {
        return this.state.handleAppendEntries(request);
    }

    public RequestVoteResponse handleRequestVote(RequestVoteRequest request) throws IOException, ClassNotFoundException {
        return this.state.handleRequestVote(request);
    }

    /**
     * 处理客户端的请求
     *
     * @param command
     */
    public ClientResponse handleCommand(String command) {
        log.info("处理客户端请求");
        return this.state.handleCommand(command);
    }

    /**
     * 处理状态转换事件
     *
     * @param event
     */
    @EventListener
    private void handleTransition(StateTransitionEvent event) {
        log.info("节点状态转换");
        switch(event.nodeType()) {
            case NodeType.LEADER:
                this.switchToLeader();
                break;
            case NodeType.FOLLOWER:
                this.switchToFollower();
                break;
            case NodeType.CANDIDATE:
                this.switchToCandidate();
                break;
        }
    }

    @EventListener
    public void persistent(ContextClosedEvent event){
        log.info("持久化数据");

    }
}
