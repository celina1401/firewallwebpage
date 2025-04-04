package com.b2110941.firewallweb.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import com.b2110941.firewallweb.model.User;
import com.b2110941.firewallweb.repository.userRepository;

public class UserService {
    @Autowired
    userRepository userRepository;

    public void deleteUser(String username) {
        try {
            Optional<User> user = userRepository.findByUsername(username);
            userRepository.delete(user.get());

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
