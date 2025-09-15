package org.example.raftserver.raft.state.machine.follower;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.example.raftserver.raft.MetaData;
import org.example.raftserver.raft.conf.RaftConfig;
import org.example.raftserver.raft.enums.AppendEntriesResponseType;
import org.example.raftserver.raft.enums.NodeType;
import org.example.raftserver.raft.log.LogEntity;
import org.example.raftserver.raft.rpc.*;
import org.example.raftserver.raft.state.event.StateTransitionEvent;
import org.example.raftserver.raft.state.machine.NodeState;
import org.example.raftserver.raft.util.event.EventManager;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Follower状态实现
 */
@Log4j2
@Getter
@Component
@RequiredArgsConstructor
public class FollowerState implements NodeState {
    private final EventManager eventManager;
    private final RaftConfig raftConfig;
    private final MetaData metaData;
    private final HeartTimeoutService heartTimeoutService;

    /**
     * 处理心跳
     *
     * @param request
     * @return
     */
    @Override
    //    public AppendEntriesResponse handleAppendEntries(AppendEntriesRequest request) {
    //        log.debug("收到来自{}的AppendEntries请求", request.leaderId());
    //        // 收到了leader的心跳
    //        Address address = raftConfig.getPeerAddress().get(request.leaderId());
    //        // 更新当前leader
    //        metaData.updateCurrentLeader(request.leaderId(), address.port());
    //        this.heartTimeoutService.resetElectionTimeout();
    //        // 1.检查任期
    //        int term = request.term();
    //        if(term < metaData.getCurrentTerm()) {
    //            // 如果leader的任期号更小，则直接拒绝
    //            log.info("leader的任期号更小，需要更新leader");
    //            return new AppendEntriesResponse(metaData.getCurrentTerm(), AppendEntriesResponseType.TERM_TOO_SMALL);
    //        }else if(term > metaData.getCurrentTerm()) {
    //            // 如果leader的任期号大于当前节点的任期号，则更新任期号
    //            log.info("当前节点的任期号为 {} 被更新为{}", metaData.getCurrentTerm(), term);
    //            metaData.setCurrentTerm(term);
    //        }
    //        // 2. 检查最新同步的日志是否匹配
    //        // 最新同步的日志
    //        int prevLogIndex = request.prevLogIndex();
    //        // 最新同步的日志的term
    //        int prevLogTerm = request.prevLogTerm();
    //        //        System.out.println("*****" + prevLogTerm + "\t" + prevLogIndex);
    //        LogEntity log1 = metaData.getLogs().getLog(prevLogIndex);
    //        // 如果不存在这条日志或日志不匹配（第0条初始日志不需要判断直接返回成功）
    //        if(prevLogIndex != 0 && (log1 == null || log1.getTerm() != prevLogTerm)) {
    //            log.warn("接收的任期为{}、索引为{}", request.prevLogTerm(), request.prevLogIndex());
    //            if(log1 != null) {
    //                log.warn("本地的任期为{}、索引为{}", log1.getTerm(), log1.getIndex());
    //            }
    //            return new AppendEntriesResponse(metaData.getCurrentTerm(), AppendEntriesResponseType.HEART_FAIL);
    //        }
    //
    //        int i = request.leaderCommit();
    //        //        System.out.println("*****" + i + "\t" + metaData.getLogs().getCommitIndex());
    //        // follower提交的日志比leader少，则更新
    //        if(i > metaData.getLogs().getCommitIndex()) {
    //            log.info("follower提交日志到{}", i);
    //            // 当前leader提交到了几
    //            int alreadyCommit = metaData.getLogs().getCommitIndex();
    //            for(int j = alreadyCommit; j <= i; j++) {
    //                LogEntity localLog = metaData.getLogs().getLog(j);
    //                if(localLog != null) {
    //                    metaData.getLogs().commitLog(j);
    //                }else{
    //                    for(int k = j+1;k<=i;k++){
    //                        if(metaData.getLogs().getLog(k) != null){
    //                            log.error("日志有空洞，运行错误");
    //                            // 退出jvm
    //                            System.exit(1);
    //                        }
    //                    }
    //                    break;
    //                }
    //            }
    //        }
    //
    //        LogEntity logEntityList = request.logEntityList();
    //        if(logEntityList == null) {
    //            // 表明是心跳
    //            return new AppendEntriesResponse(metaData.getCurrentTerm(), AppendEntriesResponseType.HEART_SUCCESS);
    //        }else {
    //            log.info("收到了leader发来的日志 {}", logEntityList);
    //            // 表明是追加
    //            metaData.getLogs().add(logEntityList);
    //            return new AppendEntriesResponse(metaData.getCurrentTerm(), AppendEntriesResponseType.APPEND_SUCCESS);
    //        }
    //    }

