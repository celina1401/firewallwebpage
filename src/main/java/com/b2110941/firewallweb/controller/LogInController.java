package com.b2110941.firewallweb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class LogInController {

    @GetMapping("/")
    public String showLoginPage() {
        return "login"; // Chỉ hoạt động nếu login.html nằm trong templates/
    }    
}