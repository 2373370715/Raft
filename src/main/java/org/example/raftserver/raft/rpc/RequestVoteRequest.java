package org.example.raftserver.raft.rpc;

import lombok.Getter;

import java.io.Serializable;

/**
 * 请求投票请求
 *
 * @param term 任期号
 */
public record RequestVoteRequest(int term, String candidateId, int lastLogIndex, int lastLogTerm) implements Serializable { }
