package com.b2110941.firewallweb.controller;

import com.b2110941.firewallweb.model.PC;
import com.b2110941.firewallweb.service.PCService;
import com.b2110941.firewallweb.service.UFWService;
import jakarta.servlet.http.HttpSession;
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
}
