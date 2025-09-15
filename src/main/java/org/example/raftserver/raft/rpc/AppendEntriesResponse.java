package org.example.raftserver.raft.rpc;

import org.example.raftserver.raft.enums.AppendEntriesResponseType;

import java.io.Serializable;

/**
 * 追加条目响应
 * @param term follower的任期号
 * @param type 响应类型
 * @param lastLogIndex follower的日志索引
 */
public record AppendEntriesResponse(int term, AppendEntriesResponseType type, int lastLogIndex) implements Serializable {
}
