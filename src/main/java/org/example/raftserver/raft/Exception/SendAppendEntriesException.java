package org.example.raftserver.raft.Exception;


import org.example.raftserver.raft.rpc.Address;

public class SendAppendEntriesException extends RuntimeException {
    private final Address targetAddress;

    public SendAppendEntriesException(String message, Address targetAddress, Throwable cause) {
        super(message, cause);
        this.targetAddress = targetAddress;
    }

    public Address getTargetAddress() {
        return targetAddress;
    }
}
