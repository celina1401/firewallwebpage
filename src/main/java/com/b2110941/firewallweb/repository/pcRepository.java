package com.b2110941.firewallweb.repository;

import com.b2110941.firewallweb.model.PC;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.DeleteQuery;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 *
 * @author User
 */
@Repository
public interface pcRepository extends MongoRepository<PC, String>{
    Optional<PC> findBypcUsername(String pcUsername);
    Optional<PC> findBypcName(String pcName);
    List<PC> findByOwnerUsername(String ownerUsername); 
    Optional<PC> findByPcNameAndOwnerUsername(String pcName, String ownerUsername);

    @Transactional
    @DeleteQuery("{ 'ownerUsername' : ?0 }")
    long deleteByOwnerUsername(String ownerUsername);
    
    boolean existsByOwnerUsername(String ownerUsername);
}
