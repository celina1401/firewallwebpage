/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.b2110941.firewallweb.controller;

import com.b2110941.firewallweb.model.PC;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 *
 * @author User
 */
@Controller
public class HomeController {
    @RequestMapping("/home")
    public String home(Model model){
        List<PC> computers = List.of(
//            new Computer("Computer 1"),
//            new Computer("Computer 2"),
//            new Computer("Computer 3")
        );
        model.addAttribute("computers", computers);
        return "home";
    }
    
}
