package com.example.buildmyschema.repository;

import com.example.buildmyschema.entity.users.UserEntity;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

//@Repository
//public interface UserRepository extends JpaRepository<UserEntity, Long> {
//    Optional<UserEntity> findByUsername(String username);
//    boolean existsByUsername(String username);
//}

@Repository
public interface UserRepository extends MongoRepository<UserEntity, ObjectId> {
    Optional<UserEntity> findByUsername(String username);
    boolean existsByUsername(String username);
}
