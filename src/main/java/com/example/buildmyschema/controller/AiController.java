package com.example.buildmyschema.controller;

import com.example.buildmyschema.service.AiService;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;

import java.io.File;

@Slf4j
@RestController
@RequestMapping("/ai")
public class AiController {
    @Autowired
    private AiService aiService;

    @GetMapping("/openai")
    public ResponseEntity<String> testAi(@RequestParam String m) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assert authentication != null;
        return new ResponseEntity<>(aiService.testAi(m, authentication.getName()), HttpStatus.OK);
    }

    @GetMapping("/download")
    public ResponseEntity<?> getTheSchemaFile(@RequestParam String m) throws JsonProcessingException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assert authentication != null;
        File file = null;
        file = aiService.generateDownloadableSchemaFile(m,authentication.getName());

        if (file == null) {
            return ResponseEntity.badRequest()
                    .body("The request is invalid. No SQL schema could be generated.");
        }

        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(file);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.getName() + "\"")
                .contentType(MediaType.parseMediaType("application/sql"))
                .contentLength(file.length())
                .body(resource);
    }

}


