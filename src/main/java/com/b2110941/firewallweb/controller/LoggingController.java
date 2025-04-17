package com.b2110941.firewallweb.controller;

import com.b2110941.firewallweb.model.PC;
import com.b2110941.firewallweb.service.ConnectSSH;
import com.b2110941.firewallweb.service.PCService;
import com.b2110941.firewallweb.service.UbuntuInfo;
import com.b2110941.firewallweb.service.UFWService;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
public class LoggingController {

    @Autowired
    private PCService pcService;
    @Autowired
    private UbuntuInfo ubuntuInfo;
    @Autowired
    private ConnectSSH connectSSH;
    @Autowired
    private UFWService ufwService;

    // UFW Logging
    @GetMapping("/machine/{pcName}/logging")
    public String getFirewallLogging(
            @PathVariable("pcName") String pcName,
            Model model,
            HttpSession session,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith) {
        String ownerUsername = (String) session.getAttribute("username");
        if (ownerUsername == null) {
            model.addAttribute("error", "Please login your account");
            return "redirect:/";
        }

        Optional<PC> computerOptional = pcService.findByPcNameAndOwnerUsername(pcName, ownerUsername);
        if (computerOptional.isEmpty()) {
            model.addAttribute("error", "Computer " + pcName + " not found");
            model.addAttribute("currentMenu", "logging");
            return "machine";
        }

        PC computer = computerOptional.get();
        // Add current logging level
        String currentLevel = ufwService.getLoggingLevel(computer);
        model.addAttribute("currentLoggingLevel", currentLevel);
        
        model.addAttribute("computer", computer);
        model.addAttribute("currentMenu", "logging");

        try {
            Session sshSession = connectSSH.establishSSH(
                    computer.getIpAddress(),
                    computer.getPort(),
                    computer.getPcUsername(),
                    computer.getPassword());
            System.out.println("SSH connection established successfully for UFW logging");

            // Fetch UFW logging status
            String command = "echo '" + computer.getPassword() + "' | sudo -S ufw status verbose";
            String ufwVerbose = ubuntuInfo.executeCommand(sshSession, command);
            System.out.println("UFW Status Output: [" + ufwVerbose + "]");

            String loggingStatus = "UNKNOWN";
            if (ufwVerbose != null && !ufwVerbose.trim().isEmpty()) {
                String ufwVerboseLower = ufwVerbose.toLowerCase();
                if (ufwVerboseLower.contains("logging: on")) {
                    loggingStatus = "ON";
                } else if (ufwVerboseLower.contains("logging: off")) {
                    loggingStatus = "OFF";
                }
            }
            System.out.println("UFW Logging Status: " + loggingStatus);
            model.addAttribute("loggingStatus", loggingStatus);

            // Fetch UFW logs if logging is ON
            List<Map<String, String>> ufwLogs = new ArrayList<>();
            if ("ON".equals(loggingStatus)) {
                String logsCommand = "echo '" + computer.getPassword()
                        + "' | sudo -S tac /var/log/ufw.log.1 | head -n 10";
                String logsOutput = ubuntuInfo.executeCommand(sshSession, logsCommand);
                System.out.println("Raw UFW logs output: [" + logsOutput + "]");

                if (logsOutput == null || logsOutput.trim().isEmpty()) {
                    model.addAttribute("logMessage", "No log entries found in /var/log/ufw.log");
                } else {
                    ufwLogs = parseUfwLogs(logsOutput);
                    if (ufwLogs.isEmpty()) {
                        model.addAttribute("logMessage", "No log entries parsed from /var/log/ufw.log");
                    }
                }
            } else {
                model.addAttribute("logMessage", "UFW logging is disabled. Enable logging to view logs.");
            }
            model.addAttribute("ufwLogs", ufwLogs);

        } catch (JSchException e) {
            model.addAttribute("error", "SSH connection failed: " + e.getMessage());
            model.addAttribute("loggingStatus", "UNKNOWN");
        } catch (Exception e) {
            model.addAttribute("error", "Error retrieving UFW logs: " + e.getMessage());
            model.addAttribute("loggingStatus", "UNKNOWN");
            e.printStackTrace();
        }

        if ("XMLHttpRequest".equals(requestedWith)) {
            return "machine :: section(menuOption='logging')";
        }
        return "machine";
    }

