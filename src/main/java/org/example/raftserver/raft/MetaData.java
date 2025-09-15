package org.example.raftserver.raft;

import lombok.Getter;
import lombok.Setter;
import org.example.raftserver.raft.conf.RaftConfig;
import org.example.raftserver.raft.log.LogEntity;
import org.example.raftserver.raft.log.MyLogList;
import org.example.raftserver.raft.rpc.Address;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.util.Tuple;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class MetaData {
    // 当前任期号
    @Setter
    @Getter
    private int currentTerm;
    // 日志列表
    @Getter
    private final MyLogList logs;
    private Tuple<Integer, String> votedFor;
    private Tuple<Integer, Address> currentLeader;
    // 记录下一个发送给节点的日志索引
    private final Map<String, Integer> nextSend = new HashMap<>();
    // 保存已经同步到每个节点的日志索引，用于判断多数派已复制
    private final Map<String, Integer> alreadyCopy = new HashMap<>();
    private final RaftConfig raftConfig;

    public MetaData(RaftConfig config) {
        this.currentTerm = 1;
        this.logs = new MyLogList(1000);
        votedFor = new Tuple<>(0, "0");
        currentLeader = new Tuple<>(0, new Address("0", "0", 0));
        this.raftConfig = config;
        leaderInit();
    }

    /**
     * 重置leader的元数据，在转化为leader时调用
     */
    public void leaderInit() {
        for(var peer : raftConfig.getPeers()) {
            nextSend.put(peer, 1);
            alreadyCopy.put(peer, 0);
        }
    }

    public Integer getNextSend(String nodeId) {
        return nextSend.get(nodeId);
    }

    public Integer getAlreadyCopy(String nodeId) {
        return alreadyCopy.get(nodeId);
    }

    public void increaseNextCommit(String nodeId) {
        nextSend.put(nodeId, nextSend.get(nodeId) + 1);
    }

    public void reduceNextCommit(String nodeId) {
        nextSend.put(nodeId, Math.max(nextSend.get(nodeId) - 1, 1));
    }

    public void increaseAlreadyCommit(String nodeId) {
        alreadyCopy.put(nodeId, alreadyCopy.get(nodeId) + 1);
    }

    /**
     * 获取日志
     * @param index 日志索引
     */
    public LogEntity getLog(int index){
        return logs.getLog(index);
    }

    /**
     * 更新当前周期投票情况
     *
     * @param nodeId
     */
    public void updateVoteFor(String nodeId) {
        votedFor = new Tuple<>(currentTerm, nodeId);
    }

    /**
     * 获取当前周期投票给了谁
     */
    public Optional<String> getVoteFor() {
        if(votedFor._1() == currentTerm) {
            return Optional.ofNullable(votedFor._2());
        }
        return Optional.empty();
    }

    public Optional<Address> getCurrentLeader() {
        if(currentLeader._1() == currentTerm) {
            return Optional.ofNullable(currentLeader._2());
        }
        return Optional.empty();
    }

    public void updateCurrentLeader(String nodeId, int port) {
        currentLeader = new Tuple<>(currentTerm, new Address(nodeId, nodeId, port));
    }
}
