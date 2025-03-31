package com.b2110941.firewallweb.service;

import com.b2110941.firewallweb.model.PC;
import com.b2110941.firewallweb.service.ConnectSSH;
import com.b2110941.firewallweb.service.PCService;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.InputStream;
import java.io.OutputStream;

public class TerminalWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private PCService pcService;  // Dịch vụ tra cứu thông tin PC

    @Autowired
    private ConnectSSH connectSSH;  // Dịch vụ thiết lập kết nối SSH

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Lấy pcName từ attributes (đã được thêm trong HandshakeInterceptor)
        String pcName = (String) session.getAttributes().get("pcName");
        System.out.println("WebSocket connection established for PC: " + pcName);

        // Tra cứu thông tin PC theo pcName (đảm bảo pcService có phương thức này)
        PC computer = pcService.findByPcName(pcName);
        if (computer == null) {
            session.sendMessage(new TextMessage("PC not found: " + pcName));
            session.close();
            return;
        }

        // Thiết lập kết nối SSH tới máy chủ của PC đó
        Session sshSession = null;
        ChannelShell channel = null;
        try {
            sshSession = connectSSH.establishSSH(
                    computer.getIpAddress(),
                    computer.getPort(),
                    computer.getPcUsername(),
                    computer.getPassword()
            );
            // Mở kênh shell và bật pty để tạo môi trường terminal
            channel = (ChannelShell) sshSession.openChannel("shell");
            channel.setPty(true);
            OutputStream sshOutput = channel.getOutputStream();
            InputStream sshInput = channel.getInputStream();
            channel.connect();

            // Lưu các đối tượng SSH vào WebSocket session attributes để dùng sau này
            session.getAttributes().put("sshSession", sshSession);
            session.getAttributes().put("channel", channel);
            session.getAttributes().put("sshOutput", sshOutput);

            // Tạo thread để đọc dữ liệu từ SSH và gửi về client qua WebSocket
            new Thread(() -> {
                byte[] buffer = new byte[1024];
                int read;
                try {
                    while ((read = sshInput.read(buffer)) != -1) {
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
                        session.sendMessage(new TextMessage("Error reading SSH output: " + e.getMessage()));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }).start();

        } catch (JSchException e) {
            e.printStackTrace();
            session.sendMessage(new TextMessage("SSH connection error: " + e.getMessage()));
            session.close();
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String input = message.getPayload();
        // Lấy đối tượng sshOutput từ session attributes
        OutputStream sshOutput = (OutputStream) session.getAttributes().get("sshOutput");
        if (sshOutput != null) {
            sshOutput.write(input.getBytes());
            sshOutput.flush();
        } else {
            session.sendMessage(new TextMessage("SSH output stream not found."));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // Đóng channel và session SSH khi WebSocket đóng kết nối
        ChannelShell channel = (ChannelShell) session.getAttributes().get("channel");
        Session sshSession = (Session) session.getAttributes().get("sshSession");
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
        }
        if (sshSession != null && sshSession.isConnected()) {
            sshSession.disconnect();
        }
        super.afterConnectionClosed(session, status);
    }
}
