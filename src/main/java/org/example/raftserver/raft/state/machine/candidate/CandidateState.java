package org.example.raftserver.raft.state.machine.candidate;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.example.raftserver.raft.MetaData;
import org.example.raftserver.raft.enums.AppendEntriesResponseType;
import org.example.raftserver.raft.enums.NodeType;
import org.example.raftserver.raft.rpc.*;
import org.example.raftserver.raft.state.event.StateTransitionEvent;
import org.example.raftserver.raft.state.machine.NodeState;
import org.example.raftserver.raft.util.event.EventManager;
import org.springframework.stereotype.Component;

/**
 * Candidate状态实现
 */
@Getter
@Log4j2
@Component
@RequiredArgsConstructor
public class CandidateState implements NodeState {
    private final EventManager eventManager;
    // 选举服务
    private final ElectionService electionService;
    private final MetaData metaData;

    /**
     * candidate收到AppendEntries请求时退出选举转化为follower再处理心跳
     *
     * @param request
     * @return
     */
    @Override
    public AppendEntriesResponse handleAppendEntries(AppendEntriesRequest request) {
        eventManager.publish(new StateTransitionEvent(NodeType.FOLLOWER));
        // 回退一个，此条重发
        return new AppendEntriesResponse(metaData.getCurrentTerm(), AppendEntriesResponseType.LOG_MISMATCH, metaData.getLogs().getLastLogIndex());
    }

    /**
     * candidate收到其他节点的请求投票时，直接返回false
     *
     * @param request
     * @return
     */
    @Override
    public RequestVoteResponse handleRequestVote(RequestVoteRequest request) {
        return new RequestVoteResponse(false);
    }

    @Override
    public ClientResponse handleCommand(String command) {
        log.info("Candidate处理客户端请求");
        return ClientResponse.fail("当前没有leader");
    }

    @Override
    public void onEnter() {
        log.info("转为Candidate");
        electionService.startElection();
    }

    @Override
    public void onExit() {
        log.info("退出Candidate");
        electionService.stopElection();
    }
}
