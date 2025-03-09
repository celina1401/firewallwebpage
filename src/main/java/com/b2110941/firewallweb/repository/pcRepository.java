/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.b2110941.firewallweb.repository;

import com.b2110941.firewallweb.model.PC;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 *
 * @author User
 */
@Repository
public interface pcRepository extends MongoRepository<PC, String>{
    PC findByUbuntuUsername(String pcUsername);
    PC findByComputerName(String pcName);
}
