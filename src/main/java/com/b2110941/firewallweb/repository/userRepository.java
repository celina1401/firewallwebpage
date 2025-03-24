package com.b2110941.firewallweb.repository;

import com.b2110941.firewallweb.model.User;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface userRepository extends MongoRepository<User, String> {

    // Tìm user bằng username
    Optional<User> findByUsername(String username);

    // Tìm user bằng email
    Optional<User> findByEmail(String email);
}
