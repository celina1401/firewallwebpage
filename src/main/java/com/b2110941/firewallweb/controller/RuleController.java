package com.b2110941.firewallweb.controller;

import com.b2110941.firewallweb.model.PC;
import com.b2110941.firewallweb.service.ConnectSSH;
import com.b2110941.firewallweb.service.PCService;
import com.b2110941.firewallweb.service.UFWService;
import com.b2110941.firewallweb.service.UbuntuInfo;
import com.jcraft.jsch.Session;

import jakarta.servlet.http.HttpSession;

import java.util.HashMap;
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
            redirectAttributes.addFlashAttribute("toastType", "error");
            redirectAttributes.addFlashAttribute("toastMessage", "Please login your account!");
            return "redirect:/";
        }

        // Tìm máy tính
        Optional<PC> pcOptional = pcService.findByPcNameAndOwnerUsername(pcName, ownerUsername);
        if (pcOptional.isEmpty()) {
            redirectAttributes.addFlashAttribute("toastType", "error");
            redirectAttributes.addFlashAttribute("toastMessage", "Computer " + pcName + " not found");

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

        System.out.println(result);

        // Gửi thông báo phản hồi
        switch (result) {
            case "added":
                redirectAttributes.addFlashAttribute("toastType", "success");
                redirectAttributes.addFlashAttribute("toastMessage", "Rule added successfully.");
                break;
            case "existing":
                redirectAttributes.addFlashAttribute("toastType", "info");
                redirectAttributes.addFlashAttribute("toastMessage", "Rule already exists.");
                break;
            case "notfound":
                redirectAttributes.addFlashAttribute("toastType", "error");
                redirectAttributes.addFlashAttribute("toastMessage", "Rule not found to add.");
                break;
            default:
                // other errors, e.g. "error: ..."
                redirectAttributes.addFlashAttribute("toastType", "error");
                redirectAttributes.addFlashAttribute("toastMessage", result.replaceFirst("^error:\\s*", ""));
                break;
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
            @RequestParam("ruleId") String ruleId,
            @RequestParam("action") String action,
            @RequestParam(value = "portCheck", required = false) Boolean isOutgoing,
            @RequestParam("protocol") String protocol,
            @RequestParam(value = "fromType", required = false) String fromType,
            @RequestParam(value = "fromIp", required = false) String fromIp,
            @RequestParam(value = "toType", required = false) String toType,
            @RequestParam(value = "toIp", required = false) String toIp,
            @RequestParam(value = "toInterface", required = false) String toInterface,
            @RequestParam(value = "toApp", required = false) String toApp,
            @RequestParam(value = "portType", required = false) String portType,
            @RequestParam(value = "port", required = false) String port,
            @RequestParam(value = "portRangeStart", required = false) String portRangeStart,
            @RequestParam(value = "portRangeEnd", required = false) String portRangeEnd,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
    
        // 1) Kiểm tra login + tìm PC
        String owner = (String) session.getAttribute("username");
        if (owner == null) {
            redirectAttributes.addFlashAttribute("toastType", "error");
            redirectAttributes.addFlashAttribute("toastMessage", "Please login to your account!");
            return "redirect:/machine/" + pcName + "/rule";
        }
        Optional<PC> opt = pcService.findByPcNameAndOwnerUsername(pcName, owner);
        if (opt.isEmpty()) {
            redirectAttributes.addFlashAttribute("toastType", "error");
            redirectAttributes.addFlashAttribute("toastMessage", "Unable to find computer: " + pcName);
            return "redirect:/machine/" + pcName + "/rule";
        }
        PC pc = opt.get();
    
        // 2) Lấy chi tiết rule cũ
        Map<String, String> oldRule = ufwService.fetchRuleDetailsById(pc, ruleId);
        if (oldRule == null) {
            redirectAttributes.addFlashAttribute("toastType", "error");
            redirectAttributes.addFlashAttribute("toastMessage", "Old rule not found");
            return "redirect:/machine/" + pcName + "/rule";
        }
    
        // 3) Tạo cấu trúc map cho rule mới để so sánh
        Map<String, String> newRule = new HashMap<>(oldRule);
        newRule.put("action", action);
        newRule.put("direction", Boolean.TRUE.equals(isOutgoing) ? "out" : "in");
        newRule.put("protocol", protocol);
        newRule.put("sourceIp", "ipaddress".equals(fromType) ? fromIp : "any");
        newRule.put("destinationIp", "ipaddress".equals(toType) ? toIp : "any");
        // port/app/interface
        if ("app".equals(toType)) {
            newRule.put("app", toApp);
            newRule.put("interface", "");
        } else if ("interface".equals(toType)) {
            newRule.put("interface", toInterface);
            newRule.put("app", "");
        } else {
            newRule.put("interface", "");
            newRule.put("app", "");
        }
        // portType / port / portStart / portEnd
        newRule.put("portType", portType != null ? portType : "any");
        newRule.put("port", "specific".equals(portType) ? port : "");
        newRule.put("portStart", "range".equals(portType) ? portRangeStart : "");
        newRule.put("portEnd",   "range".equals(portType) ? portRangeEnd   : "");
    
        // 4) Nếu rule mới giống rule cũ → không thay đổi
        if (oldRule.equals(newRule)) {
            redirectAttributes.addFlashAttribute("toastType", "info");
            redirectAttributes.addFlashAttribute("toastMessage", "No changes detected");
            return "redirect:/machine/" + pcName + "/rule";
        }
    
        // 5) Thêm rule mới
        boolean outgoing = Boolean.TRUE.equals(isOutgoing);
        String addResult = ufwService.addRuleFromForm(
            pc, action, outgoing, protocol,
            toType, toIp, portRangeStart, portRangeEnd, port,
            fromType, fromIp,
            "app".equals(toType) ? toApp :
            "interface".equals(toType) ? toInterface : null
        );
    
        // 6) Xử lý kết quả thêm
        if (addResult.startsWith("added")) {
            // 6.1) Nếu thêm thành công, tiến hành xóa rule cũ
            String delResult = ufwService.deleteRule(pc, ruleId);
            if (delResult.startsWith("success")) {
                redirectAttributes.addFlashAttribute("toastType", "success");
                redirectAttributes.addFlashAttribute("toastMessage", "Rule updated successfully!");
            } else {
                // Thêm mới thành công nhưng xóa cũ thất bại
                redirectAttributes.addFlashAttribute("toastType", "warning");
                redirectAttributes.addFlashAttribute("toastMessage",
                        "New rule added but failed to delete old rule: " + delResult);
            }
    
        } else if (addResult.startsWith("existing")) {
            // 6.2) Rule mới đã tồn tại
            redirectAttributes.addFlashAttribute("toastType", "info");
            redirectAttributes.addFlashAttribute("toastMessage", "Rule already exists, no changes made.");
        } else {
            // 6.3) Lỗi: không thêm được rule mới → giữ nguyên rule cũ
            redirectAttributes.addFlashAttribute("toastType", "error");
            redirectAttributes.addFlashAttribute("toastMessage", "Cannot add new rule: " + addResult);
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
