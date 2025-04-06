package com.b2110941.firewallweb.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller 
public class SimpleWSController {
    @MessageMapping("/hello")
    @SendTo("/topic/example")
    public String greeting() throws Exception {
      return "hi";
    }

    @GetMapping("/test123")
    public String blankView() throws Exception {
        return "testws";
    }
}