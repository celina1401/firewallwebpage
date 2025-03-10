/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.b2110941.firewallweb.controller;

import com.b2110941.firewallweb.model.PC;
import com.b2110941.firewallweb.model.PCAccount;
import com.b2110941.firewallweb.repository.pcAccountRepository;
import com.b2110941.firewallweb.repository.pcRepository;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 *
 * @author User
 */
@Controller
public class HomeController {
    @RequestMapping("/home")
    public String home(Model model, HttpSession session){
        //Lay username tu session
        String username = (String) session.getAttribute("username");
        if (username == null) {
            return "redirect:/"; 
        }
        
        // Lấy danh sách tất cả PCs từ database
        List<PC> computers = pcRepository.findAll();
        model.addAttribute("computers", computers);
        model.addAttribute("username", username);
        return "home";
    }
    
    @Autowired
    private pcRepository pcRepository;
    @Autowired
    private pcAccountRepository pcAccountRepository;
    
    @PostMapping("/home")
    public String add_computer (
                                @RequestParam String pcName,
                                @RequestParam String pcUsername,
                                @RequestParam String ipAddress,
                                @RequestParam String port,
                                @RequestParam String password,
                                Model model,
                                HttpSession session){
        Optional<PC> existPCByUsername = pcRepository.findBypcUsername(pcUsername);
        Optional<PC> existPCBypcName = pcRepository.findBypcName(pcName);
        String username = (String) session.getAttribute("username");
        
        if (existPCByUsername != null && existPCBypcName != null){
            model.addAttribute("error", "Your PC already exists");
            return "home";
        }else if (existPCBypcName != null){
             model.addAttribute("error", "PC name already exist");
        }else{
            PC newPC = new PC(pcName, pcUsername, ipAddress, port, password);
            pcRepository.save(newPC);
            session.setAttribute("username", username);
            
            PCAccount newPCAccount = new PCAccount(pcUsername, password);
            pcAccountRepository.save(newPCAccount);     
            
            model.addAttribute("message", "PC added successfully");
        }
        // Lấy lại danh sách PCs để hiển thị
        List<PC> computers = pcRepository.findAll();
        model.addAttribute("computers", computers);
        model.addAttribute("username", username);
        return "home";
        
    }
    
}
