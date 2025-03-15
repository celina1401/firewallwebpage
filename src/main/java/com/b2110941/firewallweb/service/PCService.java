/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.b2110941.firewallweb.service;

import com.b2110941.firewallweb.model.PC;
import com.b2110941.firewallweb.repository.pcRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PCService {
    @Autowired
    private pcRepository pcRepository;

    // Lấy danh sách tất cả máy tính
    public List<PC> findAll() {
        return pcRepository.findAll();
    }

    // Lấy thông tin máy tính theo pcName
    public PC findByPcName(String pcName) {
        Optional<PC> computer = pcRepository.findBypcName(pcName);
        return computer.orElseThrow(() -> new RuntimeException("Computer not found with pcName: " + pcName));
    }
    
    public PC findByPcUsername(String pcUsername) {
        Optional<PC> computer = pcRepository.findBypcUsername(pcUsername);
        return computer.orElseThrow(() -> new RuntimeException("Computer not found with pcUsername: " + pcUsername));
    }
    
    public List<PC> findByOwnerUsername(String ownerUsername) {
        return pcRepository.findByOwnerUsername(ownerUsername);
    }    
    
    public Optional<PC> findByPcNameAndOwnerUsername(String pcName, String ownerUsername) {
        return pcRepository.findByPcNameAndOwnerUsername(pcName, ownerUsername);
    }

    // Thêm máy tính mới
    public PC save(PC computer) {
        return pcRepository.save(computer);
    }

    // Xóa máy tính theo ID
    public void deleteById(String id) {
        pcRepository.deleteById(id);
    }

}
