package org.example.raftserver.raft.rpc;

import org.example.raftserver.raft.log.LogEntity;


import java.io.Serializable;
import java.util.List;

/**
 * 追加条目请求
 */
public record AppendEntriesRequest(int term, String leaderId, int prevLogIndex, int prevLogTerm, List<LogEntity> logEntityList, int leaderCommit) implements Serializable {
    /**
     * @param term         任期
     * @param leaderId     leader标识
     * @param prevLogIndex 已发送的最新日志索引
     * @param prevLogTerm  已同步的最新日志的任期
     * @param logEntityList      待存储的日志数组
     * @param leaderCommit leader当前commitIndex的值
     */
    public AppendEntriesRequest {
    }
}
