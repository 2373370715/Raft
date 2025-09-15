package org.example.raftserver.raft.rpc;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.example.raftserver.raft.conf.RaftConfig;
import org.example.raftserver.raft.enums.RequestType;
import org.springframework.stereotype.Component;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * RPC客户端
 */
@Log4j2
@Component
@AllArgsConstructor
public class RpcClient {
    private final RaftConfig raftConfig;

    /**
     * 发送AppendEntries请求到指定节点
     *
     * @param targetAddress 目标节点地址
     * @param request       AppendEntries请求
     * @return AppendEntries响应
     */
    public AppendEntriesResponse sendAppendEntries(Address targetAddress, AppendEntriesRequest request) throws IOException, ClassNotFoundException {
        log.debug("向 {} 发送心跳", targetAddress.nodeId());
        AppendEntriesResponse response = (AppendEntriesResponse) sendRequest(targetAddress, RequestType.APPEND_ENTRIES.name(), request);
        log.debug("{} 响应心跳", targetAddress.nodeId());
        return response;
    }

    /**
     * 发送RequestVote请求到指定节点
     *
     * @param targetAddress 目标节点地址
     * @param request       RequestVote请求
     * @return RequestVote响应
     */
    public RequestVoteResponse sendRequestVote(Address targetAddress, RequestVoteRequest request) {
        try {
            log.debug("向 {} 发送投票请求", targetAddress.nodeId());
            RequestVoteResponse requestVoteResponse = (RequestVoteResponse) sendRequest(targetAddress, RequestType.REQUEST_VOTE.name(), request);
            log.debug("{} 响应投票请求", targetAddress.nodeId());
            return requestVoteResponse;
        }catch(Exception e) {
            log.error("向 {} 请求投票失败: {}", targetAddress.nodeId(), e.getMessage());
            return new RequestVoteResponse(false);
        }
    }

    /**
     * 发送请求到指定节点
     *
     * @param targetAddress 目标节点地址
     * @param requestType   请求类型
     * @param request       请求对象
     * @return 响应对象
     * @throws IOException            IO异常
     * @throws ClassNotFoundException 类未找到异常
     */
    private Object sendRequest(Address targetAddress, String requestType, Object request) throws IOException, ClassNotFoundException {
        Socket socket = new Socket(targetAddress.host(), targetAddress.port());
        ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
        ObjectInputStream input = new ObjectInputStream(socket.getInputStream());

        try(socket; output; input) {
            socket.setSoTimeout(raftConfig.getHeartbeatTimeout());

            // 发送请求类型
            output.writeUTF(requestType);
            // 发送请求对象
            //            byte[] serialize = JacksonSerializer.serialize(request);
            //            output.writeObject(serialize);
            output.writeObject(request);
            output.flush();

            // 接收响应
            return input.readObject();
        }
    }
}
