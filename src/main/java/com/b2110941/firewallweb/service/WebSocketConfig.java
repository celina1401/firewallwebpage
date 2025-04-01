package com.b2110941.firewallweb.config; // Move to a config package for better organization

import com.b2110941.firewallweb.service.TerminalHandshakeInterceptor;
import com.b2110941.firewallweb.service.TerminalWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TerminalWebSocketHandler terminalWebSocketHandler;
    private final TerminalHandshakeInterceptor terminalHandshakeInterceptor;

    @Autowired
    public WebSocketConfig(TerminalWebSocketHandler terminalWebSocketHandler,
            TerminalHandshakeInterceptor terminalHandshakeInterceptor) {
        this.terminalWebSocketHandler = terminalWebSocketHandler;
        this.terminalHandshakeInterceptor = terminalHandshakeInterceptor;
    }

    @Override
//    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
//        registry.addHandler(terminalWebSocketHandler, "/machine/{pcName}/terminal")
//                .addInterceptors(terminalHandshakeInterceptor)
//                .setAllowedOrigins("http://localhost:1402"); // Restrict origins for security
//    }
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(terminalWebSocketHandler, "/machine/*/terminal")
                .addInterceptors(terminalHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