    @PostMapping("/machine/{pcName}/toggle-logging")
    @ResponseBody
    public Map<String, Object> toggleLogging(
            @PathVariable("pcName") String pcName,
            @RequestParam("enable") boolean enable,
            HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        String ownerUsername = (String) session.getAttribute("username");
        if (ownerUsername == null) {
            response.put("success", false);
            response.put("message", "User not logged in");
            return response;
        }

        try {
            Optional<PC> computerOptional = pcService.findByPcNameAndOwnerUsername(pcName, ownerUsername);
            if (computerOptional.isEmpty()) {
                response.put("success", false);
                response.put("message", "Computer " + pcName + " not found");
                return response;
            }

            PC computer = computerOptional.get();
            Session sshSession = connectSSH.establishSSH(
                    computer.getIpAddress(),
                    computer.getPort(),
                    computer.getPcUsername(),
                    computer.getPassword());
            System.out.println("SSH connection established successfully for toggle UFW logging");

            // Toggle UFW logging
            String command = enable
                    ? "echo '" + computer.getPassword() + "' | sudo -S ufw logging on"
                    : "echo '" + computer.getPassword() + "' | sudo -S ufw logging off";
            String result = ubuntuInfo.executeCommand(sshSession, command);
            System.out.println("Toggle UFW logging result: " + result);

            // Fetch updated UFW logging status and logs
            String statusCommand = "echo '" + computer.getPassword() + "' | sudo -S ufw status verbose";
            String statusOutput = ubuntuInfo.executeCommand(sshSession, statusCommand);
            System.out.println("UFW Status Output after toggle: [" + statusOutput + "]");

            String loggingStatus = "UNKNOWN";
            if (statusOutput != null && !statusOutput.trim().isEmpty()) {
                String statusOutputLower = statusOutput.toLowerCase();
                if (statusOutputLower.contains("logging: on")) {
                    loggingStatus = "ON";
                } else if (statusOutputLower.contains("logging: off")) {
                    loggingStatus = "OFF";
                }
            }
            System.out.println("UFW Logging Status after toggle: " + loggingStatus);

            List<Map<String, String>> ufwLogs = new ArrayList<>();
            if ("ON".equals(loggingStatus)) {
                String logsCommand = "echo '" + computer.getPassword()
                        + "' | sudo -S cat /var/log/ufw.log.1 | tail -n 10";
                String logsOutput = ubuntuInfo.executeCommand(sshSession, logsCommand);
                System.out.println("Raw UFW logs output after toggle: [" + logsOutput + "]");

                if (logsOutput != null && !logsOutput.trim().isEmpty()) {
                    ufwLogs = parseUfwLogs(logsOutput);
                }
            }

            response.put("success", true);
            response.put("message", "UFW logging " + (enable ? "enabled" : "disabled"));
            response.put("loggingStatus", loggingStatus);
            response.put("ufwLogs", ufwLogs);
            if (ufwLogs.isEmpty()) {
                response.put("logMessage", "No logs found in /var/log/ufw.log.1");
            }

        } catch (JSchException e) {
            response.put("success", false);
            response.put("message", "SSH connection failed: " + e.getMessage());
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error toggling UFW logging: " + e.getMessage());
            e.printStackTrace();
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
            Map<String, String> logEntry = new HashMap<>();
            System.out.println("Parsing UFW log line: " + line);

            int timestampEnd = line.indexOf(".");
            if (timestampEnd > 0) {
                String fullTimestamp = line.substring(0, timestampEnd);
                String[] timestampParts = fullTimestamp.split("\\s+");
                String timestamp = timestampParts.length > 0 ? timestampParts[0].replace('T', ' ') : fullTimestamp;
                logEntry.put("timestamp", timestamp);

                int ufwIndex = line.indexOf("[UFW");
                if (ufwIndex > 0) {
                    int closeBracketIndex = line.indexOf("]", ufwIndex);
                    if (closeBracketIndex > ufwIndex) {
                        String ufwPart = line.substring(ufwIndex + 1, closeBracketIndex);
                        String[] ufwParts = ufwPart.split("\\s+", 2);
                        if (ufwParts.length > 1) {
                            logEntry.put("action", ufwParts[1]);
                        } else {
                            logEntry.put("action", "UNDEFINED");
                        }
                    }
                }

                extractLogInfo(line, logEntry, "IN=", "interface");
                extractLogInfo(line, logEntry, "SRC=", "sourceIp");
                extractLogInfo(line, logEntry, "SPT=", "sourcePort");
                extractLogInfo(line, logEntry, "DST=", "destinationIp");
                extractLogInfo(line, logEntry, "DPT=", "destinationPort");
                extractLogInfo(line, logEntry, "PROTO=", "protocol");

                logs.add(logEntry);
            }
        }
        return logs;
    }

    private static void extractLogInfo(String line, Map<String, String> logEntry, String prefix, String key) {
        int startIndex = line.indexOf(prefix);
        if (startIndex >= 0) {
            startIndex += prefix.length();
            int endIndex = line.indexOf(" ", startIndex);
            String value;
            if (endIndex > startIndex) {
                value = line.substring(startIndex, endIndex);
            } else {
                value = line.substring(startIndex);
            }

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
            logEntry.put(key, value);
        }
    }

    @PostMapping("/machine/{pcName}/logging-level")
    @ResponseBody
    public Map<String, Object> changeLoggingLevel(
            @PathVariable("pcName") String pcName,
            @RequestParam("level") String level,
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
        String result = ufwService.changeLoggingLevel(computer, level);

        if (result.equals("success")) {
            try {
                Session sshSession = connectSSH.establishSSH(
                        computer.getIpAddress(),
                        computer.getPort(),
                        computer.getPcUsername(),
                        computer.getPassword());

                // Fetch updated logs
                String logsCommand = "echo '" + computer.getPassword()
                        + "' | sudo -S tac /var/log/ufw.log.1 | head -n 10";
                String logsOutput = ubuntuInfo.executeCommand(sshSession, logsCommand);
                
                List<Map<String, String>> ufwLogs = new ArrayList<>();
                if (logsOutput != null && !logsOutput.trim().isEmpty()) {
                    ufwLogs = parseUfwLogs(logsOutput);
                }

                response.put("success", true);
                response.put("message", "Logging level changed to " + level);
                response.put("ufwLogs", ufwLogs);
                if (ufwLogs.isEmpty()) {
                    response.put("logMessage", "No logs found in /var/log/ufw.log.1");
                }
            } catch (Exception e) {
                response.put("success", false);
                response.put("message", "Error fetching updated logs: " + e.getMessage());
            }
        } else {
            response.put("success", false);
            response.put("message", result);
        }

        return response;
    }
}
