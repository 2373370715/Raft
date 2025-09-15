package org.example.raftserver.raft.rpc;

import org.example.raftserver.raft.enums.AppendEntriesResponseType;

import java.io.Serializable;

/**
 * 追加条目响应
 */
public record AppendEntriesResponse(int term, AppendEntriesResponseType type) implements Serializable {
}
