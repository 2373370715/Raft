package org.example.raftserver.raft.Exception;

public class ServiceStartFail extends RuntimeException {
  public ServiceStartFail(String message) {
    super(message);
  }

  public ServiceStartFail(String message, Throwable cause) {
    super(message, cause);
  }
}
