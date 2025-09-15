package org.example.raftserver.raft.rpc;

import lombok.Getter;

import java.io.Serializable;

/**
 * 返回给客户端的响应
 */
public record ClientResponse(boolean isSuccess, String message) implements Serializable {
    public static ClientResponse success(String message) {
        return new ClientResponse(true, message);
    }

    public static ClientResponse fail(String message) {
        return new ClientResponse(false, message);
    }
}
