/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.b2110941.firewallweb.service;

import com.jcraft.jsch.*;

import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

@Service
public class ConnectSSH {

    private final Map<String, Session> sessions = new HashMap<>();
    private final Map<String, ChannelShell> channels = new HashMap<>();
    private final Map<String, InputStream> inputStreams = new HashMap<>();
    private final Map<String, OutputStream> outputStreams = new HashMap<>();

    public boolean checkConnectSSH(String host, int port, String username, String password) {
        try {
            System.out.println("Connecting to: " + host + ":" + port);

            JSch jsch = new JSch();
            Session session = jsch.getSession(username, host, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");

            session.connect(3000); // Timeout 3s
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

    public boolean checkSSHStatus(Session session) {
        if (session == null)
            return false;
        try {
            return session.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    public void connectTerminal(String pcName, String host, String username, String password, int port)
            throws JSchException, IOException {
        // Check if already connected
        Session existingSession = sessions.get(pcName);
        if (existingSession != null && checkSSHStatus(existingSession)) {
            System.out.println("Reusing existing SSH session for pcName: " + pcName);
            return;
        }

        // Establish new SSH session using existing method
        Session session = establishSSH(host, port, username, password);
        ChannelShell channel = (ChannelShell) session.openChannel("shell");

        // Configure PTY for terminal compatibility
        channel.setPty(true);
        channel.setPtyType("xterm"); // Match xterm.js
        channel.setPtySize(80, 20, 640, 480); // Initial size: 80 cols, 20 rows

        InputStream inputStream = channel.getInputStream();
        OutputStream outputStream = channel.getOutputStream();

        channel.connect(3000); // Timeout 3s

        // Store session and channel
        sessions.put(pcName, session);
        channels.put(pcName, channel);
        inputStreams.put(pcName, inputStream);
        outputStreams.put(pcName, outputStream);

        System.out.println("Terminal connected for pcName: " + pcName);
    }

    public void sendCommand(String pcName, String command) throws Exception {
        OutputStream outputStream = outputStreams.get(pcName);
        if (outputStream == null) {
            throw new IllegalStateException("No SSH connection for pcName: " + pcName);
        }
        outputStream.write(command.getBytes());
        outputStream.flush();
    }

    public String readOutput(String pcName) throws IOException {
        InputStream inputStream = inputStreams.get(pcName);
        if (inputStream == null) {
            return "";
        }
        StringBuilder output = new StringBuilder();
        byte[] buffer = new byte[1024];
        while (inputStream.available() > 0) {
            int bytesRead = inputStream.read(buffer);
            if (bytesRead > 0) {
                output.append(new String(buffer, 0, bytesRead));
            }
        }
        return output.toString();
    }

    public void disconnect(String pcName) {
        ChannelShell channel = channels.get(pcName);
        if (channel != null && !channel.isClosed()) {
            channel.disconnect();
        }
        Session session = sessions.get(pcName);
        if (session != null && checkSSHStatus(session)) {
            session.disconnect();
        }
        sessions.remove(pcName);
        channels.remove(pcName);
        inputStreams.remove(pcName);
        outputStreams.remove(pcName);
        System.out.println("Disconnected SSH for pcName: " + pcName);
    }

    public boolean isTerminalConnected(String pcName) {
        Session session = sessions.get(pcName);
        return checkSSHStatus(session);
    }

    @PreDestroy
    public void cleanup() {
        sessions.keySet().forEach(this::disconnect);
        System.out.println("Cleaned up all SSH sessions");
    }
}