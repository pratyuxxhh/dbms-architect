package com.example.buildmyschema.service;

import com.example.buildmyschema.entity.users.HistoryDTO;
import com.example.buildmyschema.entity.users.LoginDTO;
import com.example.buildmyschema.entity.users.RegisterDTO;
import com.example.buildmyschema.entity.users.UserEntity;
import com.example.buildmyschema.repository.UserRepository;
import com.example.buildmyschema.utils.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class UserServices {
    @Autowired
    private JwtUtils jwtUtil;
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private UserDetailServiceImpl userDetailsService;
    @Autowired
    private UserRepository userRepository;
    private static final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    public ResponseEntity<String> registerNewUser(RegisterDTO req) {
        if (req.getFirstName().trim().isEmpty()){
            log.info("First name is empty");
            return new ResponseEntity<>("First Name is empty", HttpStatus.BAD_REQUEST);

        }
        if(userRepository.existsByUsername(req.getUsername())){
            log.info("Username already exists");
            return new ResponseEntity<>("Username already exists", HttpStatus.BAD_REQUEST);
        }
        if(req.getConfirmPassword().trim().isEmpty()||req.getPassword().trim().isEmpty()){
            log.info("Password is empty");
            return new ResponseEntity<>("Password is empty", HttpStatus.BAD_REQUEST);
        }
        if (!req.getConfirmPassword().equals(req.getPassword())){
            log.info("Confirm password does not match");
            return new ResponseEntity<>("Passwords do not match", HttpStatus.BAD_REQUEST);
        }
        UserEntity user =  new UserEntity();

        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setFirstName(req.getFirstName());
        user.setLastName(req.getLastName());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(user.getUpdatedAt()==null?LocalDateTime.now():user.getUpdatedAt());
        try {
            userRepository.save(user);
            log.info("User created : {}", user.getUsername());
            return new ResponseEntity<>("User registered successfully", HttpStatus.OK);
        }catch (
                Exception e
        ){
            log.error(e.getMessage());
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

    }

    public ResponseEntity<String> loginUser(LoginDTO req) {
        if(req.getUsername().trim().isEmpty()){
            log.info("Username is empty");
            return new ResponseEntity<>("Username is empty", HttpStatus.BAD_REQUEST);
        }
        if(!userRepository.existsByUsername(req.getUsername())){
            log.info("Username does not exists");
            return new ResponseEntity<>("Username does not exists", HttpStatus.BAD_REQUEST);
        }
        try{
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
            UserDetails userDetails = userDetailsService.loadUserByUsername(req.getUsername());
            String jwt = jwtUtil.generateToken(userDetails.getUsername());
            log.info("JWT: {}", jwt);
            return new ResponseEntity<>(jwt, HttpStatus.OK);
        } catch (UsernameNotFoundException e) {
            log.error("Username not found");
            throw new RuntimeException("No such User Exists");
        }
        catch (RuntimeException e){
            log.error("Username has expired");
            throw new RuntimeException("Username has expired");
        }
        catch (Exception e){
            log.error("Exception occurred while createAuthenticationToken ", e);
            return new ResponseEntity<>("Incorrect username or password", HttpStatus.BAD_REQUEST);
        }

    }

    public String saveHistory(HistoryDTO map, String username) {
        try{
            UserEntity user = userRepository.findByUsername(username).orElse(null);
            assert user != null;
            if(user.getHistory()==null){
                user.setHistory(new ArrayList<>());
            }

            user.setUpdatedAt(LocalDateTime.now());
            user.getHistory().add(Map.of(map.getKey(), map.getValue()));
            userRepository.save(user);
            log.info("history saved : {} : {}",map.getKey(),map.getValue());
            return "history saved successfully";
        }catch (Exception e){
            throw new RuntimeException("Exception occurred while save history ", e);
        }
    }

    public List<Map<String, String>> getHistory(String username) {
        UserEntity user = userRepository.findByUsername(username).orElse(null);
        assert user != null;
        return user.getHistory();
    }

    public UserEntity getUser(String username) {
        UserEntity user = userRepository.findByUsername(username).orElse(null);

        return user;
    }
}
