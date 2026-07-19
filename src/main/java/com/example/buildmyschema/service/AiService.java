package com.example.buildmyschema.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AiService {
    @Autowired
    private ChatClient chatClient;

    public String testAi(String m ){
        return chatClient.prompt()
                .user(m)
                .call()
                .content();

    }
}
