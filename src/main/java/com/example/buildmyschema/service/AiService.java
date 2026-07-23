package com.example.buildmyschema.service;

import com.example.buildmyschema.entity.ResponseEntityy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

@Service
public class AiService {
    @Autowired
    private ChatClient chatClient;
    @Autowired
    private SqlGenerator generator;

    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private EmbeddingModel embeddingModel;
    @Value("classpath:prompts/tempSystemPrompt.st")
    private Resource system;
    @Value("classpath:prompts/tempUserPrompt.st")
    private Resource user;
    public String testAi(String m ){
        return chatClient.prompt()
                .user(m)
                .call()
                .content();

    }

    public ResponseEntityy testAiWithPrivatechatWithCustomOutput(String m  , String id){

        return chatClient.prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, id))
                .advisors(new SimpleLoggerAdvisor())
                .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .system(s->s.text(system))
                .user(u->u.text(user).param("message",m))
                .call()
                .entity(ResponseEntityy.class);

    }

    public File generateDownloadableSchemaFile(String m , String id) throws JsonProcessingException {
        ResponseEntityy r = testAiWithPrivatechatWithCustomOutput(m , id);

        ObjectMapper mapper = new ObjectMapper();
        String minifiedJson = mapper.writeValueAsString(r);

        System.out.println(minifiedJson);


        try {
            File result = generator.generateDownloadableFile(minifiedJson);
            System.out.println("SQL file generated successfully: " + result.getAbsolutePath());
            return result;
        } catch (SqlGenerator.InvalidSchemaException e) {
            System.err.println("Invalid schema JSON: " + e.getMessage());

        } catch (IOException e) {
            System.err.println("I/O error while generating SQL file: " + e.getMessage());

        } catch (IllegalArgumentException e) {
            System.err.println("Invalid arguments: " + e.getMessage());

        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

}

/*
today's target
custom output
rag implement
prompt customize
 */
