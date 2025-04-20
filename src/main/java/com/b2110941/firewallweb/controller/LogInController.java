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
    public String showLoginPage() {
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
            model.addAttribute("error", "Username does not exist");
            return "login";
        }
        UserAccount user = userOptional.get();
        if (!user.getPassword().equals(password)) {
            model.addAttribute("error", "Incorrect password");
            return "login";
        }

        // thành công → dùng flash attribute
        redirectAttrs.addFlashAttribute("loginSuccess", true);
        redirectAttrs.addFlashAttribute("loginUser", normalizedUsername);

        session.setAttribute("username", normalizedUsername);
        return "redirect:/home_" + normalizedUsername + "/manageSystem";
    }
}
