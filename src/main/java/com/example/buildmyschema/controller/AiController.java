package com.example.buildmyschema.controller;

import com.example.buildmyschema.service.AiService;
import jakarta.websocket.server.PathParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
public class AiController {
    @Autowired
    private AiService aiService;

    @GetMapping("/openai")
    public ResponseEntity<String> testAi(@RequestParam String m) {
        return new ResponseEntity<>(aiService.testAi(m), HttpStatus.OK);
    }
}
