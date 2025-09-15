package org.example.raftserver.raft.state.machine.leader;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.example.raftserver.raft.MetaData;
import org.example.raftserver.raft.conf.RaftConfig;
import org.example.raftserver.raft.log.LogEntity;
import org.example.raftserver.raft.rpc.*;
import org.example.raftserver.raft.state.machine.NodeState;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 领导者状态
 */
@Log4j2
@Component
@AllArgsConstructor
public class LeaderState implements NodeState {
    private final MetaData metaData;
    private final RpcClient rpcClient;
    private final SendHeartService heartService;
    private final RaftConfig raftConfig;

    /**
     * leader不会收到
     *
     * @param request
     * @return
     */
    @Override
    public AppendEntriesResponse handleAppendEntries(AppendEntriesRequest request) {
        return null;
    }

    /**
     * leader收到请求投票时，向对方发送心跳表明已经有leader了，然后返回false
     *
     * @param request
     * @return
     */
    @Override
    public RequestVoteResponse handleRequestVote(RequestVoteRequest request) throws IOException, ClassNotFoundException {
        String peer = request.candidateId();
        int preLogIndex = metaData.getNextSend(peer);
        System.out.println("leader收到 " + peer + " 投票请求 " + preLogIndex);
        int prevLogTerm = metaData.getLogs().getLog(preLogIndex).getTerm();
        AppendEntriesRequest heartBeatRequest = new AppendEntriesRequest(metaData.getCurrentTerm(),
                                                                         raftConfig.getNodeId(),
                                                                         preLogIndex,
                                                                         prevLogTerm,
                                                                         null,
                                                                         metaData.getLogs().getCommitIndex());

        rpcClient.sendAppendEntries(raftConfig.getPeerAddress().get(peer), heartBeatRequest);
        return new RequestVoteResponse(false);
    }

    public ClientResponse handleCommand(String command) {
        log.info("Leader处理客户端请求");
        // 添加日志
        int index = metaData.getLogs().getIndex() + 1;
        metaData.getLogs().add(new LogEntity(metaData.getCurrentTerm(), index, command));
        // TODO 日志的广播在心跳中，客户端需等待运行结果
        return ClientResponse.success("成功");
    }

    @Override
    public void onEnter() {
        log.info("转为Leader");
        metaData.leaderInit();
        heartService.startHeart();
    }

    @Override
    public void onExit() {
        log.info("退出Leader");
        heartService.stopHeart();
    }
}
