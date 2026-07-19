package com.example.buildmyschema.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AiService {
    @Autowired
    private ChatClient chatClient;

    public void testAi(){
        String res = chatClient.prompt()
                .user("hie there")
                .call()
                .content();
        System.out.println(res);
    }
}
