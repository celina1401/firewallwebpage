/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.b2110941.firewallweb.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@Controller
public class TerminalController {

    @GetMapping("/machine/{pcName}/terminal")
    public String getTerminal(
            @PathVariable("pcName") String pcName,
            Model model,
            HttpSession session,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith
    ) {
        // Kiểm tra session
        String ownerUsername = (String) session.getAttribute("username");
        if (ownerUsername == null) {
            model.addAttribute("error", "Please login your account!");
            return "redirect:/";
        }
        // Lấy PC, set currentMenu = "terminal", v.v.

        if ("XMLHttpRequest".equals(requestedWith)) {
            // Trả về fragment
            return "machine :: section(menuOption='terminal')";
        }
        // Nếu user gõ trực tiếp URL
        return "machine";
    }

}
