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
     * 
     * @param pc           The PC to add the rule to
     * @param action       The action (allow, deny, limit)
     * @param isOutgoing   Whether the rule is for outgoing traffic
     * @param protocol     The protocol (tcp, udp, icmp, none)
     * @param toType       The destination type (any, range, app,
     *                     ipaddress,interface)
     * @param toIp         The destination IP address
     * @param toRangeStart The start of port range
     * @param toRangeEnd   The end of port range
     * @param port         The port number
     * @param fromType     The source type (any, ipaddress)
     * @param fromIp       The source IP address
     * @param app          The application name
     * @return Result of the operation
     */
    public String addRuleFromForm(PC pc, String action, boolean isOutgoing,
            String protocol, String toType, String toIp,
            String toRangeStart, String toRangeEnd, String port,
            String fromType, String fromIp, String app) {

        StringBuilder outBuilder = new StringBuilder();
        String result;
        String direction = isOutgoing ? "out" : "in";

        try {
            Session session = connectSSH.establishSSH(pc.getIpAddress(), pc.getPort(),
                    pc.getPcUsername(), pc.getPassword());
            ChannelExec channel = (ChannelExec) session.openChannel("exec");

            StringBuilder cmdBuilder = new StringBuilder("echo '")
                    .append(pc.getPassword())
                    .append("' | sudo -S ufw ")
                    .append(action).append(" ")
                    .append(direction).append(" ");

            // ------------------ Handle FROM clause ------------------
            if ("ipaddress".equals(fromType) && fromIp != null && !fromIp.isEmpty()) {
                cmdBuilder.append("from ").append(fromIp).append(" ");
            } else {
                cmdBuilder.append("from any ");
            }

            // ------------------ Handle TO clause ------------------
            if ("ipaddress".equals(toType) && toIp != null && !toIp.isEmpty()) {
                cmdBuilder.append("to ").append(toIp).append(" ");
            } else if ("range".equals(toType) && toRangeStart != null && !toRangeStart.isEmpty()) {
                // Assume CIDR format input like 192.168.1.0/24
                cmdBuilder.append("to ").append(toRangeStart).append(" ");
            } else if (!"app".equals(toType) && !"interface".equals(toType)) {
                cmdBuilder.append("to any ");
            }

            // ------------------ Handle Interface or App ------------------
            if ("app".equals(toType) && app != null && !app.isEmpty()) {
                cmdBuilder.append("app \"").append(app).append("\" ");
            }

            if ("interface".equals(toType) && app != null && !app.isEmpty()) {
                cmdBuilder.append("on ").append(app).append(" ");
            }

            // ------------------ Handle Port and Protocol ------------------            
            // Handle port or port range first
            if (port != null && !port.isEmpty()) {
                cmdBuilder.append("port ").append(port).append(" ");
            } else if (toRangeStart != null && !toRangeStart.isEmpty()
                    && toRangeEnd != null && !toRangeEnd.isEmpty()) {
                if ("none".equals(protocol)) {
                    return "error: Port range requires a protocol (tcp/udp)";
                }
                // Make sure we're using the correct format for port range
                cmdBuilder.append("port ").append(toRangeStart).append(":").append(toRangeEnd).append(" ");
                logger.info("Adding port range: {}-{}", toRangeStart, toRangeEnd);
            }

            // Then add protocol (only once)
            if (!"none".equals(protocol)) {
                cmdBuilder.append("proto ").append(protocol).append(" ");
            }

            // ------------------ Execute Command ------------------
            String command = cmdBuilder.toString().trim();
            logger.info("Executing UFW command: {}", command);

            channel.setCommand(command);
            InputStream in = channel.getInputStream();
            channel.connect();

            byte[] tmp = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0)
                        break;
                    outBuilder.append(new String(tmp, 0, i));
                }
                if (channel.isClosed())
                    break;
            }

            String output = outBuilder.toString();
            logger.info("Command output: {}", output);

            if (output.contains("Rule added") || output.contains("Skipping")) {
                result = "success";
            } else if (output.contains("ERROR")) {
                result = "error: " + output;
            } else {
                result = "success";
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
     * 
     * @param pc     The PC to delete the rule from
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

            String command = "echo '" + pc.getPassword() + "' | sudo -S ufw --force delete " + ruleId;
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
     * 
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
     * 
     * @param pc     The PC to modify
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
     * 
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