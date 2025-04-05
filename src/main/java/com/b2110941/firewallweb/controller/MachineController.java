package com.b2110941.firewallweb.controller;

import com.b2110941.firewallweb.model.PC;
import com.b2110941.firewallweb.model.User;
import com.b2110941.firewallweb.repository.userRepository;
import com.b2110941.firewallweb.service.ConnectSSH;
import com.b2110941.firewallweb.service.PCService;
import com.b2110941.firewallweb.service.UbuntuInfo;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
public class MachineController {

    @Autowired
    private PCService pcService;
    @Autowired
    private UbuntuInfo ubuntuInfo;
    @Autowired
    private ConnectSSH connectSSH;
    @Autowired
    private userRepository userRepository;

    @GetMapping("/machine/{pcName}")
    public String showMachinePage(
            @PathVariable("pcName") String pcName,
            Model model,
            HttpSession session,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith) {
        return redirectToMenu(pcName, "information", model, session, requestedWith);
    }

    @GetMapping("/machine/{pcName}/{menuOption}")
    public String menuNav(
            @PathVariable("pcName") String pcName,
            @PathVariable("menuOption") String menuOption,
            Model model,
            HttpSession session,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith) {
        return redirectToMenu(pcName, menuOption, model, session, requestedWith);
    }

    private String redirectToMenu(
            String pcName,
            String menuOption,
            Model model,
            HttpSession session,
            String requestedWith) {
        String ownerUsername = (String) session.getAttribute("username");
        if (ownerUsername == null) {
            model.addAttribute("error", "User not logged in");
            model.addAttribute("currentMenu", "information");
            return "redirect:/";
        }

        Optional<User> userOpt = userRepository.findByUsername(ownerUsername);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // Đưa username (hoặc toàn bộ user) vào model
            model.addAttribute("username", user.getUsername());
            // Nếu cần hiển thị fullname hoặc các thông tin khác:
            // model.addAttribute("fullname", user.getFullname());
            // model.addAttribute("user", user); // tuỳ ý
        } else {
            // Không tìm thấy user trong DB => xử lý lỗi
            model.addAttribute("error", "User not found in DB");
            return "redirect:/";
        }

        try {
            Optional<PC> computerOptional = pcService.findByPcNameAndOwnerUsername(pcName, ownerUsername);
            if (computerOptional.isPresent()) {
                model.addAttribute("computer", computerOptional.get());
                model.addAttribute("currentMenu", menuOption.toLowerCase());
            } else {
                model.addAttribute("error", "PC not found with name: " + pcName + " for this user");
                model.addAttribute("computer", null);
                model.addAttribute("currentMenu", "information");
            }
        } catch (Exception e) {
            model.addAttribute("error", "Error retrieving PC: " + e.getMessage());
            model.addAttribute("computer", null);
            model.addAttribute("currentMenu", "information");
        }

        List<PC> computers = pcService.findByOwnerUsername(ownerUsername);
        model.addAttribute("computers", computers);

