package com.example.buildmyschema.service;

import com.example.buildmyschema.entity.schema.ResponseEntityy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;

@Slf4j
@Service
public class AiService {
    @Autowired
    private ChatClient chatClient;
    @Autowired
    private SchemaGenerator generator;
    @Autowired
    private VectorService vectorService;

    @Autowired
    private JsonService jsonService;
    public String aiHealthCheck(String m  , String id ){
        return chatClient.prompt()
                .advisors(a->a.param(ChatMemory.CONVERSATION_ID , id))
                .user(m)
                .call()
                .content();

    }

    public ResponseEntityy generateJsonWithAI(String m  , String dialect, String id){
        return jsonService.createSchema(m,dialect,id);
    }

    public File getFile(String m , String dialect, String id) throws JsonProcessingException {
        if (!vectorService.isRelevantPrompt(m)) {
            log.info("Prompt not relevant");
            return null;
        }
        log.info(m);
        log.info("Prompt is relevant");

        ResponseEntityy r = generateJsonWithAI(m ,dialect, id);
        ObjectMapper mapper = new ObjectMapper();
        String minifiedJson = mapper.writeValueAsString(r);
        System.out.println(minifiedJson);
        try {
            return switch (dialect.toUpperCase()) {
                case "POSTGRESQL" -> generator.createForPostgress(minifiedJson);
                case "MYSQL" -> generator.createForSql(minifiedJson);
                case "ORACLE_DATABASE" -> generator.createForOracle(minifiedJson);
                case "MICROSOFT_SQL" -> generator.createForMicrosoft(minifiedJson);
                default -> null;
            };

        } catch (IllegalArgumentException e) {
            System.err.println("Invalid arguments: " + e.getMessage());
            throw new RuntimeException("Invalid arguments");

        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            throw new RuntimeException("Unexpected error");
        }
    }

}

