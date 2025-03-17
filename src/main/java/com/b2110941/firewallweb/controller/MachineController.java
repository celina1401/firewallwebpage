/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.b2110941.firewallweb.controller;

import com.b2110941.firewallweb.model.PC;
import com.b2110941.firewallweb.service.ConnectSSH;
import com.b2110941.firewallweb.service.PCService;
import com.b2110941.firewallweb.service.UbuntuInfo;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@Controller
public class MachineController {

    @Autowired
    private PCService pcService;
    @Autowired
    private UbuntuInfo ubuntuInfo;
    @Autowired
    private ConnectSSH connectSSH;

    @GetMapping("/machine/{pcName}")
    public String showMachinePage(
            @PathVariable("pcName") String pcName,
            Model model,
            HttpSession session,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith) {
        return redirectToMenu(pcName, "information", model, session, requestedWith);
    }

    @GetMapping("/machine/{pcName}/{menuOption}")
    public String menuNav(
            @PathVariable("pcName") String pcName,
            @PathVariable("menuOption") String menuOption,
            Model model,
            HttpSession session,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith) {
        return redirectToMenu(pcName, menuOption, model, session, requestedWith);
    }

    private String redirectToMenu(
            String pcName,
            String menuOption,
            Model model,
            HttpSession session,
            String requestedWith) {
        String ownerUsername = (String) session.getAttribute("username");
        if (ownerUsername == null) {
            model.addAttribute("error", "User not logged in");
            model.addAttribute("currentMenu", "information");
            return "redirect:/";
        }

        try {
            Optional<PC> computerOptional = pcService.findByPcNameAndOwnerUsername(pcName, ownerUsername);
            if (computerOptional.isPresent()) {
                model.addAttribute("computer", computerOptional.get());
                model.addAttribute("currentMenu", menuOption.toLowerCase());
            } else {
                model.addAttribute("error", "PC not found with name: " + pcName + " for this user");
                model.addAttribute("computer", null);
                model.addAttribute("currentMenu", "information");
            }
        } catch (Exception e) {
            model.addAttribute("error", "Error retrieving PC: " + e.getMessage());
            model.addAttribute("computer", null);
            model.addAttribute("currentMenu", "information");
        }

        List<PC> computers = pcService.findByOwnerUsername(ownerUsername);
        model.addAttribute("computers", computers);

        if ("XMLHttpRequest".equals(requestedWith)) {
            // Trả về fragment trong machine.html
            return "machine :: section(menuOption='" + menuOption.toLowerCase() + "')";
        }
        return "machine";
    }

    // 📌 API lấy thông tin Ubuntu qua SSH
    @GetMapping("/machine/{pcName}/information")
    public String infoUbuntu(
            @PathVariable("pcName") String pcName,
            Model model,
            HttpSession session,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith) { // Add this parameter
        String ownerUsername = (String) session.getAttribute("username");
        if (ownerUsername == null) {
            model.addAttribute("error", "Bạn chưa đăng nhập");
            return "redirect:/";
        }

        Optional<PC> computerOptional = pcService.findByPcNameAndOwnerUsername(pcName, ownerUsername);
        if (computerOptional.isEmpty()) {
            model.addAttribute("error", "Không tìm thấy máy tính: " + pcName);
            model.addAttribute("currentMenu", "information");
            return "machine";
        }

        PC computer = computerOptional.get();
        model.addAttribute("computer", computer);
        model.addAttribute("currentMenu", "information");

        Session sshSession = null;
        try {
            sshSession = connectSSH.establishSSH(computer.getIpAddress(), computer.getPort(), computer.getPcUsername(), computer.getPassword());
            System.out.println("Ket noi ssh thanh cong");
            Map<String, String> systemInfo = new HashMap<>();
            systemInfo.put("CPU", ubuntuInfo.executeCommand(sshSession, "lscpu | grep 'Model name' | awk -F ':' '{print $2}'"));
            systemInfo.put("RAM", ubuntuInfo.executeCommand(sshSession, "free -h | grep 'Mem:' | awk '{print $2}'"));
            systemInfo.put("Ubuntu Version", ubuntuInfo.executeCommand(sshSession, "lsb_release -d | awk -F ':' '{print $2}'"));
            model.addAttribute("systemInfo", systemInfo);
        } catch (JSchException e) {
            model.addAttribute("error", "Cannot connect SSH to " + pcName);
        } catch (Exception e) {
            model.addAttribute("error", "Error retrieving system information: " + e.getMessage());
        } finally {
            if (sshSession != null && sshSession.isConnected()) {
                sshSession.disconnect();
            }
        }

        // Check if this is an AJAX request and return the appropriate fragment
        if ("XMLHttpRequest".equals(requestedWith)) {
            return "machine :: section(menuOption='information')";
        }
        return "machine";
    }
}
