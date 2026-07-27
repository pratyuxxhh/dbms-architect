package com.example.buildmyschema;

import com.example.buildmyschema.entity.users.RegisterDTO;
import com.example.buildmyschema.entity.users.UserEntity;
import com.example.buildmyschema.repository.UserRepository;
import com.example.buildmyschema.service.UserServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServices userService;

    private RegisterDTO dto;

    @BeforeEach
    void setUp() {
        dto = new RegisterDTO();
        dto.setFirstName("John");
        dto.setLastName("Doe");
        dto.setUsername("johndoe");
        dto.setPassword("password123");
        dto.setConfirmPassword("password123");
    }

    @Test
    void shouldRegisterUserSuccessfully() {

        when(userRepository.existsByUsername("johndoe")).thenReturn(false);

        ResponseEntity<String> response = userService.registerNewUser(dto);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("User registered successfully", response.getBody());

        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    void shouldReturnBadRequestWhenFirstNameIsEmpty() {

        dto.setFirstName(" ");

        ResponseEntity<String> response = userService.registerNewUser(dto);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("First Name is empty", response.getBody());

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldReturnBadRequestWhenUsernameAlreadyExists() {

        when(userRepository.existsByUsername("johndoe")).thenReturn(true);

        ResponseEntity<String> response = userService.registerNewUser(dto);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Username already exists", response.getBody());

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldReturnBadRequestWhenPasswordIsEmpty() {

        dto.setPassword("");
        dto.setConfirmPassword("");

        ResponseEntity<String> response = userService.registerNewUser(dto);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Password is empty", response.getBody());

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldReturnBadRequestWhenPasswordsDoNotMatch() {

        dto.setConfirmPassword("differentPassword");

        ResponseEntity<String> response = userService.registerNewUser(dto);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Passwords do not match", response.getBody());

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldReturnInternalServerErrorWhenSaveFails() {

        when(userRepository.existsByUsername("johndoe")).thenReturn(false);
        doThrow(new RuntimeException("Database error"))
                .when(userRepository)
                .save(any(UserEntity.class));

        ResponseEntity<String> response = userService.registerNewUser(dto);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Database error", response.getBody());
    }
}