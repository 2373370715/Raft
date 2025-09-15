package org.example.raftserver.raft.rpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import lombok.extern.log4j.Log4j2;
import org.example.raftserver.raft.Exception.ServiceStartFail;
import org.example.raftserver.raft.MetaData;
import org.example.raftserver.raft.RaftNode;
import org.example.raftserver.raft.conf.RaftConfig;
import org.example.raftserver.raft.enums.RequestType;
import org.example.raftserver.raft.log.LogEntity;
import org.example.raftserver.raft.log.MyLogList;
import org.example.raftserver.raft.util.JacksonSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;


import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

/**
 * RPC服务端
 */
@Log4j2
@Component
public class RpcServer {
    private final RaftNode raftNode;
    private final RaftConfig raftConfig;
    @Autowired
    MetaData metaData;

    public RpcServer(RaftNode raftNode, RaftConfig raftConfig) {
        this.raftNode = raftNode;
        this.raftConfig = raftConfig;
        // 启动Rpc服务器
        try {
            this.start();
        }catch(IOException e) {
            throw new ServiceStartFail("服务器启动失败: " + e);
        }
    }

    // 节点间通信服务器
    private ServerSocket nodeServerSocket;
    private volatile boolean nodeServerRunning = false;
    private Thread nodeServerThread;

    // 客户端通信服务器
    private ServerSocket clientServerSocket;
    private volatile boolean clientServerRunning = false;
    private Thread clientServerThread;

    /**
     * 启动RPC服务器（包括节点间通信和客户端通信）
     */
    public void start() throws IOException {
        startNodeServer();
        startClientServer();
    }

    /**
     * 启动节点间通信服务器
     */
    private void startNodeServer() throws IOException {
        if(nodeServerRunning) {
            return;
        }

        nodeServerSocket = new ServerSocket(raftConfig.getPort());
        nodeServerRunning = true;
        nodeServerThread = new Thread(this::listenForNodeRequests);
        nodeServerThread.setDaemon(true);
        nodeServerThread.start();

        log.info("节点间通信服务器已在端口 {} 上启动", raftConfig.getPort());
    }

    /**
     * 启动客户端通信服务器
     */
    private void startClientServer() throws IOException {
        if(clientServerRunning) {
            return;
        }

        clientServerSocket = new ServerSocket(raftConfig.getClientPort());
        clientServerRunning = true;
        clientServerThread = new Thread(this::listenForClientRequests);
        clientServerThread.setDaemon(true);
        clientServerThread.start();

        log.info("客户端通信服务器已在端口 {} 上启动", raftConfig.getClientPort());
    }

    /**
     * 停止RPC服务器
     */
    public void stop() throws IOException {
        stopNodeServer();
        stopClientServer();
    }

