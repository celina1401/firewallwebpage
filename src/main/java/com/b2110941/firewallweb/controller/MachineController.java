/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.b2110941.firewallweb.controller;

import com.b2110941.firewallweb.service.PCService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class MachineController {
    
    @Autowired
    private PCService PCService;
    
    @GetMapping("/machine/{pcName}")
    public String showMachinePage(@PathVariable("pcName") String pcName, Model model){
        try {
            // Lấy thông tin máy tính theo pcName
            model.addAttribute("computer", PCService.findByPcName(pcName));
            return "machine";
        } catch (Exception e) {
            // Nếu không tìm thấy máy tính, chuyển hướng về trang home với thông báo lỗi
            model.addAttribute("error", e.getMessage());
            model.addAttribute("computer", PCService.findAll());
            return "machine";
        }
    }
}
