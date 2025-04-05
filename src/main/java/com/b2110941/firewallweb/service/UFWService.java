package com.b2110941.firewallweb.service;

import com.b2110941.firewallweb.model.FirewallRule;
import com.b2110941.firewallweb.model.PC;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UFWService {
    
    private static final Logger logger = LoggerFactory.getLogger(UFWService.class);

    @Autowired
    private ConnectSSH connectSSH;

    /**
     * Add a firewall rule based on form data from machine.html
     * @param pc The PC to add the rule to
     * @param action The action (allow, deny, limit)
     * @param isOutgoing Whether the rule is for outgoing traffic
     * @param protocol The protocol (tcp, udp, icmp, none)
     * @param toType The destination type (any, range, app, ipaddress, interface)
     * @param toIp The destination IP address
     * @param toRangeStart The start of port range
     * @param toRangeEnd The end of port range
     * @param port The port number
     * @param fromType The source type (any, ipaddress)
     * @param fromIp The source IP address
     * @param app The application name
     * @return Result of the operation
     */
    public String addRuleFromForm(PC pc, String action, boolean isOutgoing, 
                                 String protocol, String toType, String toIp,
                                 int toRangeStart, int toRangeEnd, String port,
                                 String fromType, String fromIp, String app) {
        
        String result = "";
        StringBuilder outBuilder = new StringBuilder();
        String command = "";
        String direction = isOutgoing ? "out" : "in";
        
        try {
            Session session = connectSSH.establishSSH(pc.getIpAddress(), pc.getPort(), 
                                                     pc.getPcUsername(), pc.getPassword());
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            
            // Build the UFW command based on form inputs
            StringBuilder cmdBuilder = new StringBuilder("echo '").append(pc.getPassword())
                                      .append("' | sudo -S ufw ").append(action).append(" ")
                                      .append(direction).append(" ");
            
            // Handle protocol if not 'none'
            if (!protocol.equals("none")) {
                cmdBuilder.append("proto ").append(protocol).append(" ");
            }
            
            // Handle source (from)
            if (fromType != null && fromType.equals("ipaddress") && !fromIp.isEmpty()) {
                cmdBuilder.append("from ").append(fromIp).append(" ");
            }
            
            // Handle destination (to)
            if (toType != null) {
                switch (toType) {
                    case "ipaddress":
                        if (!toIp.isEmpty()) {
                            cmdBuilder.append("to ").append(toIp).append(" ");
                        }
                        break;
                    case "range":
                        if (toRangeStart > 0 && toRangeEnd > 0) {
                            cmdBuilder.append("port ").append(toRangeStart)
                                     .append(":").append(toRangeEnd).append(" ");
                        }
                        break;
                    case "app":
                        if (!app.isEmpty()) {
                            cmdBuilder.append("\"").append(app).append("\" ");
                        }
                        break;
                    // For 'any' and 'interface', no additional parameters needed
                }
            }
            
            // Handle port if specified and not using range
            if (!port.isEmpty() && toType != null && !toType.equals("range")) {
                cmdBuilder.append("port ").append(port).append(" ");
            }
            
            command = cmdBuilder.toString().trim();
            logger.info("Executing UFW command: {}", command);
            
            channel.setCommand(command);
            InputStream in = channel.getInputStream();
            channel.connect();
            
            byte[] tmp = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0) {
                        break;
                    }
                    outBuilder.append(new String(tmp, 0, i));
                }
                if (channel.isClosed()) {
                    break;
                }
            }
            
            try {
                Thread.sleep(1000);
            } catch (Exception ie) {
                logger.error("Error while waiting for command execution", ie);
            }
            
            String output = outBuilder.toString();
            logger.info("Command output: {}", output);
            
            if (output.contains("Rule added") || output.contains("Skipping")) {
                result = "success";
            } else if (output.contains("ERROR")) {
                result = "error: " + output;
            } else {
                result = "success"; // Assume success if no specific error
            }
            
            channel.disconnect();
            session.disconnect();
        } catch (IOException | JSchException e) {
            logger.error("Error adding firewall rule", e);
            result = "error: " + e.getMessage();
        }
        
        return result;
    }
    
    /**
     * Delete a firewall rule by ID
     * @param pc The PC to delete the rule from
     * @param ruleId The ID of the rule to delete
     * @return Result of the operation
     */
    public String deleteRule(PC pc, String ruleId) {
        String result = "";
        StringBuilder outBuilder = new StringBuilder();
        
        try {
            Session session = connectSSH.establishSSH(pc.getIpAddress(), pc.getPort(), 
                                                     pc.getPcUsername(), pc.getPassword());
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            
            String command = "echo '" + pc.getPassword() + "' | sudo -S ufw delete " + ruleId;
            logger.info("Executing UFW delete command for rule ID: {}", ruleId);
            
            channel.setCommand(command);
            InputStream in = channel.getInputStream();
            channel.connect();
            
            byte[] tmp = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0) {
                        break;
                    }
                    outBuilder.append(new String(tmp, 0, i));
                }
                if (channel.isClosed()) {
                    break;
                }
            }
            
            String output = outBuilder.toString();
            logger.info("Delete command output: {}", output);
            
            if (output.contains("Rule deleted")) {
                result = "success";
            } else {
                result = "error: " + output;
            }
            
            channel.disconnect();
            session.disconnect();
        } catch (IOException | JSchException e) {
            logger.error("Error deleting firewall rule", e);
            result = "error: " + e.getMessage();
        }
        
        return result;
    }
    
    /**
     * Get the status of UFW (enabled or disabled)
     * @param pc The PC to check
     * @return "active" if UFW is enabled, "inactive" otherwise
     */
    public String getUFWStatus(PC pc) {
        String result = "inactive";
        StringBuilder outBuilder = new StringBuilder();
        
        try {
            Session session = connectSSH.establishSSH(pc.getIpAddress(), pc.getPort(), 
                                                     pc.getPcUsername(), pc.getPassword());
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            
            String command = "echo '" + pc.getPassword() + "' | sudo -S ufw status | grep Status";
            
            channel.setCommand(command);
            InputStream in = channel.getInputStream();
            channel.connect();
            
            byte[] tmp = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0) {
                        break;
                    }
                    outBuilder.append(new String(tmp, 0, i));
                }
                if (channel.isClosed()) {
                    break;
                }
            }
            
            String output = outBuilder.toString();
            if (output.contains("active")) {
                result = "active";
            }
            
            channel.disconnect();
            session.disconnect();
        } catch (IOException | JSchException e) {
            logger.error("Error getting UFW status", e);
        }
        
        return result;
    }
    
    /**
     * Enable or disable UFW
     * @param pc The PC to modify
     * @param enable True to enable, false to disable
     * @return Result of the operation
     */
    public String toggleUFW(PC pc, boolean enable) {
        String result = "";
        StringBuilder outBuilder = new StringBuilder();
        
        try {
            Session session = connectSSH.establishSSH(pc.getIpAddress(), pc.getPort(), 
                                                     pc.getPcUsername(), pc.getPassword());
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            
            String command = "echo '" + pc.getPassword() + "' | sudo -S ufw " + 
                            (enable ? "--force enable" : "--force disable");
            
            channel.setCommand(command);
            InputStream in = channel.getInputStream();
            channel.connect();
            
            byte[] tmp = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0) {
                        break;
                    }
                    outBuilder.append(new String(tmp, 0, i));
                }
                if (channel.isClosed()) {
                    break;
                }
            }
            
            String output = outBuilder.toString();
            if ((enable && output.contains("enabled")) || (!enable && output.contains("disabled"))) {
                result = "success";
            } else {
                result = "error: " + output;
            }
            
            channel.disconnect();
            session.disconnect();
        } catch (IOException | JSchException e) {
            logger.error("Error toggling UFW", e);
            result = "error: " + e.getMessage();
        }
        
        return result;
    }
    
    /**
     * Get all firewall rules for a PC
     * @param pc The PC to get rules for
     * @return Array of FirewallRule objects
     */
    public FirewallRule[] getAllRules(PC pc) {
        // Implementation to get all rules
        // This would parse the output of 'sudo ufw status numbered'
        // and return an array of FirewallRule objects
        return new FirewallRule[0]; // Placeholder
    }
}