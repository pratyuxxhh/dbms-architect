package com.example.buildmyschema.service;

import com.example.buildmyschema.entity.schema.ResponseEntityy;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class JsonService {

    @Autowired
    private ChatClient chatClient;
    @Value("classpath:prompts/SystemPrompt.st")
    private Resource system;
    @Value("classpath:prompts/tempUserPrompt.st")
    private Resource user;

    public ResponseEntityy createSchema(String m , String dialect , String id){

        return createJson(m,dialect,id);
    }
    private ResponseEntityy createJson(String m , String dialect , String id){
        return chatClient.prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, id))
                .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .system(s->s.text(system))
                .user(u->u.text(user).params(Map.of("message",m ,"dialect",dialect)))
                .call()
                .entity(ResponseEntityy.class);
    }



}
