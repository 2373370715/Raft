package org.example.raftserver.raft.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.serializer.Serializer;

import java.io.IOException;

public class JacksonSerializer {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static byte[] serialize(Object object) throws JsonProcessingException {
        return mapper.writeValueAsBytes(object);
    }

    public static <T> T deserialize(byte[] bytes, Class<T> clazz) throws IOException {
        return mapper.readValue(bytes, clazz);
    }
}
