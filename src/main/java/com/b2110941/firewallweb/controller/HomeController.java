/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.b2110941.firewallweb.controller;

import com.b2110941.firewallweb.model.PC;
import com.b2110941.firewallweb.model.PCAccount;
import com.b2110941.firewallweb.repository.pcAccountRepository;
import com.b2110941.firewallweb.repository.pcRepository;
import com.b2110941.firewallweb.service.ConnectSSH;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 *
 * @author User
 */
@Controller
public class HomeController {
    @Autowired
    private pcRepository pcRepository;
    @Autowired
    private pcAccountRepository pcAccountRepository;
    @Autowired
    private ConnectSSH connectSSH;

    @RequestMapping("/home_{username}")
    public String home(@PathVariable String username, Model model, HttpSession session) {
        //Lay username tu session
        String sessionUsername = (String) session.getAttribute("username");
        
        if (sessionUsername == null || !sessionUsername.equals(username)) {
            model.addAttribute("error", "Unauthorized access! You can only view your own home page");
            return "error";
        }

        // Lấy danh sách tất cả PCs từ database
        List<PC> computers = pcRepository.findByOwnerUsername(username);
        model.addAttribute("computers", computers);
        model.addAttribute("username", username);
        return "home";
    }

    @PostMapping("/home_{username}")
    public String add_computer(
            @PathVariable String username,
            @RequestParam String pcName,
            @RequestParam String pcUsername,
            @RequestParam String ipAddress,
            @RequestParam int port,
            @RequestParam String password,
            Model model,
            HttpSession session) {
        
        //Kiem tra username tu session
        String sessionUsername = (String) session.getAttribute("username");
        
        if (sessionUsername == null || !sessionUsername.equals(username)) {
            model.addAttribute("error", "Unauthorized access! You can only add PCs to your own account");
            return "error"; 
        }
        
        Optional<PC> existPC = pcRepository.findByPcNameAndOwnerUsername(pcName, username);

        if (existPC.isPresent() && existPC.get().getOwnerUsername().equals(username)) {
            model.addAttribute("error", "PC name already exists");
            return "home";
        }else{
            // Kiểm tra kết nối SSH trước khi lưu
            boolean sshSuccess = connectSSH.checkConnectSSH(ipAddress, port, pcUsername, password);
            if (!sshSuccess) {
                model.addAttribute("error", "SSH connection failed!");
            }else{
                // Luu vao db
                PC newPC = new PC(pcName, pcUsername, ipAddress, port, password, username);
                pcRepository.save(newPC);

                PCAccount newPCAccount = new PCAccount(pcUsername, password);
                pcAccountRepository.save(newPCAccount);

                model.addAttribute("message", "PC added successfully and SSH connected!");
                
            }

        }
        // Lấy danh sách PCs để hiển thị trên giao diện
        List<PC> computers = pcRepository.findByOwnerUsername(username);
        model.addAttribute("computers", computers);
        model.addAttribute("username", username);

        return "redirect:/home_{username}";
    }

}
