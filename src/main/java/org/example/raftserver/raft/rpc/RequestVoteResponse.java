package org.example.raftserver.raft.rpc;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * 请求投票响应
 *
 * @param voteGranted 是否投票
 */
public record RequestVoteResponse(boolean voteGranted) implements Serializable {
}
