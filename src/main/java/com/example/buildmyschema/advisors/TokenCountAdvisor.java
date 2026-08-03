package com.example.buildmyschema.advisors;

import com.example.buildmyschema.entity.users.UserEntity;
import com.example.buildmyschema.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TokenCountAdvisor implements CallAdvisor {
    @Autowired
    private UserRepository userRepository;

    public TokenCountAdvisor(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public @NonNull ChatClientResponse adviseCall(@NonNull ChatClientRequest chatClientRequest, @NonNull CallAdvisorChain callAdvisorChain) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.debug("adviseCall");
        ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);
        assert chatClientResponse.chatResponse() != null;
        int input=  chatClientResponse.chatResponse().getMetadata().getUsage().getPromptTokens();
        int output=  chatClientResponse.chatResponse().getMetadata().getUsage().getCompletionTokens();
        log.info("tokens : {}" , chatClientResponse.chatResponse().getMetadata().getUsage());
        assert authentication != null;
        String username = authentication.getName();
        UserEntity user = userRepository.findByUsername(username).orElse(null);
        if (user != null) {
            user.setInputTokens(user.getInputTokens()+input);
            user.setOutputTokens(user.getOutputTokens()+output);
            user.setTotalTokenUsed(user.getTotalTokenUsed()+(input+output));
            userRepository.save(user);
        }
        return chatClientResponse;
    }

    @Override
    public @NonNull String getName() {
        return this.getClass().getName();
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
