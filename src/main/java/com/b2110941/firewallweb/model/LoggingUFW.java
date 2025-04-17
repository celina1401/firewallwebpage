package com.b2110941.firewallweb.model;

public class LoggingUFW {
    private String timestamp;
    private String action;
    private String sourceIp;
    private String sourcePort;
    private String destinationIp;
    private String destinationPort;
    private String protocol;
    private String interface_;
    private String fullLog;

    public LoggingUFW() {
    }

    public LoggingUFW(String timestamp, String action, String destinationIp, String protocol) {
        this.timestamp = timestamp;
        this.action = action;
        this.destinationIp = destinationIp;
        this.protocol = protocol;
    }

    // Getters and Setters
    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    public String getSourcePort() {
        return sourcePort;
    }

    public void setSourcePort(String sourcePort) {
        this.sourcePort = sourcePort;
    }

    public String getDestinationIp() {
        return destinationIp;
    }

    public void setDestinationIp(String destinationIp) {
        this.destinationIp = destinationIp;
    }

    public String getDestinationPort() {
        return destinationPort;
    }

    public void setDestinationPort(String destinationPort) {
        this.destinationPort = destinationPort;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getInterface() {
        return interface_;
    }

    public void setInterface(String interface_) {
        this.interface_ = interface_;
    }

    public String getFullLog() {
        return fullLog;
    }

    public void setFullLog(String fullLog) {
        this.fullLog = fullLog;
    }
}
