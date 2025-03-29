package com.b2110941.firewallweb.service;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

public class TerminalWebSocketHandler extends TextWebSocketHandler {

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Lấy pcName từ attributes (đã được thêm trong HandshakeInterceptor)
        String pcName = (String) session.getAttributes().get("pcName");
        System.out.println("WebSocket connection established for PC: " + pcName);
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String input = message.getPayload();
        // Ví dụ: echo lại thông điệp
        session.sendMessage(new TextMessage("Bạn vừa gõ: " + input));
    }
}
