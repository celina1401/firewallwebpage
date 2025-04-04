package com.b2110941.firewallweb.repository;

import com.b2110941.firewallweb.model.User;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface userRepository extends MongoRepository<User, String> {

    // Tìm user bằng username
    Optional<User> findByUsername(String username);

    // Tìm user bằng email
    Optional<User> findByEmail(String email);
    
    // Xóa user bằng username
    @Transactional
    @Query(value = "{'username': ?0}", delete = true)
    void deleteByUsername(String username);
    
    // Kiểm tra user có tồn tại không
    boolean existsByUsername(String username);
}
