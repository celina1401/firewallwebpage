package com.b2110941.firewallweb.controller;

import com.b2110941.firewallweb.model.LoggingUFW;
import com.b2110941.firewallweb.model.PC;
import com.b2110941.firewallweb.service.ConnectSSH;
import com.b2110941.firewallweb.service.PCService;
import com.b2110941.firewallweb.service.UbuntuInfo;
import com.b2110941.firewallweb.service.UFWService;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(LoggingController.class);

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
            logger.warn("Unauthorized access attempt to /machine/{}/logging - User not logged in", pcName);
            model.addAttribute("error", "Please login your account");
            return "redirect:/";
        }

        Optional<PC> computerOptional = pcService.findByPcNameAndOwnerUsername(pcName, ownerUsername);
        if (computerOptional.isEmpty()) {
            logger.error("Computer not found: pcName={}, ownerUsername={}", pcName, ownerUsername);
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

        Session sshSession = null;
        try {
            sshSession = connectSSH.establishSSH(
                    computer.getIpAddress(),
                    computer.getPort(),
                    computer.getPcUsername(),
                    computer.getPassword());
            logger.info("SSH connection established successfully for UFW logging: pcName={}", pcName);

            // Fetch UFW logging status
            String command = "echo '" + computer.getPassword() + "' | sudo -S ufw status verbose";
            String ufwVerbose = ubuntuInfo.executeCommand(sshSession, command);
            logger.debug("UFW Status Output: [{}]", ufwVerbose);

            String loggingStatus = "UNKNOWN";
            if (ufwVerbose != null && !ufwVerbose.trim().isEmpty()) {
                String ufwVerboseLower = ufwVerbose.toLowerCase();
                if (ufwVerboseLower.contains("logging: on")) {
                    loggingStatus = "ON";
                } else if (ufwVerboseLower.contains("logging: off")) {
                    loggingStatus = "OFF";
                }
            }
            logger.info("UFW Logging Status: {}", loggingStatus);
            model.addAttribute("loggingStatus", loggingStatus);

            // Fetch UFW logs if logging is ON
            List<Map<String, String>> ufwLogs = new ArrayList<>();
            if ("ON".equals(loggingStatus)) {
                // Check multiple log files to handle rotation
                String[] logFiles = {"/var/log/ufw.log", "/var/log/ufw.log.1"};
                String logsOutput = null;
                for (String logFile : logFiles) {
                    String logsCommand = "echo '" + computer.getPassword()
                            + "' | sudo -S tac " + logFile + " | head -n 10";
                    logsOutput = ubuntuInfo.executeCommand(sshSession, logsCommand);
                    logger.debug("Raw UFW logs output from {}: [{}]", logFile, logsOutput);

                    if (logsOutput != null && !logsOutput.trim().isEmpty()) {
                        break; // Found logs in this file, no need to check others
                    }
                }

                if (logsOutput == null || logsOutput.trim().isEmpty()) {
                    logger.warn("No log entries found in any UFW log files for pcName={}", pcName);
                    model.addAttribute("logMessage", "No log entries found in UFW log files");
                } else {
                    ufwLogs = parseUfwLogs(logsOutput);
                    if (ufwLogs.isEmpty()) {
                        logger.warn("No log entries parsed from UFW logs for pcName={}", pcName);
                        model.addAttribute("logMessage", "No log entries parsed from UFW log files");
                    }
                }
            } else {
                logger.info("UFW logging is disabled for pcName={}", pcName);
                model.addAttribute("logMessage", "UFW logging is disabled. Enable logging to view logs.");
            }
            model.addAttribute("ufwLogs", ufwLogs);

        } catch (JSchException e) {
            logger.error("SSH connection failed for pcName={}: {}", pcName, e.getMessage(), e);
            model.addAttribute("error", "SSH connection failed: " + e.getMessage());
            model.addAttribute("loggingStatus", "UNKNOWN");
        } catch (Exception e) {
            logger.error("Error retrieving UFW logs for pcName={}: {}", pcName, e.getMessage(), e);
            model.addAttribute("error", "Error retrieving UFW logs: " + e.getMessage());
            model.addAttribute("loggingStatus", "UNKNOWN");
        } finally {
            if (sshSession != null) {
                sshSession.disconnect();
                logger.info("SSH session disconnected for pcName={}", pcName);
            }
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
            @RequestParam("status") String status,
            HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        boolean enable = "on".equalsIgnoreCase(status);
        String ownerUsername = (String) session.getAttribute("username");
        if (ownerUsername == null) {
            logger.warn("Unauthorized attempt to toggle logging for pcName={} - User not logged in", pcName);
            response.put("success", false);
            response.put("message", "User not logged in");
            return response;
        }

        Optional<PC> computerOptional = pcService.findByPcNameAndOwnerUsername(pcName, ownerUsername);
        if (computerOptional.isEmpty()) {
            logger.error("Computer not found: pcName={}, ownerUsername={}", pcName, ownerUsername);
            response.put("success", false);
            response.put("message", "Computer " + pcName + " not found");
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
            logger.info("SSH connection established successfully for toggle UFW logging: pcName={}", pcName);

            // Toggle UFW logging
            String command = enable
                    ? "echo '" + computer.getPassword() + "' | sudo -S ufw logging on"
                    : "echo '" + computer.getPassword() + "' | sudo -S ufw logging off";
            String result = ubuntuInfo.executeCommand(sshSession, command);
            logger.debug("Toggle UFW logging result: {}", result);

            // Fetch updated UFW logging status and logs
            String statusCommand = "echo '" + computer.getPassword() + "' | sudo -S ufw status verbose";
            String statusOutput = ubuntuInfo.executeCommand(sshSession, statusCommand);
            logger.debug("UFW Status Output after toggle: [{}]", statusOutput);

            String loggingStatus = "UNKNOWN";
            if (statusOutput != null && !statusOutput.trim().isEmpty()) {
                String statusOutputLower = statusOutput.toLowerCase();
                if (statusOutputLower.contains("logging: on")) {
                    loggingStatus = "ON";
                } else if (statusOutputLower.contains("logging: off")) {
                    loggingStatus = "OFF";
                }
            }
            logger.info("UFW Logging Status after toggle: {}", loggingStatus);

            List<Map<String, String>> ufwLogs = new ArrayList<>();
            if ("ON".equals(loggingStatus)) {
                // Check multiple log files to handle rotation
                String[] logFiles = {"/var/log/ufw.log", "/var/log/ufw.log.1"};
                String logsOutput = null;
                for (String logFile : logFiles) {
                    String logsCommand = "echo '" + computer.getPassword()
                            + "' | sudo -S tac " + logFile + " | head -n 10";
                    logsOutput = ubuntuInfo.executeCommand(sshSession, logsCommand);
                    logger.debug("Raw UFW logs output from {}: [{}]", logFile, logsOutput);

                    if (logsOutput != null && !logsOutput.trim().isEmpty()) {
                        break; // Found logs in this file, no need to check others
                    }
                }

                if (logsOutput != null && !logsOutput.trim().isEmpty()) {
                    ufwLogs = parseUfwLogs(logsOutput);
                }
            }

            response.put("success", true);
            response.put("message", "UFW logging " + (enable ? "enabled" : "disabled"));
            response.put("loggingStatus", loggingStatus);
            response.put("ufwLogs", ufwLogs);
            if (ufwLogs.isEmpty()) {
                response.put("logMessage", "No logs found in UFW log files");
            }

        } catch (JSchException e) {
            logger.error("SSH connection failed for pcName={}: {}", pcName, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "SSH connection failed: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error toggling UFW logging for pcName={}: {}", pcName, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error toggling UFW logging: " + e.getMessage());
        } finally {
            if (sshSession != null) {
                sshSession.disconnect();
                logger.info("SSH session disconnected for pcName={}", pcName);
            }
        }
        return response;
    }

    private List<Map<String, String>> parseUfwLogs(String logsOutput) {
        List<Map<String, String>> logs = new ArrayList<>();
        if (logsOutput == null || logsOutput.trim().isEmpty()) {
            logger.warn("No logs to parse: logsOutput is empty or null");
            return logs;
        }

        String[] lines = logsOutput.split("\n");
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }

            Map<String, String> logEntry = new HashMap<>();
            logger.debug("Parsing UFW log line: {}", line);

            // Extract timestamp (e.g., "Apr 17 12:34:56")
            int timestampEnd = line.indexOf(".");
            if (timestampEnd < 0) {
                logger.warn("Invalid log line format, missing timestamp: {}", line);
                continue;
            }

            String fullTimestamp = line.substring(0, timestampEnd);
            String[] timestampParts = fullTimestamp.split("\\s+");
            String timestamp = timestampParts.length > 0 ? timestampParts[0].replace('T', ' ') : fullTimestamp;
            logEntry.put("timestamp", timestamp);

            int ufwIndex = line.indexOf("[UFW");
            if (ufwIndex < 0) {
                logger.warn("Invalid log line format, missing [UFW]: {}", line);
                continue;
            }

            int closeBracketIndex = line.indexOf("]", ufwIndex);
            if (closeBracketIndex < ufwIndex) {
                logger.warn("Invalid log line format, missing closing bracket for [UFW]: {}", line);
                continue;
            }

            String ufwPart = line.substring(ufwIndex + 1, closeBracketIndex);
            String[] ufwParts = ufwPart.split("\\s+", 2);
            String action = ufwParts.length > 1 ? ufwParts[1] : "UNDEFINED";
            logEntry.put("action", action);

            extractLogInfo(line, logEntry, "DST=", "destinationIp");
            extractLogInfo(line, logEntry, "PROTO=", "protocol");
            extractLogInfo(line, logEntry, "IN=", "interface");
            extractLogInfo(line, logEntry, "SRC=", "sourceIp");
            extractLogInfo(line, logEntry, "SPT=", "sourcePort");
            extractLogInfo(line, logEntry, "DPT=", "destinationPort");

            if (!logEntry.isEmpty()) {
                logs.add(logEntry);
            }
        }
        logger.info("Parsed {} log entries", logs.size());
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
                value = line.substring(startIndex).trim();
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
            logger.warn("Unauthorized attempt to change logging level for pcName={} - User not logged in", pcName);
            response.put("success", false);
            response.put("message", "User not logged in");
            return response;
        }

        Optional<PC> computerOptional = pcService.findByPcNameAndOwnerUsername(pcName, ownerUsername);
        if (computerOptional.isEmpty()) {
            logger.error("Computer not found: pcName={}, ownerUsername={}", pcName, ownerUsername);
            response.put("success", false);
            response.put("message", "Computer not found");
            return response;
        }

        PC computer = computerOptional.get();
        String result = ufwService.changeLoggingLevel(computer, level);
        Session sshSession = null;

        if (result.equals("success")) {
            try {
                sshSession = connectSSH.establishSSH(
                        computer.getIpAddress(),
                        computer.getPort(),
                        computer.getPcUsername(),
                        computer.getPassword());
                logger.info("SSH connection established successfully for fetching logs after changing logging level: pcName={}", pcName);

                // Fetch updated logs
                String[] logFiles = {"/var/log/ufw.log", "/var/log/ufw.log.1"};
                String logsOutput = null;
                for (String logFile : logFiles) {
                    String logsCommand = "echo '" + computer.getPassword()
                            + "' | sudo -S tac " + logFile + " | head -n 10";
                    logsOutput = ubuntuInfo.executeCommand(sshSession, logsCommand);
                    logger.debug("Raw UFW logs output from {}: [{}]", logFile, logsOutput);

                    if (logsOutput != null && !logsOutput.trim().isEmpty()) {
                        break;
                    }
                }

                List<Map<String, String>> ufwLogs = new ArrayList<>();
                if (logsOutput != null && !logsOutput.trim().isEmpty()) {
                    ufwLogs = parseUfwLogs(logsOutput);
                }

                response.put("success", true);
                response.put("message", "Logging level changed to " + level);
                response.put("ufwLogs", ufwLogs);
                if (ufwLogs.isEmpty()) {
                    response.put("logMessage", "No logs found in UFW log files");
                }
            } catch (Exception e) {
                logger.error("Error fetching updated logs for pcName={}: {}", pcName, e.getMessage(), e);
                response.put("success", false);
                response.put("message", "Error fetching updated logs: " + e.getMessage());
            } finally {
                if (sshSession != null) {
                    sshSession.disconnect();
                    logger.info("SSH session disconnected for pcName={}", pcName);
                }
            }
        } else {
            logger.error("Failed to change logging level for pcName={}: {}", pcName, result);
            response.put("success", false);
            response.put("message", result);
        }

        return response;
    }

    @GetMapping("/machine/{pcName}/log-details")
    @ResponseBody
    public Map<String, Object> getLogDetails(
            @PathVariable("pcName") String pcName,
            @RequestParam("timestamp") String timestamp,
            HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        String ownerUsername = (String) session.getAttribute("username");
        if (ownerUsername == null) {
            logger.warn("Unauthorized attempt to fetch log details for pcName={} - User not logged in", pcName);
            response.put("success", false);
            response.put("message", "User not logged in");
            return response;
        }

        try {
            logger.info("Fetching log details for pcName={}, timestamp={}", pcName, timestamp);
            LoggingUFW logDetails = ufwService.getLogDetailsByTimestamp(pcName, timestamp, ownerUsername);
            if (logDetails == null) {
                logger.warn("No log details found for pcName={}, timestamp={}", pcName, timestamp);
                response.put("success", false);
                response.put("message", "Log entry not found for timestamp: " + timestamp);
                return response;
            }

            logger.debug("Log details retrieved: {}", logDetails.getFullLog());
            response.put("success", true);
            response.put("logDetails", logDetails);
        } catch (Exception e) {
            logger.error("Error fetching log details for pcName={}, timestamp={}: {}", pcName, timestamp, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error fetching log details: " + e.getMessage());
        }

        return response;
    }
}