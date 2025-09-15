package org.example.raftserver.raft.state.machine;


import org.example.raftserver.raft.rpc.*;

import java.io.IOException;

/**
 * 节点状态接口
 */

public interface NodeState {

    AppendEntriesResponse handleAppendEntries(AppendEntriesRequest request);

    RequestVoteResponse handleRequestVote(RequestVoteRequest request) throws IOException, ClassNotFoundException;

    ClientResponse handleCommand(String command);

    /**
     * 状态进入时调用
     */
    void onEnter();

    /**
     * 状态退出时调用
     */
    void onExit();
}
