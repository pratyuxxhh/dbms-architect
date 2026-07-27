package com.example.buildmyschema.controller;

import com.example.buildmyschema.entity.users.LoginDTO;
import com.example.buildmyschema.entity.users.RegisterDTO;
import com.example.buildmyschema.service.UserDetailServiceImpl;
import com.example.buildmyschema.service.UserServices;
import com.example.buildmyschema.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import static reactor.netty.http.HttpConnectionLiveness.log;

@RestController
@RequestMapping("/public")
public class PublicController {

    @Autowired
    private UserServices userService;

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody LoginDTO user) {
        return userService.loginUser(user);
    }
    @PostMapping("/register")
    public ResponseEntity<String> registerUser(@RequestBody RegisterDTO req){
        return userService.registerNewUser(req);
    }
    @GetMapping("/test")
    public ResponseEntity<String> securedPage(){
        return new ResponseEntity<>("hie this is a public page", HttpStatus.OK);
    }
}