    /**
     * 停止节点间通信服务器
     */
    private void stopNodeServer() throws IOException {
        if(!nodeServerRunning) {
            return;
        }

        nodeServerRunning = false;
        if(nodeServerSocket != null && !nodeServerSocket.isClosed()) {
            nodeServerSocket.close();
        }

        if(nodeServerThread != null) {
            try {
                nodeServerThread.join(1000); // 等待最多1秒
            }catch(InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        log.debug("节点间通信服务器已停止");
    }

    /**
     * 停止客户端通信服务器
     */
    private void stopClientServer() throws IOException {
        if(!clientServerRunning) {
            return;
        }

        clientServerRunning = false;
        if(clientServerSocket != null && !clientServerSocket.isClosed()) {
            clientServerSocket.close();
        }

        if(clientServerThread != null) {
            try {
                clientServerThread.join(1000); // 等待最多1秒
            }catch(InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        log.debug("客户端通信服务器已停止");
    }

    /**
     * 监听并处理节点间请求
     */
    private void listenForNodeRequests() {
        while(nodeServerRunning && !nodeServerSocket.isClosed()) {
            try {
                Socket nodeSocket = nodeServerSocket.accept();
                // 为每个连接创建新线程处理
                Thread handlerThread = new Thread(() -> handleNodeRequest(nodeSocket));
                handlerThread.setDaemon(true);
                handlerThread.start();
            }catch(IOException e) {
                if(nodeServerRunning) {
                    log.error("接受节点连接时出错: {}", e.getMessage());
                }
                // 如果服务器正在运行但出现异常，可能需要处理
            }
        }
    }

    /**
     * 监听并处理客户端请求
     */
    private void listenForClientRequests() {
        while(clientServerRunning && !clientServerSocket.isClosed()) {
            try {
                Socket clientSocket = clientServerSocket.accept();
                // 为每个连接创建新线程处理
                log.info("接受到客户端连接");
                Thread handlerThread = new Thread(() -> handleClientRequest(clientSocket));
                handlerThread.setDaemon(true);
                handlerThread.start();
            }catch(IOException e) {
                if(clientServerRunning) {
                    log.error("接受客户端连接时出错: {}", e.getMessage());
                }
                // 如果服务器正在运行但出现异常，可能需要处理
            }
        }
    }

    /**
     * 处理节点间RPC请求
     */
    private void handleNodeRequest(Socket nodeSocket) {
        try(nodeSocket;
            ObjectInputStream input = new ObjectInputStream(nodeSocket.getInputStream());
            ObjectOutputStream output = new ObjectOutputStream(nodeSocket.getOutputStream())) {
            try {
                // 读取请求类型
                String requestType = input.readUTF();
                if(RequestType.APPEND_ENTRIES.name().equals(requestType)) {
                    // 处理AppendEntries请求
                    AppendEntriesRequest request = (AppendEntriesRequest) input.readObject();
                    log.debug("接收到 {} 的心跳", request.leaderId());
                    AppendEntriesResponse response = raftNode.handleAppendEntries(request);
                    log.debug("响应 {} 的心跳", request.leaderId());
                    output.writeObject(response);
                }else if(RequestType.REQUEST_VOTE.name().equals(requestType)) {
                    // 处理RequestVote请求
                    RequestVoteRequest request = (RequestVoteRequest) input.readObject();
                    log.info("接收到{}的投票请求", request.candidateId());
                    RequestVoteResponse response = raftNode.handleRequestVote(request);
                    log.debug("响应{}的投票请求", request.candidateId());
                    output.writeObject(response);
                }else {
                    // 未知请求类型
                    log.error("未知的节点请求类型: {}", requestType);
                }

            }catch(IOException | ClassNotFoundException e) {
                log.error("处理节点请求时出错", e);
            }
        }catch(IOException e) {
            log.error("关闭节点连接时出错: {}", e.getMessage());
        }
    }

    /**
     * 处理客户端请求
     */
    private void handleClientRequest(Socket clientSocket) {
        log.info("接收到客户端请求");
        try(clientSocket;
            DataInputStream input = new DataInputStream(clientSocket.getInputStream());
            DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream())) {
            try {
                // 读取请求类型
                String requestType = input.readUTF();

                if("CLIENT_COMMAND".equals(requestType)) {
                    // 处理客户端命令
                    String command = input.readUTF();
                    log.info("接收到客户端命令: {}", command);
                    ClientResponse clientResponse = raftNode.handleCommand(command);
                    byte[] serialize = JacksonSerializer.serialize(clientResponse);
                    output.writeInt(serialize.length);
                    // 返回确认响应
                    log.info("返回客户端响应: {}", clientResponse);
                    output.write(serialize);
                    log.info("返回客户端响应成功");
                }else {
                    // 未知请求类型
                    log.warn("未知的客户端请求类型: {}", requestType);
                    ClientResponse fail = ClientResponse.fail("Unknown request type: " + requestType);
                    byte[] serialize = JacksonSerializer.serialize(fail);
                    output.write(serialize);
                }

            }catch(IOException e) {
                log.error("处理客户端请求时出错: ", e);
                try {
                    ClientResponse fail = ClientResponse.fail("Error processing request: " + e.getMessage());
                    byte[] serialize = JacksonSerializer.serialize(fail);
                    output.write(serialize);
                }catch(IOException ioException) {
                    log.error("写入错误响应时出错: ", ioException);
                }
            }
        }catch(IOException e) {
            log.error("关闭客户端连接时出错: ", e);
        }
    }
}
