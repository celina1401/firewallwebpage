package com.b2110941.firewallweb.service;

import com.b2110941.firewallweb.model.PC;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    private final PCService pcService;
    private final ConnectSSH connectSSH;

    @Autowired
    public TerminalWebSocketHandler(PCService pcService, ConnectSSH connectSSH) {
        this.pcService = pcService;
        this.connectSSH = connectSSH;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Retrieve pcName and username from session attributes
        String pcName = (String) session.getAttributes().get("pcName");
        String username = (String) session.getAttributes().get("username");

        if (pcName == null || username == null) {
            sendErrorAndClose(session, "Error: pcName or username is missing in session attributes.", CloseStatus.BAD_DATA);
            return;
        }

        System.out.println("WebSocket connection established for PC: " + pcName + ", user: " + username);

        // Find the PC using pcName and validate user access
        PC computer = pcService.findByPcName(pcName);
//        if (computer == null) {
//            sendErrorAndClose(session, "PC not found: " + pcName, CloseStatus.NOT_FOUND);
//            return;
//        }

        // Validate that the user has access to this PC
        if (!username.equals(computer.getOwnerUsername())) {
            sendErrorAndClose(session, "Access denied: User " + username + " does not own PC " + pcName, CloseStatus.POLICY_VIOLATION);
            return;
        }

        // Establish SSH connection to the target machine
        Session sshSession = null;
        ChannelShell channel = null;
        OutputStream sshOutput = null;
        InputStream sshInput = null;

        try {
            sshSession = connectSSH.establishSSH(
                    computer.getIpAddress(),
                    computer.getPort(),
                    computer.getPcUsername(),
                    computer.getPassword()
            );
            channel = (ChannelShell) sshSession.openChannel("shell");
            channel.setPty(true); // Enable pseudo-terminal for shell
            sshOutput = channel.getOutputStream();
            sshInput = channel.getInputStream();
            channel.connect();

            // Store SSH objects in session attributes
            session.getAttributes().put("sshSession", sshSession);
            session.getAttributes().put("channel", channel);
            session.getAttributes().put("sshOutput", sshOutput);
            session.getAttributes().put("sshInput", sshInput);

            // Send a welcome message to the client
            session.sendMessage(new TextMessage("Connected to terminal on " + pcName + "\r\n"));

            // Start a thread to read SSH output and send it to the WebSocket client
            startSshOutputReader(session, sshInput);

        } catch (JSchException e) {
            e.printStackTrace();
            sendErrorAndClose(session, "SSH connection error: " + e.getMessage(), CloseStatus.SERVER_ERROR);
            cleanupSshResources(sshSession, channel);
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // Retrieve the SSH output stream from session attributes
        OutputStream sshOutput = (OutputStream) session.getAttributes().get("sshOutput");
        if (sshOutput == null) {
            session.sendMessage(new TextMessage("Error: SSH output stream not found.\r\n"));
            return;
        }

        // Send the client's input to the SSH session
        String input = message.getPayload();
        sshOutput.write(input.getBytes());
        sshOutput.flush();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // Clean up SSH resources
        ChannelShell channel = (ChannelShell) session.getAttributes().get("channel");
        Session sshSession = (Session) session.getAttributes().get("sshSession");
        cleanupSshResources(sshSession, channel);
        System.out.println("WebSocket connection closed for session " + session.getId() + ", status: " + status);
        super.afterConnectionClosed(session, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        System.err.println("WebSocket transport error for session " + session.getId() + ": " + exception.getMessage());
        session.sendMessage(new TextMessage("Error: " + exception.getMessage() + "\r\n"));
    }

    private void sendErrorAndClose(WebSocketSession session, String errorMessage, CloseStatus status) throws Exception {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(errorMessage + "\r\n"));
            session.close(status);
        }
    }

    private void cleanupSshResources(Session sshSession, ChannelShell channel) {
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
        }
        if (sshSession != null && sshSession.isConnected()) {
            sshSession.disconnect();
        }
    }

    private void startSshOutputReader(WebSocketSession session, InputStream sshInput) {
        AtomicBoolean isRunning = new AtomicBoolean(true);
        session.getAttributes().put("readerRunning", isRunning);

        new Thread(() -> {
            byte[] buffer = new byte[1024];
            int read;
            try {
                while (isRunning.get() && (read = sshInput.read(buffer)) != -1) {
                    String output = new String(buffer, 0, read);
                    synchronized (session) {
                        if (session.isOpen()) {
                            session.sendMessage(new TextMessage(output));
                        } else {
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage("Error reading SSH output: " + e.getMessage() + "\r\n"));
                    }
                } catch (Exception ex) {
                    System.err.println("Error sending SSH output error message: " + ex.getMessage());
                }
            } finally {
                isRunning.set(false);
            }
        }).start();
    }
}
