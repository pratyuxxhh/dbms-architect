package com.example.buildmyschema.entity.users;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterDTO {
    private String firstName;
    private String lastName;
    private String username;
    private String password;
    private String confirmPassword;
}
