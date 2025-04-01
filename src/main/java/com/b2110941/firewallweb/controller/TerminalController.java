package com.b2110941.firewallweb.controller;

import com.b2110941.firewallweb.model.PC;
import com.b2110941.firewallweb.service.PCService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Optional;

@Controller
public class TerminalController {

    private final PCService pcService;

    @Autowired
    public TerminalController(PCService pcService) {
        this.pcService = pcService;
    }

    @GetMapping("/machine/{pcName}/terminal")
    public String getTerminal(
            @PathVariable("pcName") String pcName,
            Model model,
            HttpSession session,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith
    ) {
        // Check session: redirect to login if not authenticated
        String ownerUsername = (String) session.getAttribute("username");
        if (ownerUsername == null) {
            model.addAttribute("error", "Please login your account!");
            return "redirect:/";
        }

        // Find the PC by pcName and ownerUsername
        Optional<PC> computerOpt = pcService.findByPcNameAndOwnerUsername(pcName, ownerUsername);
        if (!computerOpt.isPresent()) {
            model.addAttribute("error", "Computer not found for pcName: " + pcName);
            return "redirect:/error";
        }
        PC computer = computerOpt.get();

        // Add attributes to the model
        model.addAttribute("computer", computer);
        model.addAttribute("pcName", pcName);
        model.addAttribute("currentMenu", "terminal");

        // Log the request for debugging
        System.out.println("Rendering terminal for pcName: " + pcName + ", owner: " + ownerUsername);

        // Return fragment for AJAX requests, or full page for direct access
        if ("XMLHttpRequest".equals(requestedWith)) {
            return "machine :: section(menuOption='terminal')";
        }
        return "machine";
    }
}
