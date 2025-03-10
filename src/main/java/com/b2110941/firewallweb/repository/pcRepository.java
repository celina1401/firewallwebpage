package com.b2110941.firewallweb.repository;

import com.b2110941.firewallweb.model.PC;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 *
 * @author User
 */
@Repository
public interface pcRepository extends MongoRepository<PC, String>{
    Optional<PC> findBypcUsername(String pcUsername);
    Optional<PC> findBypcName(String pcName);

}
