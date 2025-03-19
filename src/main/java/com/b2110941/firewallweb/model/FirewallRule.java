/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.b2110941.firewallweb.model;

/**
 *
 * @author User
 */
public class FirewallRule {
    private Long id;
    private String sourceIp;
    private String destinationIp;
    private int port;
    private String protocol;
    private String action;

    public FirewallRule() {
    }

    public FirewallRule(Long id, String sourceIp, String destinationIp, int port, String protocol, String action) {
        this.id = id;
        this.sourceIp = sourceIp;
        this.destinationIp = destinationIp;
        this.port = port;
        this.protocol = protocol;
        this.action = action;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    public String getDestinationIp() {
        return destinationIp;
    }

    public void setDestinationIp(String destinationIp) {
        this.destinationIp = destinationIp;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }
    
}
