package com.b2110941.firewallweb.controller;

import com.b2110941.firewallweb.model.UserAccount;
import com.b2110941.firewallweb.repository.userAccountRepository;
import com.b2110941.firewallweb.repository.userRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LogInController {
    @Autowired
     private userAccountRepository userAccountRepository;

    @GetMapping("/")
    public String showLoginPage(Model model) {
        return "login"; // Chỉ hoạt động nếu login.html nằm trong templates/
    }    
    
    @PostMapping("/")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        Model model){
        UserAccount user = userAccountRepository.findByUsername(username);
        if (user == null){
            model.addAttribute("error", "Username does not exist!");
            return "login";
        } else if (!user.getPassword().equals(password)){
            model.addAttribute("error", "Incorrect password");
            return "login";
        }else{
            model.addAttribute("message","Login successful! Welcome, " + username);
            return "home";
        }
        
        
    }
}