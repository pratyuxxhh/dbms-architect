package com.example.buildmyschema.controller;

import com.example.buildmyschema.entity.users.HistoryDTO;
import com.example.buildmyschema.entity.users.LoginDTO;
import com.example.buildmyschema.entity.users.RegisterDTO;
import com.example.buildmyschema.service.UserServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {
    /*
    // after proper authentication
    get tokens, ------------------------> current problem to solve
    get prompts and results (string) ,
     */
    @Autowired
    private UserServices service;
    @GetMapping("/home")
    public ResponseEntity<String> securedPage(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assert authentication != null;
        String username = authentication.getName();
        return new ResponseEntity<>("you logged in successfully , hie "+ username, HttpStatus.OK);
    }

    @GetMapping("/getName")
    public ResponseEntity<String> getUserName(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assert authentication != null;
        String username = authentication.getName();
        return new ResponseEntity<>(username, HttpStatus.OK);
    }
    @PostMapping("/save-history")
    public ResponseEntity<String> saveHistory(@RequestBody HistoryDTO map){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assert authentication != null;
        String username = authentication.getName();
        return new ResponseEntity<>(service.saveHistory(map ,username), HttpStatus.OK);

    }

    @GetMapping("/get-history")
    public ResponseEntity<List<Map<String,String>>> getHistory(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assert authentication != null;
        String username = authentication.getName();
        return new ResponseEntity<>(service.getHistory(username), HttpStatus.OK);

    }

}
