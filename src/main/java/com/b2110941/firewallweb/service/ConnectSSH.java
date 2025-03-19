/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.b2110941.firewallweb.service;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ConnectSSH {

    public boolean checkConnectSSH(String host, int port, String username, String password) {
        try {
            System.out.println("Connecting to: " + host + ":" + port);

            JSch jsch = new JSch();
            Session session = jsch.getSession(username, host, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");

            session.connect(5000); // Timeout 5s
            System.out.println("SSH Connection Successful!");

            session.disconnect();
            return true;
        } catch (JSchException e) {
            System.err.println("SSH Connection Error: " + e.getMessage());
            return false;
        }
    }

    public Session establishSSH(String host, int port, String username, String password) throws JSchException {
        JSch jsch = new JSch();
        Session session = jsch.getSession(username, host, port);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(5000);
        return session;
    }
    
}
