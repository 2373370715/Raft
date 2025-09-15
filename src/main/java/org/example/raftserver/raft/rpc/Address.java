package org.example.raftserver.raft.rpc;

public record Address(String nodeId, String host, int port) { }
