package org.example.raftserver.raft.util.event;// event/EventManager.java


import lombok.AllArgsConstructor;
import org.example.raftserver.raft.state.event.Event;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 通用事件管理器
 * 封装事件发布逻辑，支持任意事件类型
 */
@Component
@AllArgsConstructor
public class EventManager {

    private final ApplicationEventPublisher publisher;

    /**
     * 发布任意事件（同步）
     * 支持 ApplicationEvent 子类 或 普通 POJO（Spring 4.2+）
     */
    public void publish(Event event) {
        if (event == null) {
            return;
        }
        publisher.publishEvent(event);
    }
}