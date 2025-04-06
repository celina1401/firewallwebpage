package com.b2110941.firewallweb.service;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    config.enableSimpleBroker("/topic");
    config.setApplicationDestinationPrefixes("/app");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/terminal")
            .setAllowedOrigins("*");
  }
}

// package com.b2110941.firewallweb.service; // Move to a config package for better organization

// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.context.annotation.Configuration;
// import org.springframework.web.socket.config.annotation.EnableWebSocket;
// import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
// import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

// @Configuration
// @EnableWebSocket
// public class WebSocketConfig implements WebSocketConfigurer {

//     private final TerminalWebSocketHandler terminalWebSocketHandler;
//     private final TerminalHandshakeInterceptor terminalHandshakeInterceptor;

//     @Autowired
//     public WebSocketConfig(TerminalWebSocketHandler terminalWebSocketHandler,
//             TerminalHandshakeInterceptor terminalHandshakeInterceptor) {
//         this.terminalWebSocketHandler = terminalWebSocketHandler;
//         this.terminalHandshakeInterceptor = terminalHandshakeInterceptor;
//     }

//     @Override
//     public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
//         registry.addHandler(terminalWebSocketHandler, "/machine/*/terminal")
//                 .addInterceptors(terminalHandshakeInterceptor)
//                 .setAllowedOrigins("*");
//     }
// }
