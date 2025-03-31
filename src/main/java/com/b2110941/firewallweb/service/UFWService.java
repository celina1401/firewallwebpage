/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.b2110941.firewallweb.service;

import com.b2110941.firewallweb.model.PC;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UFWService {

    public ConnectSSH connectSSH;
    public UbuntuInfo ubuntuInfo;

    @Autowired

    public UFWService(ConnectSSH connectSSH, UbuntuInfo ubuntuInfo) {
        this.connectSSH = connectSSH;
        this.ubuntuInfo = ubuntuInfo;
    }

    public void executeSSHCommand(PC computer, String command, Map<String, Object> model, String pcName) {
        Session sshSession = null;
        try {
            // Thiết lập kết nối SSH
            sshSession = connectSSH.establishSSH(
                    computer.getIpAddress(),
                    computer.getPort(),
                    computer.getPcUsername(),
                    computer.getPassword()
            );
            System.out.println("Kết nối SSH thành công");

            // Thực thi lệnh bằng UbuntuInfo
            String output = ubuntuInfo.executeCommand(sshSession, command);
            System.out.println("Command Output: " + output);

        } catch (JSchException e) {
            model.put("error", "Không thể kết nối SSH tới " + pcName + ": " + e.getMessage());
            model.put("firewallStatus", "inactive");
        } catch (Exception e) {
            model.put("error", "Lỗi khi thực thi lệnh trên " + pcName + ": " + e.getMessage());
            model.put("firewallStatus", "inactive");
        } finally {
            if (sshSession != null && sshSession.isConnected()) {
                sshSession.disconnect();
            }
        }
    }

    public void addUfwRule(PC computer, String rule, Map<String, Object> model, String pcName) {
        String command = "echo '" + computer.getPassword() + "' | sudo -S ufw " + rule;
        executeSSHCommand(computer, command, model, pcName);
    }

    private List<Map<String, String>> parseUFWOutput(String ufwOutput) {
        //Phan tich output cua UFW de tra danh sach rule
        return List.of();
    }
    
//    public void loggingStatus(PC computer, String )

}
