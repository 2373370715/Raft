package org.example.raftserver.raft.enums;

public enum LogStateType {
    // 未提交
    UNCOMMITTED,
    // 已提交
    COMMITTED,
    // 已应用到状态机
    APPLIED
}
