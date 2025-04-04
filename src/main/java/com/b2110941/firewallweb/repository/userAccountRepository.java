package com.b2110941.firewallweb.repository;

import com.b2110941.firewallweb.model.UserAccount;
import java.util.Optional;

import org.springframework.data.mongodb.repository.DeleteQuery;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface userAccountRepository extends MongoRepository<UserAccount, String>{
    Optional<UserAccount> findByUsername(String username);

    @Transactional
    @DeleteQuery("{ 'username' : ?0 }")
    long deleteByUsername(String username);
    
    boolean existsByUsername(String username);
}
