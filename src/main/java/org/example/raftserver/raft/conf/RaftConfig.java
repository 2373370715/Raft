// 文件：org.example.raftserver.raft.conf.RaftConfig.java

package org.example.raftserver.raft.conf;

import lombok.Getter;
import org.example.raftserver.raft.rpc.Address;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Raft 配置类：由 Spring 管理，通过依赖注入使用
 * 不再使用静态单例模式
 */
@Getter
@Component
public final class RaftConfig {

    private final String nodeId;
    private final int port;
    private final int clientPort;
    private final String dataPath;
    private final int electionTimeoutMin;
    private final int electionTimeoutMax;
    private final int heartbeatTimeout;
    private final int heartbeatInterval;
    private final int peerCount;
    private final Map<String, Address> peerAddress;
    private final List<String> peers;

    // 通过 RaftProperties 构造（由 Spring 注入）
    public RaftConfig(@Qualifier("RaftProperties") RaftProperties properties) {
        this.nodeId = properties.getNodeId();
        this.port = properties.getPort();
        this.clientPort = properties.getClientPort();
        this.dataPath = properties.getDataPath();
        this.electionTimeoutMin = properties.getElectionTimeoutMin();
        this.electionTimeoutMax = properties.getElectionTimeoutMax();
        this.heartbeatTimeout = properties.getHeartbeatTimeout();
        this.heartbeatInterval = properties.getHeartbeatInterval();

        // 构建 peers 和 peerAddress
        List<RaftProperties.Peer> configPeers = properties.getPeers();
        this.peerCount = configPeers.size();
        this.peerAddress = new HashMap<>();
        this.peers = new ArrayList<>();

        for (RaftProperties.Peer peer : configPeers) {
            String peerId = peer.getNodeId();
            Address address = new Address(peerId, peer.getIp(), peer.getPort());
            this.peerAddress.put(peerId, address);
            this.peers.add(peerId);
        }

        validate();
    }

    // 移除 initialize() 和 getInstance()

    // 校验配置合法性
    private void validate() {
        if (nodeId == null || nodeId.trim().isEmpty()) {
            throw new IllegalArgumentException("nodeId 不能为空");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port 必须在 1-65535 之间");
        }
        if (heartbeatInterval <= 0) {
            throw new IllegalArgumentException("heartbeatInterval 必须大于 0");
        }
    }
}