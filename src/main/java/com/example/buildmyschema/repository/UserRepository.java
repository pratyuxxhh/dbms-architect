package com.example.buildmyschema.repository;

import com.example.buildmyschema.controller.UserController;
import com.example.buildmyschema.entity.users.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findById(Long id);
    Optional<UserEntity> findByUsername(String username);
    boolean existsByUsername(String username);
}