        if ("XMLHttpRequest".equals(requestedWith)) {
            // Trả về fragment trong machine.html
            return "machine :: section(menuOption='" + menuOption.toLowerCase() + "')";
        }
        return "machine";
    }

    // 📌 API lấy thông tin Ubuntu qua SSH
    @GetMapping("/machine/{pcName}/information")
    public String infoUbuntu(
            @PathVariable("pcName") String pcName,
            Model model,
            HttpSession session,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith) {
        String ownerUsername = (String) session.getAttribute("username");
        if (ownerUsername == null) {
            model.addAttribute("error", "Please login your account!");
            return "redirect:/";
        }

        Optional<PC> computerOptional = pcService.findByPcNameAndOwnerUsername(pcName, ownerUsername);
        if (computerOptional.isEmpty()) {
            model.addAttribute("error", "Computer " + pcName + " not found");
            model.addAttribute("currentMenu", "information");
            return "machine";
        }

        PC computer = computerOptional.get();
        model.addAttribute("computer", computer);
        model.addAttribute("currentMenu", "information");

        try {
            Session sshSession = connectSSH.establishSSH(
                    computer.getIpAddress(),
                    computer.getPort(),
                    computer.getPcUsername(),
                    computer.getPassword()
            );            
            System.out.println("Ket noi thanh cong line 139");

            Map<String, String> systemInfo = new HashMap<>();
            systemInfo.put("CPU", ubuntuInfo.executeCommand(sshSession, "lscpu | grep 'Model name' | awk -F ':' '{print $2}'").trim());
            systemInfo.put("RAM", ubuntuInfo.executeCommand(sshSession, "free -h | grep 'Mem:' | awk '{print $2}'"));
            systemInfo.put("Ubuntu Version", ubuntuInfo.executeCommand(sshSession, "lsb_release -d | awk -F ':' '{print $2}'"));
            model.addAttribute("systemInfo", systemInfo);
        } catch (JSchException e) {
            model.addAttribute("error", "Không thể kết nối SSH tới " + pcName + ": " + e.getMessage());
        } catch (Exception e) {
            model.addAttribute("error", "Lỗi khi lấy thông tin hệ thống: " + e.getMessage());
        }

        if ("XMLHttpRequest".equals(requestedWith)) {
            return "machine :: section(menuOption='information')";
        }
        return "machine";
    }

    // 📌 API lấy danh sách quy tắc UFW và trạng thái firewall qua SSH
    @GetMapping("/machine/{pcName}/rule")
    public String getFirewallRules(
            @PathVariable("pcName") String pcName,
            Model model,
            HttpSession session,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith) {
        String ownerUsername = (String) session.getAttribute("username");
        if (ownerUsername == null) {
            model.addAttribute("error", "Please login your account!");
            return "redirect:/";
        }

        Optional<PC> computerOptional = pcService.findByPcNameAndOwnerUsername(pcName, ownerUsername);
        if (computerOptional.isEmpty()) {
            model.addAttribute("error", "Computer " + pcName + " not found");
            model.addAttribute("currentMenu", "rule");
            return "machine";
        }

        PC computer = computerOptional.get();
        model.addAttribute("computer", computer);
        model.addAttribute("currentMenu", "rule");

        try {
            Session sshSession = connectSSH.establishSSH(
                    computer.getIpAddress(),
                    computer.getPort(),
                    computer.getPcUsername(),
                    computer.getPassword()
            );
            System.out.println("Kết nối SSH thành công");

            String command = "echo '" + computer.getPassword() + "' | sudo -S ufw status";
            String ufwOutput = ubuntuInfo.executeCommand(sshSession, command);
            System.out.println("UFW Output: " + ufwOutput);

            // Set firewall status
            if (ufwOutput != null && ufwOutput.contains("Status: active")) {
                model.addAttribute("firewallStatus", "active");
            } else {
                model.addAttribute("firewallStatus", "inactive");
            }

            // Parse and set firewall rules
            if (ufwOutput == null || ufwOutput.trim().isEmpty()) {
                model.addAttribute("error", "Không thể lấy trạng thái UFW hoặc UFW chưa được kích hoạt.");
            } else {
                List<Map<String, String>> firewallRules = parseUfwOutput(ufwOutput);
                model.addAttribute("firewallRules", firewallRules);
            }
        } catch (JSchException e) {
            model.addAttribute("error", "Không thể kết nối SSH tới " + pcName + ": " + e.getMessage());
            model.addAttribute("firewallStatus", "inactive");
        } catch (Exception e) {
            model.addAttribute("error", "Lỗi khi lấy danh sách quy tắc: " + e.getMessage());
            model.addAttribute("firewallStatus", "inactive");
        }

        if ("XMLHttpRequest".equals(requestedWith)) {
            return "machine :: section(menuOption='rule')";
        }
        return "machine";
    }

    private List<Map<String, String>> parseUfwOutput(String ufwOutput) {
        List<Map<String, String>> firewallRules = new ArrayList<>();

        // Split the output into lines
        String[] lines = ufwOutput.split("\n");
        boolean rulesStarted = false;
        int id = 1; // For assigning rule IDs

        for (String line : lines) {
            // Skip header lines until we reach the actual rules
            if (line.startsWith("Status:") || line.trim().isEmpty() || line.contains("To") || line.contains("--")) {
                continue;
            }

            // Start processing rules after the header
            rulesStarted = true;

            // Split the line into columns (To, Action, From)
            String[] columns = line.trim().split("\\s{2,}");
            if (columns.length < 3) {
                continue; // Skip malformed lines
            }

            // Extract fields
            String to = columns[0].trim(); // e.g., "22/tcp" or "22/tcp (v6)"
            String action = columns[1].trim(); // e.g., "ALLOW IN"
            String from = columns[2].trim(); // e.g., "Anywhere" or "203.0.113.103"

            // Parse the "To" field to get port and protocol
            String port = "";
            String protocol = "";
            String ipVersion = "IPv4"; // Default to IPv4 unless specified as (v6)

            if (to.contains("(v6)")) {
                ipVersion = "IPv6";
                to = to.replace("(v6)", "").trim();
            }

            if (to.contains("/")) {
                String[] toParts = to.split("/");
                port = toParts[0]; // e.g., "22"
                protocol = toParts[1]; // e.g., "tcp"
            } else {
                port = to; // If no protocol specified, assume it's a port
                protocol = "any"; // Default protocol
            }

            // Parse the "From" field (source IP)
            String sourceIp = from;
            if (from.equals("Anywhere") || from.equals("Anywhere (v6)")) {
                sourceIp = (ipVersion.equals("IPv6")) ? "::/0" : "0.0.0.0/0";
            }

            // Destination IP (UFW typically applies to the local machine, so we can set it as "any" or the machine's IP)
            String destinationIp = "0.0.0.0/0"; // Adjust this if you know the machine's IP

            // Create a rule map
            Map<String, String> rule = new HashMap<>();
            rule.put("id", String.valueOf(id++));
            rule.put("sourceIp", sourceIp);
            rule.put("destinationIp", destinationIp);
            rule.put("port", port);
            rule.put("protocol", protocol);
            rule.put("action", action);

            firewallRules.add(rule);
        }
        return firewallRules;
    }

    @PostMapping("/machine/{pcName}/toggle-firewall")
    @ResponseBody
    public Map<String, Object> toggleFirewall(
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
                    computer.getPassword()
            );
            String command = enable
                    ? "echo '" + computer.getPassword() + "' | sudo -S sh -c \"echo y | ufw enable\""
                    : "echo '" + computer.getPassword() + "' | sudo -S ufw disable";
            String result = ubuntuInfo.executeCommand(sshSession, command);
            System.out.println("Toggle result: " + result);

            // Lấy trạng thái và quy tắc mới sau khi bật/tắt
            String statusCommand = "echo '" + computer.getPassword() + "' | sudo -S ufw status";
            String statusOutput = ubuntuInfo.executeCommand(sshSession, statusCommand);
            boolean isActive = statusOutput != null && statusOutput.contains("Status: active");

            response.put("success", true);
            response.put("message", "Firewall " + (enable ? "enabled" : "disabled"));
            response.put("firewallStatus", isActive ? "active" : "inactive");
            response.put("firewallRules", isActive ? parseUfwOutput(statusOutput) : Collections.emptyList());
        } catch (JSchException e) {
            response.put("success", false);
            response.put("message", "SSH connection failed: " + e.getMessage());
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error toggling firewall: " + e.getMessage());
            e.printStackTrace(); // Print full stack trace for debugging
        }
        return response;
    }

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
        model.addAttribute("computer", computer);
        model.addAttribute("currentMenu", "logging");

        try {
            Session sshSession = connectSSH.establishSSH(
                    computer.getIpAddress(),
                    computer.getPort(),
                    computer.getPcUsername(),
                    computer.getPassword()
            );
            System.out.println("SSH connection established successfully");

            // Fetch logging status
            String command = "echo '" + computer.getPassword() + "' | sudo -S ufw status verbose";
            String ufwVerbose = ubuntuInfo.executeCommand(sshSession, command);
            System.out.println("UFW Status Output in getFirewallLogging: [" + ufwVerbose + "]");

            // Set logging status
            String loggingStatus = "UNKNOWN";
            if (ufwVerbose != null && !ufwVerbose.trim().isEmpty()) {
                String ufwVerboseLower = ufwVerbose.toLowerCase();
                if (ufwVerboseLower.contains("logging: on")) {
                    loggingStatus = "ON";
                    model.addAttribute("loggingStatus", "ON");
                } else if (ufwVerboseLower.contains("logging: off")) {
                    loggingStatus = "OFF";
                    model.addAttribute("loggingStatus", "OFF");
                } else {
                    System.out.println("Unexpected UFW status format. Full output: [" + ufwVerbose + "]");
                }
            } else {
                System.out.println("UFW status output is null or empty. Possible SSH or sudo issue.");
            }
            System.out.println("Logging Status in getFirewallLogging: " + loggingStatus);
            model.addAttribute("loggingStatus", loggingStatus);

            // Fetch logs if logging is ON
            List<Map<String, String>> ufwLogs = new ArrayList<>();
            if ("ON".equals(loggingStatus)) {
                String logsCommand = "echo '" + computer.getPassword() + "' | sudo -S cat /var/log/ufw.log.1 | tail -n 10";
                String logsOutput = ubuntuInfo.executeCommand(sshSession, logsCommand);
                System.out.println("Raw logs output in getFirewallLogging: [" + logsOutput + "]");

                if (logsOutput == null || logsOutput.trim().isEmpty()) {
                    model.addAttribute("logMessage", "No log entries found in /var/log/ufw.log");
                } else {
                    ufwLogs = parseUfwLogs(logsOutput);
                    if (ufwLogs.isEmpty()) {
                        model.addAttribute("logMessage", "No log entries found in /var/log/ufw.log");
                    }
                }
            } else {
                model.addAttribute("logMessage", "Logging is disabled. Enable logging to view logs.");
            }
            model.addAttribute("ufwLogs", ufwLogs);

        } catch (JSchException e) {
            model.addAttribute("error", "SSH connection failed: " + e.getMessage());
            model.addAttribute("loggingStatus", "UNKNOWN");
        } catch (Exception e) {
            model.addAttribute("error", "Error retrieving firewall logs: " + e.getMessage());
            model.addAttribute("loggingStatus", "UNKNOWN");
            e.printStackTrace();
        }

        if ("XMLHttpRequest".equals(requestedWith)) {
            return "machine :: section(menuOption='logging')";
        }
        return "machine";
    }

