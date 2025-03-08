/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.b2110941.firewallweb.repository;

import com.b2110941.firewallweb.model.UserAccount;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 *
 * @author User
 */
public interface userAccountRepository extends MongoRepository<UserAccount, String>{
    UserAccount findByUsername(String username);
}
