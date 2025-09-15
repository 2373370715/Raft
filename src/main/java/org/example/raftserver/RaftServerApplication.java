package org.example.raftserver;

import org.example.raftserver.raft.conf.RaftProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableConfigurationProperties(RaftProperties.class)
public class RaftServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RaftServerApplication.class, args);
    }

}
