package org.example.raftserver.raft.log;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;

/**
 * 存储日志的容器
 */
@Log4j2
@Getter
public class MyLogList {
    private final List<LogEntity> logs;
    // 记录已提交（达成共识）到了第几个
    private int commitIndex;
    // 已应用到状态机的索引
    private int appliedIndex;

    public MyLogList(int size) {
        this.logs = new ArrayList<>();
        logs.add(new LogEntity(0, 0, ""));
        this.commitIndex = 0;
        this.appliedIndex = 0;
    }

    /**
     * 获取最后一条日志的索引
     *
     * @return
     */
    public int getLastLogIndex() {
        return logs.size() - 1;
    }

    /**
     * 获取最后一条日志的term
     *
     * @return
     */
    public int getLastLogTerm() {
        return logs.get(getLastLogIndex()).getTerm();
    }

    /**
     * 添加日志
     */
    public void append(LogEntity logEntity) {
        logs.add(logEntity);
    }

    /**
     * 批量添加日志
     *
     * @param entities
     */
    public void appendAll(List<LogEntity> entities) {
        logs.addAll(entities);
    }

    /**
     * 截断日志：从 index 开始删除所有后续日志
     */
    public void truncateFrom(int index) {
        if(index <= 0) {
            // 保留 dummy entry (index=0)
            logs.subList(1, logs.size()).clear();
        }else if(index < logs.size()) {
            logs.subList(index, logs.size()).clear();
            log.warn("日志已从 index={} 截断，当前最后索引为 {}", index, getLastLogIndex());

            // 安全性检查
            if(commitIndex > getLastLogIndex()) {
                log.warn("commitIndex {} 超出范围，重置为 {}", commitIndex, getLastLogIndex());
                commitIndex = getLastLogIndex();
            }
            if(appliedIndex > getLastLogIndex()) {
                log.warn("appliedIndex {} 超出范围，重置为 {}", appliedIndex, getLastLogIndex());
                appliedIndex = getLastLogIndex();
            }
        }
    }

    /**
     * 提交到指定索引
     */
    public void commitTo(int newCommitIndex) {
        if(newCommitIndex > commitIndex && newCommitIndex <= getLastLogIndex()) {
            log.info("提交日志从 {} 到 {}", commitIndex, newCommitIndex);
            commitIndex = newCommitIndex;
        }
    }

    /**
     * 根据索引获取日志条目
     *
     * @param index 日志索引
     * @return 日志条目，如果索引无效则返回null
     */
    public LogEntity getLog(int index) {
        if(index < 0 || index >= logs.size()) {
            return null;
        }
        return logs.get(index);
    }
}
