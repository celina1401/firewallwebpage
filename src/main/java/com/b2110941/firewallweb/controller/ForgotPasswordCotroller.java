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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
                                        RedirectAttributes redirectAttrs,
                                        Model model){
        String trimmedUserName = username.trim();
        String trimmedNewPasswd = newPasswd.trim();
        String trimmedConfirmPasswd = confirmPasswd.trim();
        
        if (trimmedUserName.isEmpty() || trimmedNewPasswd.isEmpty() || trimmedConfirmPasswd.isEmpty()) {
            redirectAttrs.addFlashAttribute("error", "Please fill in all fields!");
            return "forgotPassword";
        }
        
        if (!trimmedNewPasswd.equals(trimmedConfirmPasswd)) {
            redirectAttrs.addFlashAttribute("error", "Passwords do not match!");
            return "forgotPassword";
        }
        
        // Kiểm tra username có tồn tại trong MongoDB không
        Optional<UserAccount> userAccOptional = userAccountRepository.findByUsername(trimmedUserName);
        Optional<User> userOptional = userRepository.findByUsername(trimmedUserName);
        if (!userAccOptional.isPresent()) {
            redirectAttrs.addFlashAttribute("error", "Username does not exist!");
            return "redirect:/forgot-password";
        }
        
        UserAccount userAcc = userAccOptional.get();
        User user = userOptional.get();

        //matkhautontai
        if (trimmedNewPasswd.equals(userAcc.getPassword())) {
            redirectAttrs.addFlashAttribute("error", "New password must be different from current password!");
            return "redirect:/forgot-password";
        }

        userAcc.setPassword(trimmedNewPasswd);
        user.setPassword(trimmedNewPasswd);
        userAccountRepository.save(userAcc);
        userRepository.save(user);
        
        redirectAttrs.addFlashAttribute("toastMessage", "Password changed successfully!");
        redirectAttrs.addFlashAttribute("toastType", "success");
        return "redirect:/";
    }
}
