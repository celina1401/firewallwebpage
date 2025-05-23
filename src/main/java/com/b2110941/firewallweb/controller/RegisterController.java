
package com.b2110941.firewallweb.controller;

import com.b2110941.firewallweb.model.User;
import com.b2110941.firewallweb.model.UserAccount;
import com.b2110941.firewallweb.repository.userAccountRepository;
import com.b2110941.firewallweb.repository.userRepository;
import jakarta.servlet.http.HttpSession;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
    @Autowired
    private userAccountRepository userAccountRepository;
    
    @PostMapping("/register")
    public String register(@RequestParam String fullname, 
                           @RequestParam String username,
                           @RequestParam String password,
                           @RequestParam String confirm_password,
                           @RequestParam String email,
                           RedirectAttributes redirectAttrs,
                           Model model,
                           HttpSession session){
        
        String normalizedUsername = username.toLowerCase();
        
        Optional<User>  existingUserByUsername = userRepository.findByUsername(username);
        System.out.println(existingUserByUsername);
 
        if (!existingUserByUsername.equals(Optional.empty()) ) {
            model.addAttribute("error", "Username already exists");
            return "register"; // Trả về trang đăng ký với thông báo lỗi
        }

        // Kiểm tra mật khẩu và xác nhận mật khẩu
        if (!password.equals(confirm_password)) {
            redirectAttrs.addFlashAttribute("error", "Passwords do not match");
            return "register"; // Trả về trang đăng ký với thông báo lỗi
        }

        // Tạo user mới và lưu vào database
        User newUser = new User(fullname, normalizedUsername, email, password);
        userRepository.save(newUser);
        session.setAttribute("fullname", fullname);
        session.setAttribute("username", normalizedUsername);
        session.setAttribute("email", email);
        UserAccount newUserAccount = new UserAccount(normalizedUsername, password);
        userAccountRepository.save(newUserAccount);

        redirectAttrs.addFlashAttribute("toastMessage", "Register successfully!");
        redirectAttrs.addFlashAttribute("toastType", "success");

        return "redirect:/";
    }
}