// Toggle logging status
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
                    computer.getPassword()
            );
            System.out.println("SSH connection established successfully");

            // Test sudo privileges
            String testSudoCommand = "echo '" + computer.getPassword() + "' | sudo -S whoami";
            String testSudoOutput = ubuntuInfo.executeCommand(sshSession, testSudoCommand);
            System.out.println("Test Sudo Command Output: " + testSudoOutput);
            if (!"root".equals(testSudoOutput.trim())) {
                throw new RuntimeException("Sudo privileges not granted. Expected 'root', got: " + testSudoOutput);
            }

            // Toggle logging
            String command = enable
                    ? "echo '" + computer.getPassword() + "' | sudo -S ufw logging on"
                    : "echo '" + computer.getPassword() + "' | sudo -S ufw logging off";
            String result = ubuntuInfo.executeCommand(sshSession, command);
            System.out.println("Toggle logging result: " + result);

            // Fetch updated logging status and logs
            String statusCommand = "echo '" + computer.getPassword() + "' | sudo -S ufw status verbose";
            String statusOutput = ubuntuInfo.executeCommand(sshSession, statusCommand);
            System.out.println("UFW Status Output in toggleLogging: [" + statusOutput + "]");

            String loggingStatus = "UNKNOWN";
            if (statusOutput != null && !statusOutput.trim().isEmpty()) {
                String statusOutputLower = statusOutput.toLowerCase();
                if (statusOutputLower.contains("logging: on")) {
                    loggingStatus = "ON";
                } else if (statusOutputLower.contains("logging: off")) {
                    loggingStatus = "OFF";
                } else {
                    System.out.println("Unexpected UFW status format in toggleLogging. Full output: [" + statusOutput + "]");
                }
            } else {
                System.out.println("UFW status output is null or empty in toggleLogging. Possible SSH or sudo issue.");
            }
            System.out.println("Logging Status in toggleLogging: " + loggingStatus);

            // Fetch logs if logging is ON
            List<Map<String, String>> ufwLogs = new ArrayList<>();
            if ("ON".equals(loggingStatus)) {
                String logsCommand = "echo '" + computer.getPassword() + "' | sudo -S cat /var/log/ufw.log | tail -n 10";
                String logsOutput = ubuntuInfo.executeCommand(sshSession, logsCommand);
                System.out.println("Raw logs output in toggleLogging: [" + logsOutput + "]");

                if (logsOutput != null && !logsOutput.trim().isEmpty()) {
                    ufwLogs = parseUfwLogs(logsOutput);
                }
            }

            response.put("success", true);
            response.put("message", "Logging " + (enable ? "enabled" : "disabled"));
            response.put("loggingStatus", loggingStatus);
            response.put("ufwLogs", ufwLogs);
            if (ufwLogs.isEmpty()) {
                response.put("logMessage", "No logs found in /var/log/ufw.log");
            }

        } catch (JSchException e) {
            response.put("success", false);
            response.put("message", "SSH connection failed: " + e.getMessage());
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error toggling logging: " + e.getMessage());
            e.printStackTrace();
        }
        return response;
    }

    private List<Map<String, String>> parseUfwLogs(String logsOutput) {
        List<Map<String, String>> logs = new ArrayList<>();
        if (logsOutput == null || logsOutput.trim().isEmpty()) {
            return logs;
        }
// Giả sử logsOutput chứa toàn bộ văn bản log
        String[] lines = logsOutput.split("\n");

        for (String line : lines) {
            Map<String, String> logEntry = new HashMap<>();
            System.out.println("Đang xử lý dòng: " + line); // Debug: in ra dòng đang xử lý

            // Xử lý timestamp và hostname
            int timestampEnd = line.indexOf(".");
            if (timestampEnd > 0) {
                String fullTimestamp = line.substring(0, timestampEnd);

                // Extract only date and time (format: Dec 29 01:18:53)
                String[] timestampParts = fullTimestamp.split("\\s+");
                if (timestampParts.length > 0) {
                    String timestamp = timestampParts[0].replace('T', ' ');
                    logEntry.put("timestamp", timestamp);
                } else {
                    logEntry.put("timestamp", fullTimestamp); // Fallback to full timestamp if parsing fails
                }
                
                // Tìm phần UFW và ACTION
                int ufwIndex = line.indexOf("[UFW");
                if (ufwIndex > 0) {
                    int closeBracketIndex = line.indexOf("]", ufwIndex);
                    if (closeBracketIndex > ufwIndex) {
                        // Lấy toàn bộ phần từ [UFW đến ]
                        String ufwPart = line.substring(ufwIndex + 1, closeBracketIndex);
                        System.out.println("UFW part: " + ufwPart); // Debug

                        // Tách phần UFW để lấy ACTION
                        String[] ufwParts = ufwPart.split("\\s+", 2);
                        if (ufwParts.length > 1) {
                            String action = ufwParts[1];
                            logEntry.put("action", action);
                            System.out.println("Đã tìm thấy action: " + action); // Debug
                        } else {
                            logEntry.put("action", "UNDEFINED");
                        }
                    }
                }

                // Xử lý các thông tin khác
                extractLogInfo(line, logEntry, "IN=", "interface");
                extractLogInfo(line, logEntry, "SRC=", "sourceIp");
                extractLogInfo(line, logEntry, "SPT=", "sourcePort");
                extractLogInfo(line, logEntry, "DST=", "destinationIp");
                extractLogInfo(line, logEntry, "DPT=", "destinationPort");
                extractLogInfo(line, logEntry, "PROTO=", "protocol");

                logs.add(logEntry);
            }
        }
        // Phương thức hỗ trợ trích xuất thông tin

        return logs;
    }

    private static void extractLogInfo(String line, Map<String, String> logEntry, String prefix, String key) {
        int startIndex = line.indexOf(prefix);
        String value;
        if (startIndex >= 0) {
            startIndex += prefix.length();
            int endIndex = line.indexOf(" ", startIndex);
            if (endIndex > startIndex) {
                value = line.substring(startIndex, endIndex);
                logEntry.put(key, value);
            } else {
                // Nếu đây là thành phần cuối cùng của dòng
                value = line.substring(startIndex);
                logEntry.put(key, value);
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

}
