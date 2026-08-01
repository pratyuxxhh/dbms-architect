package com.example.buildmyschema.entity.users;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "user_data")
public class UserEntity {
    @Id
    private ObjectId id;

    @Indexed(unique = true)
    private String username;
    private String password;
    private String firstName;
    private String lastName;
    private long totalTokenUsed;
    private long inputTokens;
    private long outputTokens;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;    // give account update options to user
    private List<String> userPrompts;
    private String role = "USER";
}
