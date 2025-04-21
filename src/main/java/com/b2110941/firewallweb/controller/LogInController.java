package com.b2110941.firewallweb.controller;

import com.b2110941.firewallweb.model.UserAccount;
import com.b2110941.firewallweb.repository.userAccountRepository;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class LogInController {
    @Autowired
    private userAccountRepository userAccountRepository;

    @GetMapping("/")
    public String showLoginPage(Model model) {
        // This will make flash attributes available to the view
        return "login";
    }

    @PostMapping("/")
    public String login(@RequestParam String username,
            @RequestParam String password,
            RedirectAttributes redirectAttrs,
            HttpSession session,
            Model model) {
        String normalizedUsername = username.toLowerCase();
        Optional<UserAccount> userOptional = userAccountRepository.findByUsername(normalizedUsername);

        if (!userOptional.isPresent()) {
            redirectAttrs.addFlashAttribute("toastMessage", "Username does not exist");
            redirectAttrs.addFlashAttribute("toastType", "error");
            return "redirect:/";
        }
        UserAccount user = userOptional.get();
        if (!user.getPassword().equals(password)) {
            redirectAttrs.addFlashAttribute("toastMessage", "Incorrect password");
            redirectAttrs.addFlashAttribute("toastType", "error");
            return "redirect:/";
        }

        // thành công → dùng flash attribute
        String displayName = normalizedUsername.substring(0, 1).toUpperCase()
                + normalizedUsername.substring(1);

        // Chèn <strong>…</strong> quanh tên
        String welcomeMsg = "Login successful! Welcome <strong>"
                + displayName
                + "</strong>!";
        redirectAttrs.addFlashAttribute("toastMessage", welcomeMsg);
        redirectAttrs.addFlashAttribute("toastType", "success");
        
        // lưu session và redirect
        session.setAttribute("username", normalizedUsername);
        return "redirect:/home_" + normalizedUsername + "/manageSystem";
    }
}
