/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.b2110941.firewallweb.repository;

import com.b2110941.firewallweb.model.PCAccount;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface pcAccountRepository extends MongoRepository<PCAccount, String>{
    PCAccount findBypcUsername(String pcUsername);
}
