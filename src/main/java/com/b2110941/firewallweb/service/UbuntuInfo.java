/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Other/File.java to edit this template
 */
package com.b2110941.firewallweb.service;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
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
            Session session = connectSSH.establishSSH(host, port, username, password);

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
        ChannelExec channel = null;
        StringBuilder outputBuffer = new StringBuilder();
        try {
            // Tạo channel exec
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            InputStream in = channel.getInputStream();
            InputStream err_in = channel.getErrStream();

            //Thực thi
            channel.connect();

            BufferedReader reader = new BufferedReader(new InputStreamReader(in));

            String line;
//            System.out.println("aaaaaaaaaa");
            while ((line = reader.readLine()) != null) {
                outputBuffer.append(line).append("\n");

            }
            return outputBuffer.toString();

        } catch (Exception e) {
            throw new Exception("Failed to execute command: " + command + ". Error: " + e.getMessage(), e);
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
    }
}