    public AppendEntriesResponse handleAppendEntries(AppendEntriesRequest request) {
        log.debug("收到来自 {} 的 AppendEntries 请求", request.leaderId());

        Address address = raftConfig.getPeerAddress().get(request.leaderId());
        metaData.updateCurrentLeader(request.leaderId(), address.port());
        this.heartTimeoutService.resetElectionTimeout();

        // 1. 检查任期
        int term = request.term();
        if(term < metaData.getCurrentTerm()) {
            log.info("拒绝 AppendEntries：leader 任期 {} 小于当前节点任期 {}", term, metaData.getCurrentTerm());
            return new AppendEntriesResponse(metaData.getCurrentTerm(), AppendEntriesResponseType.TERM_TOO_SMALL);
        }else if(term > metaData.getCurrentTerm()) {
            log.info("更新当前节点任期：{} -> {}", metaData.getCurrentTerm(), term);
            metaData.setCurrentTerm(term);
        }

        // 2. 日志一致性检查（prevLogIndex 和 prevLogTerm）
        int prevLogIndex = request.prevLogIndex();
        int prevLogTerm = request.prevLogTerm();

        // 特殊处理：prevLogIndex == 0 表示没有前一条日志（初始情况），直接通过
        if(prevLogIndex > 0) {
            LogEntity localLog = metaData.getLogs().getLog(prevLogIndex);
            if(localLog == null || localLog.getTerm() != prevLogTerm) {
                log.warn("日志不匹配：期望 term={}, index={}，本地 term={}, index={}",
                         prevLogTerm,
                         prevLogIndex,
                         localLog != null ? localLog.getTerm() : -1,
                         prevLogIndex);
                return new AppendEntriesResponse(metaData.getCurrentTerm(), AppendEntriesResponseType.LOG_MISMATCH);
            }
        }

        // 3. 追加新日志（如果有）
        List<LogEntity> entries = request.logEntityList(); // 假设你修改了请求结构，支持批量日志
        if(entries != null && !entries.isEmpty()) {
            log.info("追加 {} 条日志，从 index={}", entries.size(), entries.get(0).getIndex());

            // 删除冲突日志（从 prevLogIndex+1 开始覆盖）
            metaData.getLogs().truncateFrom(prevLogIndex + 1);

            // 追加新日志
            for(LogEntity entry : entries) {
                metaData.getLogs().add(entry);
            }
        }

        // 4. 更新 commitIndex（关键：只能提交到本地最后一条日志）
        int leaderCommit = request.leaderCommit();
        int lastLogIndex = metaData.getLogs().getLast().getIndex();

        if(leaderCommit > metaData.getLogs().getCommitIndex()) {
            int newCommitIndex = Math.min(leaderCommit, lastLogIndex);
            if(newCommitIndex > metaData.getLogs().getCommitIndex()) {
                log.info("Follower 提交日志从 {} 到 {}", metaData.getLogs().getCommitIndex(), newCommitIndex);
                metaData.getLogs().commitTo(newCommitIndex); // 批量提交
            }
        }

        // 5. 返回成功响应
        int currentLastLogIndex = metaData.getLogs().getLast().getIndex();
        return new AppendEntriesResponse(metaData.getCurrentTerm(), AppendEntriesResponseType.SUCCESS, currentLastLogIndex);
    }

    @Override
    public RequestVoteResponse handleRequestVote(RequestVoteRequest request) {
        int term = request.term();
        // 如果candidate的任期小于当前node的返回false
        if(term < metaData.getCurrentTerm()) {
            return new RequestVoteResponse(false);
        }
        // 如果这个term已经投过票了则返回false
        Optional<String> voteFor = metaData.getVoteFor();
        if(voteFor.isPresent()) {
            return new RequestVoteResponse(false);
        }

        int lastLogIndex = request.lastLogIndex();
        int lastLogTerm = request.lastLogTerm();
        LogEntity last = metaData.getLogs().getLast();
        // 如果candidate的日志更少，或者term更小，则拒绝投票
        if(last.getIndex() > lastLogIndex || last.getTerm() > lastLogTerm) {
            return new RequestVoteResponse(false);
        }

        // 投票
        metaData.updateVoteFor(request.candidateId());
        return new RequestVoteResponse(true);
    }

    @Override
    public ClientResponse handleCommand(String command) {
        log.info("Follower处理客户端请求");
        Optional<Address> currentLeader = metaData.getCurrentLeader();
        if(currentLeader.isPresent()) {
            Address address = currentLeader.get();
            return ClientResponse.fail("当前节点不是leader，leader节点为" + address.nodeId() + ", ip为" + address.host());
        }else {
            return ClientResponse.fail("当前没有leader");
        }
    }

    @Override
    public void onEnter() {
        log.info("转为follower");
        heartTimeoutService.startHeartTimeout();
    }

    @Override
    public void onExit() {
        log.info("退出Follower");
        heartTimeoutService.stopElectionTimeout();
    }
}
