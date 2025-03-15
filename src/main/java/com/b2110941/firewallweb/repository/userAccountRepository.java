package com.b2110941.firewallweb.repository;

import com.b2110941.firewallweb.model.UserAccount;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface userAccountRepository extends MongoRepository<UserAccount, String>{
    Optional<UserAccount> findByUsername(String username);
}
