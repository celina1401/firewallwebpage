package com.b2110941.firewallweb.controller;

import com.b2110941.firewallweb.model.PC;
import com.b2110941.firewallweb.service.ConnectSSH;
import com.b2110941.firewallweb.service.PCService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Controller
public class WebSocketTerminalController {

    @Autowired
    private ConnectSSH connectSSH;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private PCService pcService;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @MessageMapping("/terminal/{pcName}/connect")
    public void connect(@DestinationVariable String pcName, String message) throws Exception {
        // Fetch PC details
        Optional<PC> computerOpt = pcService.findByPcName(pcName);
        if (computerOpt.isEmpty()) {
            messagingTemplate.convertAndSend("/topic/terminal/" + pcName + "/output", 
                    String.format("\r\n\u001B[31m*** Computer not found: %s ***\u001B[0m\r\n", pcName));
            return;
        }
        PC computer = computerOpt.get();

        String host = computer.getIpAddress();
        String username = computer.getPcUsername();
        String password = computer.getPassword();
        int port = computer.getPort();

        // Verify connectivity
        if (!connectSSH.checkConnectSSH(host, port, username, password)) {
            messagingTemplate.convertAndSend("/topic/terminal/" + pcName + "/output",
                    String.format("\r\n\u001B[31m*** Failed to connect to SSH server at %s:%d ***\u001B[0m\r\n", host, port));
            return;
        }

        if (!connectSSH.isTerminalConnected(pcName)) {
            connectSSH.connectTerminal(pcName, host, username, password, port);
            messagingTemplate.convertAndSend("/topic/terminal/" + pcName + "/output",
                    String.format("\r\n\u001B[32m*** Connected to %s ***\u001B[0m\r\n", host));
        } else {
            messagingTemplate.convertAndSend("/topic/terminal/" + pcName + "/output",
                    String.format("\r\n\u001B[32m*** Reusing existing SSH connection ***\u001B[0m\r\n"));
        }

        // Start polling for output
        scheduler.scheduleAtFixedRate(() -> {
            try {
                String output = connectSSH.readOutput(pcName);
                if (!output.isEmpty()) {
                    messagingTemplate.convertAndSend("/topic/terminal/" + pcName + "/output", output);
                }
            } catch (Exception e) {
                messagingTemplate.convertAndSend("/topic/terminal/" + pcName + "/output",
                        String.format("\r\n\u001B[31m*** Error reading output: %s ***\u001B[0m\r\n", e.getMessage()));
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
    }

    @MessageMapping("/terminal/{pcName}/command")
    public void handleCommand(@DestinationVariable String pcName, String command) throws Exception {
        if (!connectSSH.isTerminalConnected(pcName)) {
            messagingTemplate.convertAndSend("/topic/terminal/" + pcName + "/output",
                    String.format("\r\n\u001B[31m*** Not connected to SSH ***\u001B[0m\r\n"));
            return;
        }
        connectSSH.sendCommand(pcName, command);
    }
}