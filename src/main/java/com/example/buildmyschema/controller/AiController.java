package com.example.buildmyschema.controller;

import com.example.buildmyschema.entity.ResponseEntityy;
import com.example.buildmyschema.service.AiService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;

import java.io.File;

@RestController
@RequestMapping("/ai")
public class AiController {
    @Autowired
    private AiService aiService;

    @GetMapping("/openai")
    public ResponseEntity<String> testAi(@RequestParam String m) {
        return new ResponseEntity<>(aiService.testAi(m), HttpStatus.OK);
    }

    @GetMapping("/ai")
    public ResponseEntity<String> testAiWithId(@RequestParam String m,@RequestParam String id) {
        return new ResponseEntity<>(aiService.testAiwithPrivatechat(m,id), HttpStatus.OK);
    }

    //working great
    @GetMapping("/schema")
    public ResponseEntity<ResponseEntityy> getTheSchema(@RequestParam String m, @RequestParam String id) {
        return ResponseEntity.ok(aiService.testAiwithPrivatechatWithCustomOutput(m,id));
    }
    @GetMapping("/schemastring")
    public ResponseEntity<String> getTheSchemaString(@RequestParam String m, @RequestParam String id)  {
        try{
        return ResponseEntity.ok().body(aiService.jsonToStringResponse(m,id));

        }catch(JsonProcessingException e){
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> getTheSchemafile(@RequestParam String m, @RequestParam String id) throws JsonProcessingException {
        File file = aiService.generateSchemaFile(m,id);
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.getName() + "\"")
                .contentType(MediaType.parseMediaType("application/sql"))
                .contentLength(file.length())
                .body(resource);
    }

}

/*
    right now i am able to convert the json to sql DDL syntax ,

    next task is to return a zip containing
    - schema json
    - sql file
    - seed file ( find a way to generate a seed)
 */
