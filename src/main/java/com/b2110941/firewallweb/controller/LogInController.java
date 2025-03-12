package com.b2110941.firewallweb.controller;

import com.b2110941.firewallweb.model.UserAccount;
import com.b2110941.firewallweb.repository.userAccountRepository;
import jakarta.servlet.http.HttpSession;
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
                        Model model,
                        HttpSession session){
        String normalizedUsername = username.toLowerCase();

        // Tìm user trong database với username đã chuẩn hóa
        UserAccount user = userAccountRepository.findByUsername(normalizedUsername);
        
        if (user == null) {
            model.addAttribute("error", "Username does not exist!");
            return "login";
        } else if (!user.getPassword().equals(password)) {
            model.addAttribute("error", "Incorrect password");
            return "login";
        } else {
            model.addAttribute("message", "Login successful! Welcome, " + normalizedUsername);
            session.setAttribute("username", normalizedUsername); // Lưu username chữ thường vào session
            return "redirect:/home_" + normalizedUsername; // Redirect với username chữ thường
        }       
    }
}