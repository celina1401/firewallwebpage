/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.b2110941.firewallweb.service;

import com.b2110941.firewallweb.model.FirewallRule;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UFWService {

    private static final Logger logger = LoggerFactory.getLogger(UFWService.class);

    @Autowired
    private ConnectSSH connectSSH;

    @Autowired
    private UbuntuInfo ubuntuInfo;

    public List<FirewallRule> getFirewallRules(String ipAddress, int port, String username, String password) throws JSchException {
        List<FirewallRule> firewallRules = new ArrayList<>();

        try {
            logger.info("Establishing SSH connection to {}:{}", ipAddress, port);
            Session sshSession = connectSSH.establishSSH(ipAddress, port, username, password);
            logger.info("SSH connection established successfully");

            // Kiểm tra xem UFW có được kích hoạt không
            logger.info("Checking UFW status...");
            String ufwStatus = ubuntuInfo.executeCommand(sshSession, "sudo ufw status");
            logger.info("UFW status output:\n{}", ufwStatus);

            if (ufwStatus == null || ufwStatus.trim().isEmpty()) {
                logger.warn("UFW status output is empty or null");
                throw new IllegalStateException("UFW status output is empty or null");
            }

            if (ufwStatus.contains("Status: inactive")) {
                logger.warn("UFW is inactive on the remote machine");
                throw new IllegalStateException("UFW is not active on the remote machine. Please enable it by running 'sudo ufw enable'.");
            }

            // Thực thi lệnh ufw status numbered
            logger.info("Executing command: sudo ufw status numbered");
            String ufwOutput = ubuntuInfo.executeCommand(sshSession, "sudo ufw status numbered");
            logger.info("UFW numbered output:\n{}", ufwOutput);

            if (ufwOutput == null || ufwOutput.trim().isEmpty()) {
                logger.warn("UFW numbered output is empty or null");
                throw new IllegalStateException("UFW numbered output is empty or null");
            }

            firewallRules = parseUfwOutput(ufwOutput);
            logger.info("Parsed {} firewall rules", firewallRules.size());

        } catch (JSchException e) {
            logger.error("Failed to establish SSH connection: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Error while fetching firewall rules: {}", e.getMessage(), e);
            throw new RuntimeException("Error fetching firewall rules: " + e.getMessage(), e);
        }

        return firewallRules;
    }

    private List<FirewallRule> parseUfwOutput(String output) {
        List<FirewallRule> rules = new ArrayList<>();
        String[] lines = output.split("\n");
        boolean inRulesSection = false;

        for (String line : lines) {
            logger.debug("Processing line: {}", line);
            if (line.contains("Status: active")) {
                inRulesSection = true;
                continue;
            }

            if (!inRulesSection || line.trim().isEmpty() || line.contains("To") || line.contains("--")) {
                continue;
            }

            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 5) {
                try {
                    FirewallRule rule = new FirewallRule();
                    String idStr = parts[0].replace("[", "").replace("]", "");
                    rule.setId(Long.parseLong(idStr));

                    String toField = parts[1];
                    String portProtocol = parts[2];
                    rule.setDestinationIp(toField);
                    if (portProtocol.contains("/")) {
                        String[] portProtParts = portProtocol.split("/");
                        rule.setPort(Integer.parseInt(portProtParts[0]));
                        rule.setProtocol(portProtParts[1].toUpperCase());
                    } else {
                        rule.setPort(0); // Nếu không có cổng, đặt là 0
                        rule.setProtocol("ANY");
                    }

                    rule.setAction(parts[3].toUpperCase());
                    rule.setSourceIp(parts[4]);
                    rules.add(rule);
                    logger.debug("Parsed rule: ID={}, SourceIP={}, DestIP={}, Port={}, Protocol={}, Action={}",
                            rule.getId(), rule.getSourceIp(), rule.getDestinationIp(), rule.getPort(), rule.getProtocol(), rule.getAction());
                } catch (NumberFormatException e) {
                    logger.error("Failed to parse rule from line '{}': {}", line, e.getMessage(), e);
                }
            } else {
                logger.warn("Line does not match expected format: {}", line);
            }
        }

        if (rules.isEmpty()) {
            logger.info("No firewall rules found in UFW output");
        }
        return rules;
    }
}
