/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.b2110941.firewallweb.controller;

import com.b2110941.firewallweb.model.User;
import com.b2110941.firewallweb.repository.userRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 *
 * @author User
 */
@Controller
public class RegisterController {
    @GetMapping("/register")
    public String showSignupPage(){
        return "register";
    }
    
    @Autowired
    private userRepository userRepository;
    
    @PostMapping("/register")
    public String register(@RequestParam String fullname, 
                           @RequestParam String username,
                           @RequestParam String password,
                           @RequestParam String confirm_password,
                           @RequestParam String email,
                           Model model){
        // Kiểm tra xem username hoặc email đã tồn tại chưa
        User existingUserByUsername = userRepository.findByUsername(username);
        User existingUserByEmail = userRepository.findByEmail(email);

        if (existingUserByUsername != null) {
            model.addAttribute("error", "Username already exists");
            return "register"; // Trả về trang đăng ký với thông báo lỗi
        }

        if (existingUserByEmail != null) {
            model.addAttribute("error", "Email already exists");
            return "register"; // Trả về trang đăng ký với thông báo lỗi
        }

        // Kiểm tra mật khẩu và xác nhận mật khẩu
        if (!password.equals(confirm_password)) {
            model.addAttribute("error", "Passwords do not match");
            return "register"; // Trả về trang đăng ký với thông báo lỗi
        }

        // Tạo user mới và lưu vào database
        User newUser = new User(fullname, username, email, password);
        userRepository.save(newUser);

        model.addAttribute("message", "Registration successful! Please login.");
        return "register"; // Trả về trang đăng nhập sau khi đăng ký thành công
    }
}
