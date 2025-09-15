package org.example.raftserver.raft.enums;

public enum AppendEntriesResponseType {

    /**
     * 追加日志或心跳成功
     */
    SUCCESS,

    /**
     * 请求中的 term 小于当前节点的 term
     * 表示发送者已经过时，不是当前 Leader
     */
    TERM_TOO_SMALL,

    /**
     * 日志不匹配：
     * - prevLogIndex 不存在
     * - 或 prevLogIndex 存在但 term 不匹配
     */
    LOG_MISMATCH,

    /**
     * 其他错误（可选，用于扩展）
     * 比如磁盘写入失败、节点正在快照等
     */
    INTERNAL_ERROR
}
