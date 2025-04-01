package com.b2110941.firewallweb.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import java.util.Map;

@Component
public class TerminalHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        // Get HTTP session
        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            HttpSession session = servletRequest.getServletRequest().getSession();

            // Extract pcName from URL path
            String path = request.getURI().getPath();
            String[] pathParts = path.split("/");
            if (pathParts.length >= 3) {
                String pcName = pathParts[pathParts.length - 2];
                attributes.put("pcName", pcName);

                // Add authentication check here
                String token = request.getURI().getQuery();
                // Validate token if needed

                return true; // Allow handshake
            }
        }
        return false; // Reject handshake
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
        // Nothing to do after handshake
    }
}
