package com.b2110941.firewallweb.controller;

import com.b2110941.firewallweb.model.PC;
import com.b2110941.firewallweb.service.ConnectSSH;
import com.b2110941.firewallweb.service.PCService;
import com.b2110941.firewallweb.service.UFWService;
import com.b2110941.firewallweb.service.UbuntuInfo;
import com.jcraft.jsch.Session;

import jakarta.servlet.http.HttpSession;

import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RuleController {

    @Autowired
    private PCService pcService;

    @Autowired
    private UFWService ufwService;

    @Autowired
    private ConnectSSH connectSSH;

    @Autowired
    private UbuntuInfo ubuntuInfo;

    @PostMapping("/machine/{pcName}/rule")
    public String addFirewallRule(
            @PathVariable("pcName") String pcName,
            @RequestParam String action,
            @RequestParam(required = false) String portCheck,
            @RequestParam String protocol,
            @RequestParam String toType,
            @RequestParam(required = false) String toIp,
            @RequestParam(required = false) String portRangeStart, // Sửa tên parameter
            @RequestParam(required = false) String portRangeEnd, // Sửa tên parameter
            @RequestParam(required = false) String toApp,
            @RequestParam(required = false) String toInterface,
            @RequestParam(required = false) String specificPortCheck,
            @RequestParam(required = false) String port,
            @RequestParam String fromType,
            @RequestParam(required = false) String fromIp,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        // Kiểm tra đăng nhập
        String ownerUsername = (String) session.getAttribute("username");
        if (ownerUsername == null) {
            redirectAttributes.addFlashAttribute("error", "Please login your account!");
            return "redirect:/";
        }

        // Tìm máy tính
        Optional<PC> pcOptional = pcService.findByPcNameAndOwnerUsername(pcName, ownerUsername);
        if (pcOptional.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Computer " + pcName + " not found");
            return "redirect:/machine/" + pcName + "/rule";
        }

        PC pc = pcOptional.get();

        // Xử lý dữ liệu checkbox
        boolean isOutgoing = portCheck != null;
        String app = "";
        if ("app".equals(toType)) {
            app = (toApp != null ? toApp : "");
        } else if ("interface".equals(toType)) {
            app = (toInterface != null ? toInterface : "");
        }

        // Gọi service thêm rule; chuyển đúng tên biến cho port range
        String result = ufwService.addRuleFromForm(pc, action, isOutgoing, protocol,
                toType, toIp, portRangeStart, portRangeEnd, port, fromType, fromIp, app);

        // Gửi thông báo phản hồi
        if (result.startsWith("success")) {
            redirectAttributes.addFlashAttribute("success", "Firewall rule added successfully.");
        } else {
            redirectAttributes.addFlashAttribute("error", "Failed to add rule: " + result);
        }

        return "redirect:/machine/" + pcName + "/rule";
    }

    @DeleteMapping("/machine/deleteRule")
    @ResponseBody
    public Object deleteFirewallRule(
            @RequestParam String ruleId,
            @RequestParam String pcName,
            HttpSession session) {

        // Check login
        String ownerUsername = (String) session.getAttribute("username");
        if (ownerUsername == null) {
            return Map.of("success", false, "message", "Please login your account!");
        }

        // Find the computer
        Optional<PC> pcOptional = pcService.findByPcNameAndOwnerUsername(pcName, ownerUsername);
        if (pcOptional.isEmpty()) {
            return Map.of("success", false, "message", "Computer " + pcName + " not found");
        }

        PC pc = pcOptional.get();

        // Call service to delete rule
        String result = ufwService.deleteRule(pc, ruleId);

        if (result.startsWith("success")) {
            return Map.of("success", true, "message", "Rule deleted successfully");
        } else {
            return Map.of("success", false, "message", result);
        }
    }

    // New endpoint to get the UFW app list
    @PostMapping("/machine/{pcName}/app-list")
    @ResponseBody
    public String[] getUFWAppList(
            @PathVariable("pcName") String pcName,
            HttpSession session) {

        // Check if user is logged in
        String ownerUsername = (String) session.getAttribute("username");
        if (ownerUsername == null) {
            return new String[] { "Error: Please login to your account!" };
        }

        // Find the PC by name and owner
        Optional<PC> pcOptional = pcService.findByPcNameAndOwnerUsername(pcName, ownerUsername);
        if (pcOptional.isEmpty()) {
            return new String[] { "Error: Computer " + pcName + " not found" };
        }

        PC pc = pcOptional.get();

        // Call UFWService to get the app list
        return ufwService.getUFWAppList(pc);
    }

    @PostMapping("/machine/{pcName}/interface-list")
    @ResponseBody
    public String[] getInterfaceList(
            @PathVariable("pcName") String pcName,
            HttpSession session) {

        // Check if user is logged in
        String ownerUsername = (String) session.getAttribute("username");
        if (ownerUsername == null) {
            return new String[] { "Error: Please login to your account!" };
        }

        // Find the PC by name and owner
        Optional<PC> pcOptional = pcService.findByPcNameAndOwnerUsername(pcName, ownerUsername);
        if (pcOptional.isEmpty()) {
            return new String[] { "Error: Computer " + pcName + " not found" };
        }

        PC pc = pcOptional.get();

        try {
            Session sshSession = connectSSH.establishSSH(
                    pc.getIpAddress(),
                    pc.getPort(),
                    pc.getPcUsername(),
                    pc.getPassword());

            // Execute ip link command and parse output to get interface names
            String command = "ip link | grep -E '^[0-9]+:' | cut -d: -f2 | awk '{print $1}'";
            String output = ubuntuInfo.executeCommand(sshSession, command);

            if (output != null && !output.trim().isEmpty()) {
                return output.trim().split("\n");
            }

            return new String[] { "No interfaces found" };

        } catch (Exception e) {
            return new String[] { "Error: " + e.getMessage() };
        }
    }

    @PostMapping("/machine/{pcName}/rule/update")
    public String updateRule(
            @PathVariable String pcName,
            @RequestParam String ruleId,
            @RequestParam String action,
            @RequestParam(required = false) String portCheck,
            @RequestParam String protocol,
            @RequestParam String toType,
            @RequestParam(required = false) String toIp,
            @RequestParam(required = false) String portRangeStart,
            @RequestParam(required = false) String portRangeEnd,
            @RequestParam(required = false) String port,
            @RequestParam String fromType,
            @RequestParam(required = false) String fromIp,
            @RequestParam(required = false) String toApp,
            @RequestParam(required = false) String toInterface,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        // 1) Xác thực login
        String ownerUsername = (String) session.getAttribute("username");
        if (ownerUsername == null) {
            redirectAttributes.addFlashAttribute("error", "Please login!");
            return "redirect:/";
        }

        // 2) Tìm PC
        Optional<PC> opt = pcService.findByPcNameAndOwnerUsername(pcName, ownerUsername);
        if (opt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Computer not found");
            return "redirect:/machine/" + pcName + "/rule";
        }
        PC pc = opt.get();

        // 3) Xóa rule cũ
        String intRuleId = Integer.toString(Integer.parseInt(ruleId));
        String deleteResult = ufwService.deleteRule(pc, intRuleId);
        System.out.println("Delete result: " + ruleId);
        if (!deleteResult.startsWith("success")) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete old rule: " + deleteResult);
            return "redirect:/machine/" + pcName + "/rule";
        }

        // 4) Add rule mới
        boolean isOutgoing = portCheck != null;
        String app = "";
        if ("app".equals(toType)) {
            app = toApp != null ? toApp : "";
        } else if ("interface".equals(toType)) {
            app = toInterface != null ? toInterface : "";
        }
    
        String addResult = ufwService.addRuleFromForm(
            pc,
            action,
            isOutgoing,
            protocol,
            toType,
            toIp,
            portRangeStart,
            portRangeEnd,
            port,
            fromType,
            fromIp,
            app
        );
        if (!addResult.startsWith("success")) {
            redirectAttributes.addFlashAttribute("error", "Failed to add updated rule: " + addResult);
        } else {
            redirectAttributes.addFlashAttribute("success", "Rule updated successfully.");
        }

        return "redirect:/machine/" + pcName + "/rule";
    }

    @GetMapping("/machine/{pcName}/rule/{ruleId}")
    @ResponseBody
    public Map<String, Object> fetchRuleDetails(
            @PathVariable String pcName,
            @PathVariable String ruleId,
            HttpSession session) {

        // 1) Xác thực login
        String owner = (String) session.getAttribute("username");
        if (owner == null) {
            return Map.of("success", false, "message", "Please login!");
        }

        // 2) Tìm PC
        Optional<PC> opt = pcService.findByPcNameAndOwnerUsername(pcName, owner);
        if (opt.isEmpty()) {
            return Map.of("success", false, "message", "Computer not found");
        }
        PC pc = opt.get();

        // 3) gọi service lấy chi tiết rule
        Map<String, String> rule = ufwService.fetchRuleDetailsById(pc, ruleId);
        if (rule == null || rule.isEmpty()) {
            return Map.of("success", false, "message", "Cannot fetch rule details");
        }

        // 4) trả về JSON
        return Map.of(
                "success", true,
                "rule", rule);
    }

}
