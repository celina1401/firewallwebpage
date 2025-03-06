package com.b2110941.firewallweb.repository;

import com.b2110941.firewallweb.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface userRepository extends MongoRepository<User, String> {
    // Tìm user bằng username
    User findByUsername(String username);

    // Tìm user bằng email
    User findByEmail(String email);
}