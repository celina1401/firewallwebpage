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
            System.out.println("Ket noi thanh cong line 126");

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
    public String getFirewallLogging(@PathVariable("pcName") String pcName,
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
            model.addAttribute("error", "Computer " + pcName + " notfound");
            model.addAttribute("currentMenu", "logging");
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
            String command = "echo '" + computer.getPassword() + "' |  sudo -S ufw status verbose";
            String ufwVerbose = ubuntuInfo.executeCommand(sshSession, command);
            System.out.println(ufwVerbose);

            String loggingStatus = "unknown";
            if (ufwVerbose != null) {
                for (String line : ufwVerbose.split("\\n")) {
                    if (line.contains("Logging:")) {
                        // Tách phần sau ":" và chia từ
                        String[] words = line.split(":")[1].trim().split("\\s+");
                        loggingStatus = words[0];
                        break;
                    }
                    System.out.println(loggingStatus);
                }
            }
            model.addAttribute("loggingStatus", loggingStatus);
        } catch (Exception e) {
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
            Model model,
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

            // Chạy lệnh bật/tắt logging của UFW
            String command = enable
                    ? "echo '" + computer.getPassword() + "' | sudo -S ufw logging on"
                    : "echo '" + computer.getPassword() + "' | sudo -S ufw logging off";
            String result = ubuntuInfo.executeCommand(sshSession, command);
            System.out.println("Toggle logging result: " + result);

            // Lấy trạng thái logging sau khi thực thi lệnh
            String statusCommand = "echo '" + computer.getPassword() + "' | sudo -S ufw status verbose";
            String statusOutput = ubuntuInfo.executeCommand(sshSession, statusCommand);
            boolean isLoggingOn = statusOutput != null && statusOutput.contains("Logging: on");

            response.put("success", true);
            response.put("message", "Logging " + (enable ? "enabled" : "disabled"));
            response.put("loggingStatus", isLoggingOn ? "active" : "inactive");

            String logsCommand = "echo '" + computer.getPassword() + "' | sudo -S tac /var/log/ufw.log | head -n 10";
            String logsOutput = ubuntuInfo.executeCommand(sshSession, logsCommand);
            List<Map<String, String>> ufwLogs = parseUfwLogs(logsOutput);
            
            model.addAttribute("ufwLogs", ufwLogs);
            
        } catch (JSchException e) {
            response.put("success", false);
            response.put("message", "SSH connection failed: " + e.getMessage());
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error toggling logging: " + e.getMessage());
            e.printStackTrace(); // In ra log chi tiết cho mục đích debug
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

            // Tách theo khoảng trắng để lấy các token
            String[] tokens = line.split("\\s+");
            if (tokens.length >= 6) {
                // Giả sử 3 token đầu là timestamp: Month, Day, Time
                String timestamp = tokens[0] + " " + tokens[1] + " " + tokens[2];
                logEntry.put("timestamp", timestamp);

                // Token chứa hành động thường nằm ở vị trí chứa "[UFW" hoặc "[UFW BLOCK]"
                String action = "";
                for (String token : tokens) {
                    if (token.startsWith("[UFW")) {
                        action = token.replaceAll("\\[|\\]", "");
                        break;
                    }
                }
                logEntry.put("action", action);

                // Duyệt qua các token để tìm các thông tin cần thiết
                for (String token : tokens) {
                    if (token.startsWith("IN=")) {
                        logEntry.put("interface", token.substring(3));
                    }
                    if (token.startsWith("SRC=")) {
                        logEntry.put("sourceIp", token.substring(4));
                    }
                    if (token.startsWith("SPT=")) {
                        logEntry.put("sourcePort", token.substring(4));
                    }
                    if (token.startsWith("DST=")) {
                        logEntry.put("destinationIp", token.substring(4));
                    }
                    if (token.startsWith("DPT=")) {
                        logEntry.put("destinationPort", token.substring(4));
                    }
                    if (token.startsWith("PROTO=")) {
                        logEntry.put("protocol", token.substring(6));
                    }
                }
                logs.add(logEntry);
            }
        }
        return logs;
    }

}
