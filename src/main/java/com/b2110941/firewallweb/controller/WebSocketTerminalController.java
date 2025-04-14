package com.b2110941.firewallweb.controller;

import com.b2110941.firewallweb.model.PC;
import com.b2110941.firewallweb.service.ConnectSSH;
import com.b2110941.firewallweb.service.PCService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Controller
public class WebSocketTerminalController {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketTerminalController.class);

    @Autowired
    private ConnectSSH connectSSH;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private PCService pcService;

    private final Map<String, ScheduledExecutorService> schedulers = new HashMap<>();
    private final Map<String, String> sessionToPcName = new HashMap<>();

    @MessageMapping("/terminal/{pcName}/connect")
    public void connect(@DestinationVariable String pcName, String message, @Header("simpSessionId") String sessionId) throws Exception {
        logger.info("Attempting to connect terminal for pcName: {}, sessionId: {}", pcName, sessionId);

        // Parse the incoming message to extract ownerUsername
        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> messageMap;
        String ownerUsername;
        try {
            messageMap = mapper.readValue(message, Map.class);
            ownerUsername = messageMap.get("ownerUsername");
        } catch (Exception e) {
            logger.error("Error parsing connection message for pcName: {}: {}", pcName, e.getMessage());
            messagingTemplate.convertAndSend("/topic/terminal/" + pcName + "/output",
                    "\r\n\u001B[31m*** Error parsing connection message: " + e.getMessage() + " ***\u001B[0m\r\n");
            return;
        }

        if (ownerUsername == null || ownerUsername.isEmpty()) {
            logger.warn("No ownerUsername provided for pcName: {}", pcName);
            messagingTemplate.convertAndSend("/topic/terminal/" + pcName + "/output",
                    "\r\n\u001B[31m*** Error: User not logged in ***\u001B[0m\r\n");
            return;
        }

        // Fetch PC details using both pcName and ownerUsername
        Optional<PC> computerOpt = pcService.findByPcNameAndOwnerUsername(pcName, ownerUsername);
        if (computerOpt.isEmpty()) {
            logger.warn("PC not found for pcName: {} and ownerUsername: {}", pcName, ownerUsername);
            messagingTemplate.convertAndSend("/topic/terminal/" + pcName + "/output",
                    String.format("\r\n\u001B[31m*** Computer not found: %s for user %s ***\u001B[0m\r\n", pcName, ownerUsername));
            return;
        }
        PC computer = computerOpt.get();

        String host = computer.getIpAddress();
        String username = computer.getPcUsername();
        String password = computer.getPassword();
        int port = computer.getPort();

        // Verify connectivity
        if (!connectSSH.checkConnectSSH(host, port, username, password)) {
            logger.error("Failed to connect to SSH server at {}:{} for pcName: {}", host, port, pcName);
            messagingTemplate.convertAndSend("/topic/terminal/" + pcName + "/output",
                    String.format("\r\n\u001B[31m*** Failed to connect to SSH server at %s:%d ***\u001B[0m\r\n", host, port));
            return;
        }

        try {
            if (!connectSSH.isTerminalConnected(pcName)) {
                connectSSH.connectTerminal(pcName, host, username, password, port);
                logger.info("Successfully connected to SSH server for pcName: {}", pcName);
                messagingTemplate.convertAndSend("/topic/terminal/" + pcName + "/output",
                        String.format("\r\n\u001B[32m*** Connected to %s ***\u001B[0m\r\n", host));
            } else {
                logger.info("Reusing existing SSH connection for pcName: {}", pcName);
                messagingTemplate.convertAndSend("/topic/terminal/" + pcName + "/output",
                        String.format("\r\n\u001B[32m*** Reusing existing SSH connection ***\u001B[0m\r\n"));
            }
        } catch (Exception e) {
            logger.error("Failed to establish SSH terminal for pcName: {}: {}", pcName, e.getMessage());
            messagingTemplate.convertAndSend("/topic/terminal/" + pcName + "/output",
                    String.format("\r\n\u001B[31m*** Not connected to SSH: %s ***\u001B[0m\r\n", e.getMessage()));
            return;
        }

        // Map sessionId to pcName
        sessionToPcName.put(sessionId, pcName);

        // Start polling for output
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            try {
                String output = connectSSH.readOutput(pcName);
                if (!output.isEmpty()) {
                    messagingTemplate.convertAndSend("/topic/terminal/" + pcName + "/output", output);
                }
            } catch (Exception e) {
                logger.error("Error reading output for pcName: {}: {}", pcName, e.getMessage());
                messagingTemplate.convertAndSend("/topic/terminal/" + pcName + "/output",
                        String.format("\r\n\u001B[31m*** Error reading output: %s ***\u001B[0m\r\n", e.getMessage()));
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        schedulers.put(pcName, scheduler);
    }

    @MessageMapping("/terminal/{pcName}/command")
    public void handleCommand(@DestinationVariable String pcName, String command) throws Exception {
        if (!connectSSH.isTerminalConnected(pcName)) {
            logger.warn("Attempted to send command to disconnected terminal for pcName: {}", pcName);
            messagingTemplate.convertAndSend("/topic/terminal/" + pcName + "/output",
                    String.format("\r\n\u001B[31m*** Not connected to SSH ***\u001B[0m\r\n"));
            return;
        }
        logger.debug("Sending command to pcName: {}: {}", pcName, command);
        connectSSH.sendCommand(pcName, command);
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        String pcName = sessionToPcName.remove(sessionId);
        if (pcName != null) {
            logger.info("WebSocket session disconnected for pcName: {}, sessionId: {}", pcName, sessionId);
            stopScheduler(pcName);
            messagingTemplate.convertAndSend("/topic/terminal/" + pcName + "/output",
                    "\r\n\u001B[31m*** SSH Terminal Disconnected ***\u001B[0m\r\n");
        }
    }

    private void stopScheduler(String pcName) {
        ScheduledExecutorService scheduler = schedulers.remove(pcName);
        if (scheduler != null) {
            logger.info("Stopping scheduler for pcName: {}", pcName);
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while stopping scheduler for pcName: {}", pcName);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down WebSocketTerminalController, stopping all schedulers");
        schedulers.keySet().forEach(this::stopScheduler);
    }
}
