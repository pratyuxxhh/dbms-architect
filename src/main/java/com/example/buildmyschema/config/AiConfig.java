package com.example.buildmyschema.config;

import com.example.buildmyschema.advisors.TokenCountAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {
    @Autowired
    private TokenCountAdvisor tokenCountAdvisor;

    @Bean
    public ChatClient chatClient(OpenAiChatModel openAiChatModel) {
        ChatMemory chatMemory = MessageWindowChatMemory.builder().build();
        return ChatClient.builder(openAiChatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultAdvisors(tokenCountAdvisor).build();
                // will store in memory data , once app os restart everything is gone
    }
}
