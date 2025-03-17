/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Other/File.java to edit this template
 */
package com.b2110941.firewallweb.service;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UbuntuInfo {

    @Autowired
    private ConnectSSH connectSSH;

    public Map<String, String> getSystemInfo(String host, int port, String username, String password) {
        Map<String, String> systemInfo = new HashMap<>();

        try {
            // 📌 Dùng ConnectSSH để kết nối SSH
            Session session = connectSSH.establishSSH(host, port, username, password);

            // 📌 Lấy thông tin hệ thống
            systemInfo.put("CPU", executeCommand(session, "lscpu | grep 'Model name' | awk -F ':' '{print $2}'"));
            systemInfo.put("RAM", executeCommand(session, "free -h | grep 'Mem:' | awk '{print $2}'"));
            systemInfo.put("Ubuntu Version", executeCommand(session, "lsb_release -d | awk -F ':' '{print $2}'"));

        } catch (JSchException e) {
            systemInfo.put("error", "SSH Connection Error: " + e.getMessage());
        } catch (Exception e) {
            systemInfo.put("error", "Error executing commands: " + e.getMessage());
        }

        return systemInfo;
    }

    // 📌 Thực thi lệnh qua SSH
    public String executeCommand(Session session, String command) throws Exception {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        channel.setInputStream(null);
        channel.setErrStream(System.err);

        InputStream inputStream = channel.getInputStream();
        channel.connect();

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        String result = reader.readLine(); // Đọc dòng đầu tiên
        channel.disconnect();

        return (result != null) ? result.trim() : "N/A";
    }
}
