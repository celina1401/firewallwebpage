/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Other/File.java to edit this template
 */
package com.b2110941.firewallweb.controller;

import com.b2110941.firewallweb.model.User;
import com.b2110941.firewallweb.model.UserAccount;
import com.b2110941.firewallweb.repository.userAccountRepository;
import com.b2110941.firewallweb.repository.userRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ForgotPasswordCotroller {
    
    @Autowired
    private userAccountRepository userAccountRepository;
    @Autowired
    private userRepository userRepository;
    
    @GetMapping("/forgot-password")
    public String showForgotPassword(){
        return "forgotPassword";
    }
    
    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam String username,
                                        @RequestParam String newPasswd,
                                        @RequestParam String confirmPasswd,
                                        Model model){
        String trimmedUserName = username.trim();
        String trimmedNewPasswd = newPasswd.trim();
        String trimmedConfirmPasswd = confirmPasswd.trim();
        
        if (trimmedUserName.isEmpty() || trimmedNewPasswd.isEmpty() || trimmedConfirmPasswd.isEmpty()) {
            model.addAttribute("error", "All fields are required and cannot be empty!");
            return "forgotPassword";
        }
        
        if (!trimmedNewPasswd.equals(trimmedConfirmPasswd)) {
            model.addAttribute("error", "Confirm password does not match!");
            return "forgotPassword";
        }
        
        // Kiểm tra username có tồn tại trong MongoDB không
        Optional<UserAccount> userAccOptional = userAccountRepository.findByUsername(trimmedUserName);
        Optional<User> userOptional = userRepository.findByUsername(trimmedUserName);
        if (!userAccOptional.isPresent()) {
            model.addAttribute("error", "Username does not exist!");
            return "forgotPassword";
        }
        
        UserAccount userAcc = userAccOptional.get();
        User user = userOptional.get();
        userAcc.setPassword(trimmedNewPasswd);
        user.setPassword(trimmedNewPasswd);
        userAccountRepository.save(userAcc);
        userRepository.save(user);
        
        
        model.addAttribute("message", "Your password has been updated successfully!");
        return "redirect:/";
    }
}
