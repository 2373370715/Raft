package org.example.raftserver.raft.log;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

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

    public LogEntity(int term, int index, String command) {
        this.term = term;
        this.index = index;
        this.command = command;
    }

    public String toString() {
        return "{term=" + term + ", index=" + index + ", command=" + command + "}";
    }
}
