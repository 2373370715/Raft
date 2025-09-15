package org.example.raftserver.raft.util.event;

public class GetTermQueryEvent {
    private final TermCallback callback;

    public GetTermQueryEvent(TermCallback callback) {
        this.callback = callback;
    }

    public void reply(int term) {
        callback.onSuccess(term);
    }

    @FunctionalInterface
    public interface TermCallback {
        void onSuccess(int term);
    }
}
