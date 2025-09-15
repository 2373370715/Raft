package org.example.raftserver.raft.conf;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component("RaftProperties")
@ConfigurationProperties(prefix = "raft")
@Getter
@Setter
public class RaftProperties {

    private String nodeId;
    private int port;
    private int clientPort;
    private String dataPath;

    private int electionTimeoutMin = 1500;
    private int electionTimeoutMax = 3000;
    private int heartbeatTimeout = 1000;
    private int heartbeatInterval = 100;

    private List<Peer> peers = new ArrayList<>();

    // 对应配置中的 peer
    @Getter
    @Setter
    public static class Peer {
        private String nodeId;
        private String ip;
        private int port;
    }
}
