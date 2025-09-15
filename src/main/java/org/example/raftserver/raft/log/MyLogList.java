package org.example.raftserver.raft.log;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Getter
public class MyLogList {
    @Getter
    private LogEntity[] list;
    // 记录最新的索引
    private int index;
    // 记录已提交（达成共识）到了第几个
    private int commitIndex;
    // 已应用到状态机的索引
    private int appliedIndex;
    // 偏移量
    private int offset;

    public MyLogList(int size) {
        this.list = new LogEntity[size];
        list[0] = new LogEntity(0, 0, "");
        index = 0;
        this.commitIndex = 0;
        this.appliedIndex = 0;
        this.offset = 0;
        list[0].applyLog();
    }

    /**
     * 顺序添加日志，添加在哪个位置由logEntity传入
     *
     * @param logEntity 日志
     */
    public void add(LogEntity logEntity) {
        log.info("添加日志");
        int index = logEntity.getIndex() - offset;
        // 扩容
        while(index > list.length) {
            LogEntity[] newList = new LogEntity[index * 2];
            System.arraycopy(list, 0, newList, 0, list.length);
            this.list = newList;
        }
        list[index] = logEntity;
        log.info("当前日志如下：=====");
        for(int i = 0; i <= index; i++) {
            log.info(list[i]);
        }
        this.index = index;
    }

    public LogEntity getLog(int index) {
        return list[index - offset];
    }

    public LogEntity getLast() {
        return list[index];
    }

    /**
     * 提交日志
     *
     * @param index 要提交的日志索引
     */
    public void commitLog(int index) {
        if(this.list[index] == null) {
            return;
        }
        this.list[index].commitLog();
        this.commitIndex = index;
        // 一些逻辑
        this.list[index].applyLog();
    }

    /**
     * 批量提交
     * @param newCommitIndex
     */
    public void commitTo(int newCommitIndex) {
        for(int i=this.commitIndex;i<=newCommitIndex;i++){
            this.commitLog(i);
        }
    }

    /**
     * 截断日志，从i开始删除所有后续日志
     * @param index 截断开始位置
     */
    public void truncateFrom(int index) {
        if (index <= 0) {
            // index <= 0 是非法的，或者表示清空所有日志（除 dummy 外）
            // 但我们至少保留 index=0 的 dummy entry
            logEntries.subList(1, logEntries.size()).clear();
            return;
        }

        if (index >= logEntries.size()) {
            // 请求删除的位置超出当前日志长度，无需操作
            return;
        }

        // 检查 index 是否存在
        if (index < logEntries.size()) {
            // 保留 [0, index) 的日志，删除 [index, end]
            logEntries.subList(index, logEntries.size()).clear();
            log.info("日志已从 index={} 开始截断，当前最后日志索引为 {}", index, getLastLogIndex());

            // ✅ 安全性检查：commitIndex 和 lastApplied 不能大于新 lastLogIndex
            int newLastIndex = getLastLogIndex();
            if (commitIndex > newLastIndex) {
                log.warn("commitIndex {} 大于新最后索引 {}，已重置", commitIndex, newLastIndex);
                commitIndex = newLastIndex;
            }
            if (lastApplied > newLastIndex) {
                log.warn("lastApplied {} 大于新最后索引 {}，已重置", lastApplied, newLastIndex);
                lastApplied = newLastIndex;
            }
        }
    }
}
