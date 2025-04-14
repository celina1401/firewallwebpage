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
            
            model.addAttribute("username", user.getUsername());
            
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
                    computer.getPassword());
            System.out.println("Ket noi thanh cong line 139");

            Map<String, String> systemInfo = new HashMap<>();
            systemInfo.put("CPU", ubuntuInfo
                    .executeCommand(sshSession, "lscpu | grep 'Model name' | awk -F ':' '{print $2}'").trim());
            systemInfo.put("RAM", ubuntuInfo.executeCommand(sshSession, "free -h | grep 'Mem:' | awk '{print $2}'"));
            systemInfo.put("Ubuntu Version",
                    ubuntuInfo.executeCommand(sshSession, "lsb_release -d | awk -F ':' '{print $2}'"));
            model.addAttribute("systemInfo", systemInfo);

            String statusCommand = "echo '" + computer.getPassword() + "' | sudo -S systemctl is-active rsyslog";
            String rsyslogStatus = ubuntuInfo.executeCommand(sshSession, statusCommand).trim();
            System.out.println(rsyslogStatus);
            String systemLoggingStatus = "UNKNOWN";
            if ("active".equals(rsyslogStatus)) {
                systemLoggingStatus = "ON";
            } else if ("inactive".equals(rsyslogStatus)) {
                systemLoggingStatus = "OFF";
            }
            model.addAttribute("systemLoggingStatus", systemLoggingStatus);

            List<Map<String, String>> systemLogs = new ArrayList<>();
            if ("ON".equals(systemLoggingStatus)) {
                String logsCommand = "echo '" + computer.getPassword() + "' | sudo -S tac /var/log/syslog | head -n 10";
                String logsOutput = ubuntuInfo.executeCommand(sshSession, logsCommand);
                if (logsOutput == null || logsOutput.trim().isEmpty()) {
                    model.addAttribute("systemLogMessage", "No log entries found in /var/log/syslog");
                } else {
                    systemLogs = parseSystemLogs(logsOutput);
                    if (systemLogs.isEmpty()) {
                        model.addAttribute("systemLogMessage", "No log entries parsed from /var/log/syslog");
                    }
                }
            }
            model.addAttribute("systemLogs", systemLogs);
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

    @PostMapping("/machine/{pcName}/toggle-system-logging")
    @ResponseBody
    public Map<String, Object> toggleSystemLogging(
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
            System.out.println("SSH connection established for toggle system logging");

            // Toggle rsyslog service
            String enableCommand = enable
                    ? "echo '" + computer.getPassword() + "' | sudo -S systemctl enable rsyslog"
                    : "echo '" + computer.getPassword() + "' | sudo -S systemctl disable rsyslog";
            String startStopCommand = enable
                    ? "echo '" + computer.getPassword() + "' | sudo -S systemctl start rsyslog"
                    : "echo '" + computer.getPassword() + "' | sudo -S systemctl stop rsyslog";

            // Execute enable/disable
            String enableResult = ubuntuInfo.executeCommand(sshSession, enableCommand);
            System.out.println("Enable/Disable command: " + enableCommand);
            System.out.println("Enable/Disable result: [" + enableResult + "]");
            if (enableResult != null && (enableResult.toLowerCase().contains("failed")
                    || enableResult.toLowerCase().contains("error"))) {
                response.put("success", false);
                response.put("message", "Failed to enable/disable rsyslog: " + enableResult);
                return response;
            }

            // Execute start/stop
            String startStopResult = ubuntuInfo.executeCommand(sshSession, startStopCommand);
            System.out.println("Start/Stop command: " + startStopCommand);
            System.out.println("Start/Stop result: [" + startStopResult + "]");
            if (startStopResult != null && (startStopResult.toLowerCase().contains("failed")
                    || startStopResult.toLowerCase().contains("error"))) {
                response.put("success", false);
                response.put("message", "Failed to start/stop rsyslog: " + startStopResult);
                return response;
            }

            // Clear logs when disabling
            if (!enable) {
                String clearLogCommand = "echo '" + computer.getPassword()
                        + "' | sudo -S truncate -s 0 /var/log/syslog";
                String clearResult = ubuntuInfo.executeCommand(sshSession, clearLogCommand);
                System.out.println("Clear log command: " + clearLogCommand);
                System.out.println("Clear log result: [" + clearResult + "]");
            }

            // Fetch updated rsyslog status
            String statusCommand = "echo '" + computer.getPassword() + "' | sudo -S systemctl is-active rsyslog";
            String rsyslogStatus = ubuntuInfo.executeCommand(sshSession, statusCommand).trim().toLowerCase();
            System.out.println("Raw rsyslog status after toggle: [" + rsyslogStatus + "]");

            String systemLoggingStatus = "UNKNOWN";
            if ("active".equals(rsyslogStatus)) {
                systemLoggingStatus = "ON";
            } else if ("inactive".equals(rsyslogStatus)) {
                systemLoggingStatus = "OFF";
            } else {
                response.put("success", false);
                response.put("message", "Unexpected rsyslog status after toggle: " + rsyslogStatus);
                return response;
            }

            List<Map<String, String>> systemLogs = new ArrayList<>();
            if ("ON".equals(systemLoggingStatus)) {
                String logsCommand = "echo '" + computer.getPassword() + "' | sudo -S cat /var/log/syslog | tail -n 10";
                String logsOutput = ubuntuInfo.executeCommand(sshSession, logsCommand);
                System.out.println("Raw system logs output after toggle: [" + logsOutput + "]");

                if (logsOutput != null && !logsOutput.trim().isEmpty()) {
                    systemLogs = parseSystemLogs(logsOutput);
                }
            }

            response.put("success", true);
            response.put("message", "System logging " + (enable ? "enabled" : "disabled"));
            response.put("systemLoggingStatus", systemLoggingStatus);
            response.put("systemLogs", systemLogs);
            if (systemLogs.isEmpty()) {
                response.put("systemLogMessage", "No logs found in /var/log/syslog");
            }

        } catch (JSchException e) {
            response.put("success", false);
            response.put("message", "SSH connection failed: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error toggling system logging: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("Toggle response: " + response);
        return response;
    }

    private List<Map<String, String>> parseSystemLogs(String logsOutput) {
        List<Map<String, String>> logs = new ArrayList<>();
        if (logsOutput == null || logsOutput.trim().isEmpty()) {
            return logs;
        }

        String[] lines = logsOutput.split("\n");
        for (String line : lines) {
            Map<String, String> logEntry = new HashMap<>();
            // System.out.println("Parsing system log line: " + line);

            // Tìm vị trí kết thúc của timestamp (dấu chấm đầu tiên trước mili giây)
            int timestampEnd = line.indexOf(".");
            if (timestampEnd > 0) {
                // Lấy phần timestamp đầy đủ (bao gồm múi giờ)
                String fullTimestamp = line.substring(0, timestampEnd);
                // Thay thế 'T' bằng khoảng trắng để định dạng dễ đọc
                String timestamp = fullTimestamp.replace('T', ' ');
                logEntry.put("timestamp", timestamp);

                // Tách phần còn lại của dòng log sau timestamp và múi giờ
                int firstSpaceAfterTimestamp = line.indexOf(" ", timestampEnd);
                if (firstSpaceAfterTimestamp > 0) {
                    // Lấy hostname (phần giữa timestamp và service)
                    int hostnameEnd = line.indexOf(" ", firstSpaceAfterTimestamp + 1);
                    if (hostnameEnd > firstSpaceAfterTimestamp) {
                        String hostname = line.substring(firstSpaceAfterTimestamp + 1, hostnameEnd);
                        logEntry.put("hostname", hostname);

                        // Lấy service (phần giữa hostname và dấu hai chấm)
                        int serviceEnd = line.indexOf(":", hostnameEnd);
                        if (serviceEnd > hostnameEnd) {
                            String service = line.substring(hostnameEnd + 1, serviceEnd);
                            logEntry.put("service", service);

                            // Lấy message (phần còn lại sau dấu hai chấm)
                            String message = line.substring(serviceEnd + 1).trim();
                            logEntry.put("message", message);
                        } else {
                            // Nếu không có dấu hai chấm, message có thể rỗng
                            logEntry.put("service", line.substring(hostnameEnd + 1).trim());
                            logEntry.put("message", "");
                        }
                    }
                }

                // Thêm logEntry vào danh sách logs
                logs.add(logEntry);
            }
        }
        return logs;
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
                    computer.getPassword());
            // System.out.println("Kết nối SSH thành công");

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
                // sourceIp = (ipVersion.equals("IPv6")) ? "::/0" : "0.0.0.0/0";
            }

            // Destination IP (UFW typically applies to the local machine, so we can set it
            // as "any" or the machine's IP)
            String destinationIp = ""; // Adjust this if you know the machine's IP

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
                    computer.getPassword());
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
}
