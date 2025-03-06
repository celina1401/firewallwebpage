/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */


import com.b2110941.firewallweb.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;


public interface userRepository extends MongoRepository<User, String>{
    User findByUsername(String username);   // Tìm user theo username
    User findByEmail(String email);         // Tìm user theo email
}

