package org.example.raftserver.raft.state.event;


import org.example.raftserver.raft.enums.NodeType;

public record StateTransitionEvent(NodeType nodeType) implements Event { }
