package com.b2110941.firewallweb.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.b2110941.firewallweb.model.PC;
import com.b2110941.firewallweb.service.ConnectSSH;
import com.b2110941.firewallweb.service.PCService;
import com.b2110941.firewallweb.service.UbuntuInfo;
import com.jcraft.jsch.Session;

import jakarta.servlet.http.HttpSession;

@Controller
public class FillterController {

    @Autowired
    private UbuntuInfo ubuntuInfo;

    @Autowired
    private PCService pcService;

    @Autowired
    private ConnectSSH connectSSH;

    @GetMapping("/machine/{pcName}/logging-filter")
    @ResponseBody
    public Map<String, Object> filterLogsAjax(
            @PathVariable("pcName") String pcName,
            // @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "action", required = false, defaultValue = "all") String action,
            @RequestParam(value = "protocol", required = false, defaultValue = "all") String protocol,
            @RequestParam(value = "rows", required = false, defaultValue = "10") int rows,
            RedirectAttributes redirectAttrs,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();
        String ownerUsername = (String) session.getAttribute("username");

        if (ownerUsername == null) {
            response.put("success", false);
            response.put("message", "User not logged in");
            return response;
        }

        Optional<PC> computerOptional = pcService.findByPcNameAndOwnerUsername(pcName, ownerUsername);
        if (computerOptional.isEmpty()) {
            response.put("success", false);
            response.put("message", "Computer not found");
            return response;
        }

        PC computer = computerOptional.get();
        Session sshSession = null;

        try {
            sshSession = connectSSH.establishSSH(
                    computer.getIpAddress(),
                    computer.getPort(),
                    computer.getPcUsername(),
                    computer.getPassword());

            // Build grep command based on filters
            StringBuilder command = new StringBuilder("grep -h \"\\[UFW ");

            // Add action filter
            if (!"all".equalsIgnoreCase(action)) {
                command.append(action).append("\\]");
            } else {
                command.append(".*\\]");
            }

            command.append("\" /var/log/ufw.log /var/log/ufw.log.1");

            // Add protocol filter
            if (!"all".equalsIgnoreCase(protocol)) {
                command.append(" | grep -i \"PROTO=").append(protocol).append("\"");
            }

            // Limit number of rows
            command.append(" | head -n ").append(rows);

            String fullCommand = "echo '" + computer.getPassword() + "' | sudo -S " + command.toString();
            String logsOutput = ubuntuInfo.executeCommand(sshSession, fullCommand);
            List<Map<String, String>> filteredLogs = parseUfwLogs(logsOutput);

            AtomicInteger counter = new AtomicInteger(1);
            filteredLogs.forEach(logEntry ->
                logEntry.put("id", String.valueOf(counter.getAndIncrement()))
            );

            response.put("success", true);
            response.put("ufwLogs", filteredLogs);

            redirectAttrs.addFlashAttribute("toastType", "sucess");
            redirectAttrs.addFlashAttribute("toastMessage", "Successfully filtered logs");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error filtering logs: " + e.getMessage());


            redirectAttrs.addFlashAttribute("error", "Error filtering logs: " + e.getMessage());
        } finally {
            if (sshSession != null) {
                sshSession.disconnect();

                
                redirectAttrs.addFlashAttribute("error", "SSH session disconnected");
            }
        }

        return response;
    }

    private List<Map<String, String>> parseUfwLogs(String logsOutput) {
        List<Map<String, String>> logs = new ArrayList<>();
        if (logsOutput == null || logsOutput.trim().isEmpty()) {
            return logs;
        }

        String[] lines = logsOutput.split("\n");
        for (String line : lines) {
            if (line.contains("[UFW ")) {
                Map<String, String> logEntry = new HashMap<>();

                int timestampEnd = line.indexOf(".");
                if (timestampEnd < 0) {
                    continue;
                }
                // Extract timestamp
                String fullTimestamp = line.substring(0, timestampEnd);
                System.out.println("Full Timestamp: " + fullTimestamp);
                
                logEntry.put("timestamp", fullTimestamp);

                // Extract action (BLOCK, ALLOW, etc.)
                int actionStart = line.indexOf("[UFW ");
                int actionEnd = line.indexOf("]", actionStart);
                if (actionStart > 0 && actionEnd > actionStart) {
                    String action = line.substring(actionStart + 5, actionEnd).trim();
                    logEntry.put("action", action);
                } else {
                    logEntry.put("action", "N/A");
                }

                // Extract other fields
                extractField(line, "SRC=", logEntry, "sourceIp");
                extractField(line, "DST=", logEntry, "destinationIp");
                extractField(line, "SPT=", logEntry, "sourcePort");
                extractField(line, "DPT=", logEntry, "destinationPort");
                extractField(line, "PROTO=", logEntry, "protocol");
                extractField(line, "IN=", logEntry, "interface");

                // Store the full log line
                logEntry.put("fullLog", line);

                logs.add(logEntry);
            }
        }

        return logs;
    }

    private void extractField(String line, String prefix, Map<String, String> logEntry, String field) {
        int startIndex = line.indexOf(prefix);
        if (startIndex >= 0) {
            startIndex += prefix.length();
            int endIndex = line.indexOf(" ", startIndex);
            String value;
            if (endIndex > startIndex) {
                value = line.substring(startIndex, endIndex);
            } else {
                value = line.substring(startIndex).trim();
            }

            // Convert protocol numbers to names
            if (prefix.equals("PROTO=")) {
                switch (value) {
                    case "2":
                        value = "IGMP";
                        break;
                    case "6":
                        value = "TCP";
                        break;
                    case "17":
                        value = "UDP";
                        break;
                }
            }

            logEntry.put(field, value);
        } else {
            logEntry.put(field, "N/A");
        }
    }

}
