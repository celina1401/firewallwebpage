/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.b2110941.firewallweb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class TerminalController {

    @GetMapping("/machine/{pcName}/terminal")
    public String showLoginPage(@PathVariable("pcName") String pcName,
                                Model model) {
        model.addAttribute("pcName", pcName);
        return "machine"; 
    }
}
    