package org.example.raftserver.raft.log;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.example.raftserver.raft.enums.LogStateType;

import java.io.Serializable;

/**
 * 日志实体</br>
 * 新任期不会重置索引
 */
@Getter
@Setter
@Slf4j
public class LogEntity implements Serializable {
    private int term;
    private int index;
    private String command;
    private LogStateType state;

    public LogEntity(int term, int index, String command) {
        this.term = term;
        this.index = index;
        this.command = command;
        this.state = LogStateType.UNCOMMITTED;
    }

    public void commitLog() {
        if(this.state != LogStateType.COMMITTED && this.state != LogStateType.APPLIED) {
            log.info("提交第 {} 条日志", index);
            this.state = LogStateType.COMMITTED;
        }else {
            log.debug("第{}条日志已提交", index);
        }
    }

    public void applyLog() {
        this.state = LogStateType.APPLIED;
    }

    public String toString() {
        return "{term=" + term + ", index=" + index + ", state=" + state + "}";
    }
}
