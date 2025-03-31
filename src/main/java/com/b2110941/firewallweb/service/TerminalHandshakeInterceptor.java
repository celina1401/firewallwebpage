package com.b2110941.firewallweb.service;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

public class TerminalHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest) {
            String uri = ((ServletServerHttpRequest) request).getServletRequest().getRequestURI();
            // Giả sử URL có dạng: /machine/{pcName}/terminal
            String[] parts = uri.split("/");
            // parts[0] là rỗng, parts[1] = "machine", parts[2] = pcName, parts[3] = "terminal"
            if (parts.length >= 4) {
                String pcName = parts[2];
                attributes.put("pcName", pcName);
                System.out.println("Extracted pcName from URL: " + pcName);
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // Không cần xử lý sau handshake
    }
}
