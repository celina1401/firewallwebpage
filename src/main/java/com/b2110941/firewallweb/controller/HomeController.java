package com.b2110941.firewallweb.controller;

import com.b2110941.firewallweb.model.PC;
import com.b2110941.firewallweb.model.PCAccount;
import com.b2110941.firewallweb.model.User;
import com.b2110941.firewallweb.model.UserAccount;
import com.b2110941.firewallweb.repository.pcAccountRepository;
import com.b2110941.firewallweb.repository.pcRepository;
import com.b2110941.firewallweb.repository.userAccountRepository;
import com.b2110941.firewallweb.repository.userRepository;
import com.b2110941.firewallweb.service.ConnectSSH;
import com.jcraft.jsch.Session;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class HomeController {

    @Autowired
    private pcRepository pcRepository;
    @Autowired
    private pcAccountRepository pcAccountRepository;
    @Autowired
    private userRepository userRepository;
    @Autowired
    private userAccountRepository userAccountRepository;
    @Autowired
    private ConnectSSH connectSSH;

    @RequestMapping("/home_{username}")
    public String home(@PathVariable String username, 
                       RedirectAttributes redirectAttr,
                       Model model, HttpSession session) {
        // Lay username tu session
        String sessionUsername = (String) session.getAttribute("username");

        if (sessionUsername == null || !sessionUsername.equals(username)) {
            model.addAttribute("error", "Unauthorized access! You can only view your own home page");
            return "error";
        }

        // Lấy danh sách tất cả PCs từ database
        List<PC> computers = pcRepository.findByOwnerUsername(username);    
        model.addAttribute("computers", computers);
        model.addAttribute("username", username);
        return "home";
    }

    @PostMapping("/home_{username}")
    public String add_computer(
            @PathVariable String username,
            @RequestParam String pcName,
            @RequestParam String pcUsername,
            @RequestParam String ipAddress,
            @RequestParam int port,
            @RequestParam String password,
            RedirectAttributes redirectAttrs,
            Model model,
            HttpSession session) {

        // Kiem tra username tu session
        String sessionUsername = (String) session.getAttribute("username");

        if (sessionUsername == null || !sessionUsername.equals(username)) {
            redirectAttrs.addFlashAttribute("error", "Unauthorized access! You can only add PCs to your own home page");
            return "redirect:/home_{username}";
        }

        // Loại bỏ khoảng trắng ở đầu và cuối cho tất cả các trường nhập liệu
        String trimmedPcName = pcName.trim();
        String trimmedPcUsername = pcUsername.trim();
        String trimmedIpAddress = ipAddress.trim();
        String trimmedPassword = password.trim();

        // Kiểm tra xem các trường có rỗng sau khi trim không
        if (trimmedPcName.isEmpty() || trimmedPcUsername.isEmpty()
                || trimmedIpAddress.isEmpty() || trimmedPassword.isEmpty()) {
            redirectAttrs.addFlashAttribute("error", "All fields are required!");
            List<PC> computers = pcRepository.findByOwnerUsername(username);
            model.addAttribute("computers", computers);
            model.addAttribute("username", username);
            return "redirect:/home_{username}";
        }

        //Kiem tra ton tai (trung IP vs trung pcName)
        Optional<PC> existPC = pcRepository.findByPcNameAndOwnerUsername(trimmedPcName, username);
        Optional<PC> existIPPC = pcRepository.findByIpAddressAndOwnerUsername(trimmedIpAddress, username);

        if (existPC.isPresent() && existPC.get().getOwnerUsername().equals(username)) {
            redirectAttrs.addFlashAttribute("toastType", "error");
            redirectAttrs.addFlashAttribute("toastMessage", "PC name already exists!");
            return "redirect:/home_{username}";
        } else if (existIPPC.isPresent() && existIPPC.get().getOwnerUsername().equals(username)) {
            redirectAttrs.addFlashAttribute("toastType", "error");
            redirectAttrs.addFlashAttribute("toastMessage", "A computer with this IP address already exists!");
            return "redirect:/home_{username}";
        }else {
            // Kiểm tra kết nối SSH trước khi lưu
            boolean sshSuccess = connectSSH.checkConnectSSH(trimmedIpAddress, port, trimmedPcUsername, trimmedPassword);
            if (!sshSuccess) {
                redirectAttrs.addFlashAttribute("toastType", "error");
                redirectAttrs.addFlashAttribute("toastMessage", "Failed to connect to SSH. Please check your credentials.");
                return "redirect:/home_{username}";
            } else {
                // Luu vao db
                PC newPC = new PC(trimmedPcName, trimmedPcUsername, trimmedIpAddress, port, trimmedPassword, username);
                updateComputerStatus(newPC); // Cập nhật trạng thái cho máy mới thêm
                pcRepository.save(newPC);

                PCAccount newPCAccount = new PCAccount(trimmedPcUsername, trimmedPassword);
                pcAccountRepository.save(newPCAccount);

                redirectAttrs.addFlashAttribute("toastType", "success");
                redirectAttrs.addFlashAttribute("toastMessage", "PC added successfully!");
            }

        }
        // Lấy danh sách PCs để hiển thị trên giao diện
        List<PC> computers = pcRepository.findByOwnerUsername(username);
        updateComputerStatuses(computers); // Cập nhật trạng thái sau khi thêm máy mới
        model.addAttribute("computers", computers);
        model.addAttribute("username", username);

        if (model.containsAttribute("toastMessage")) {
            // Keep the toast message in the model
            String toastMessage = (String) model.asMap().get("toastMessage");
            String toastType = (String) model.asMap().getOrDefault("toastType", "success");
            model.addAttribute("toastMessage", toastMessage);
            model.addAttribute("toastType", toastType);
        }

        return "redirect:/home_" + username + "/manageSystem";
    }

    @GetMapping("/home_{username}/{menuOption}")
    public String loadSection(@PathVariable("username") String username,
            @PathVariable("menuOption") String menuOption,
            HttpServletRequest request,
            Model model) {
        model.addAttribute("username", username);
        model.addAttribute("menuOption", menuOption);

        // Ví dụ: Lấy userInfo nếu là information
        if ("information".equals(menuOption)) {
            Optional<User> userOpt = userRepository.findByUsername(username);
            userOpt.ifPresentOrElse(
                    user -> model.addAttribute("userInfo", user),
                    () -> model.addAttribute("error", "User not found"));
        } else {
            // Nếu là menu mặc định, ta load danh sách máy tính
            List<PC> computers = pcRepository.findByOwnerUsername(username);
            updateComputerStatuses(computers); // Cập nhật trạng thái trước khi hiển thị
            model.addAttribute("computers", computers);
        }

        // Kiểm tra AJAX
        String ajaxHeader = request.getHeader("X-Requested-With");
        if ("XMLHttpRequest".equals(ajaxHeader)) {
            return "home :: section(menuOption='" + menuOption + "')";
        }
        return "home";
    }

    @PostMapping("/home_{username}/information")
    public String updateUserInformation(
            @PathVariable("username") String username,
            @RequestParam("fullname") String fullname,
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            RedirectAttributes redirectAttrs,
            Model model) {

        // Lấy user từ CSDL dựa trên username
        Optional<User> updateUser = userRepository.findByUsername(username);
        Optional<UserAccount> updateUserAcc = userAccountRepository.findByUsername(username);
        if (updateUser == null) {
            // Xử lý nếu không tìm thấy user, trả về trang lỗi hoặc redirect
            redirectAttrs.addFlashAttribute("error", "User not found");
        }
        User user = updateUser.get();
        UserAccount userAcc = updateUserAcc.get();
        // Cập nhật lại các trường
        user.setFullname(fullname);
        user.setEmail(email);
        user.setPassword(password);
        userAcc.setPassword(password);

        // Lưu thay đổi vào CSDL
        userRepository.save(user);
        userAccountRepository.save(userAcc);

        // Sau khi cập nhật, có thể đưa user đã cập nhật vào model
        model.addAttribute("userInfo", user);

        redirectAttrs.addFlashAttribute("toastType", "success");
        redirectAttrs.addFlashAttribute("toastMessage", "User information updated successfully!");

        return "redirect:/home_" + username + "/information";

    }

    @GetMapping("/delete-account/{username}")
    public String deleteAccount(@PathVariable String username,
            RedirectAttributes redirectAttrs,
            HttpSession session,
            Model model) {
        try {
            // Check if user exists
            if (!userRepository.existsByUsername(username)) {
                redirectAttrs.addFlashAttribute("error", "User not found");
                return "redirect:/home_" + username + "/information";
            }

            // Delete all PCs owned by the user
            List<PC> userPCs = pcRepository.findByOwnerUsername(username);
            for (PC pc : userPCs) {
                pcAccountRepository.deleteByUsername(pc.getPcUsername());
                pcRepository.delete(pc);
            }
            
            // Delete user and user account
            userAccountRepository.deleteByUsername(username);
            userRepository.deleteByUsername(username);
            
            redirectAttrs.addFlashAttribute("toastMessage", "Account deleted successfully!");
            // Clear session
            session.invalidate();

            return "redirect:/";
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("error", "Failed to delete account. Please try again.");
            return "redirect:/home_" + username + "/information";
        }
    }

    @GetMapping("/delete-pc/{username}/{pcName}")
    public String deletePC(@PathVariable String username,
            @PathVariable String pcName,
            RedirectAttributes redirectAttrs,
            HttpSession session,
            Model model) {
        String sessionUsername = (String) session.getAttribute("username");

        // Kiểm tra đăng nhập
        if (sessionUsername == null || !sessionUsername.equals(username)) {
            redirectAttrs.addFlashAttribute("error", "Unauthorized access! You can only delete PCs on your own home page");
            return "redirect:/home_{username}";
        }

        try {
            // Tìm máy theo pcName và username
            Optional<PC> optionalPC = pcRepository.findByPcNameAndOwnerUsername(pcName, username);
            if (optionalPC.isEmpty()) {
                redirectAttrs.addFlashAttribute("error", "PC not found");
                return "redirect:/home_" + username;
            }

            PC pc = optionalPC.get();
            // Xoá tài khoản SSH của máy (nếu có)
            pcAccountRepository.deleteByUsername(pc.getPcUsername());

            // Xoá máy
            pcRepository.delete(pc);
            redirectAttrs.addFlashAttribute("toastMessage", "PC deleted successfully!");
            
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("error", "Failed to delete PC. Please try again.");
        }

        return "redirect:/home_" + username;
    }

    @GetMapping("/api/ssh-status/{username}/{pcName}")
    @ResponseBody
    public PC checkSinglePCStatus(
            @PathVariable String username,
            @PathVariable String pcName,
            HttpSession session) {

        // Verify user authentication
        String sessionUsername = (String) session.getAttribute("username");
        if (sessionUsername == null || !sessionUsername.equals(username)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized access");
        }

        // Find the PC
        Optional<PC> pcOpt = pcRepository.findByPcNameAndOwnerUsername(pcName, username);
        if (pcOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PC not found");
        }

        PC pc = pcOpt.get();
        try {
            Thread.sleep(15000); // Sleep for 15 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Status check was interrupted: " + e.getMessage());
        }
        updateComputerStatus(pc); // Update the status

        return pc; // Return the PC with updated status
    }

    @GetMapping("/api/ufw-status/{username}/{pcName}")
    @ResponseBody
    public java.util.Map<String, Object> checkUFWStatus(
            @PathVariable String username,
            @PathVariable String pcName,
            HttpSession session) {

        java.util.Map<String, Object> response = new java.util.HashMap<>();

        // Verify user authentication
        String sessionUsername = (String) session.getAttribute("username");
        if (sessionUsername == null || !sessionUsername.equals(username)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized access");
        }

        // Find the PC
        Optional<PC> pcOpt = pcRepository.findByPcNameAndOwnerUsername(pcName, username);
        if (pcOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PC not found");
        }

        PC pc = pcOpt.get();

        try {
            Session sshSession = connectSSH.establishSSH(
                    pc.getIpAddress(),
                    pc.getPort(),
                    pc.getPcUsername(),
                    pc.getPassword());

            if (sshSession == null || !sshSession.isConnected()) {
                response.put("success", false);
                response.put("message", "Failed to establish SSH connection");
                return response;
            }

            // Check UFW status
            String ufwStatusCommand = "echo '" + pc.getPassword() + "' | sudo -S ufw status";

            com.jcraft.jsch.ChannelExec channel = (com.jcraft.jsch.ChannelExec) sshSession.openChannel("exec");
            channel.setCommand(ufwStatusCommand);

            java.io.InputStream in = channel.getInputStream();
            channel.connect();

            byte[] tmp = new byte[1024];
            StringBuilder output = new StringBuilder();
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0)
                        break;
                    output.append(new String(tmp, 0, i));
                }
                if (channel.isClosed()) {
                    break;
                }
                try {
                    Thread.sleep(100);
                } catch (Exception ee) {
                    // Ignore
                }
            }

            channel.disconnect();
            sshSession.disconnect();

            String ufwOutput = output.toString();
            boolean isActive = ufwOutput.contains("Status: active");

            response.put("success", true);
            response.put("ufwActive", isActive);
            response.put("status", isActive ? "ON" : "OFF");
            response.put("rawOutput", ufwOutput);

            System.out.println("UFW Status for " + pc.getPcName() + ": " +
                    (isActive ? "ON" : "OFF"));

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error checking UFW status: " + e.getMessage());
            e.printStackTrace();
        }

        return response;
    }

    private void updateComputerStatus(PC computer) {
        try {
            Session sshSession = connectSSH.establishSSH(
                    computer.getIpAddress(),
                    computer.getPort(),
                    computer.getPcUsername(),
                    computer.getPassword());

            boolean isConnected = sshSession != null && sshSession.isConnected();
            computer.setSshStatus(isConnected);

            // Check UFW status if SSH is connected
            if (isConnected) {
                try {
                    // Check UFW status
                    String ufwStatusCommand = "echo '" + computer.getPassword() + "' | sudo -S ufw status";

                    com.jcraft.jsch.ChannelExec channel = (com.jcraft.jsch.ChannelExec) sshSession.openChannel("exec");
                    channel.setCommand(ufwStatusCommand);

                    java.io.InputStream in = channel.getInputStream();
                    channel.connect();

                    byte[] tmp = new byte[1024];
                    StringBuilder output = new StringBuilder();
                    while (true) {
                        while (in.available() > 0) {
                            int i = in.read(tmp, 0, 1024);
                            if (i < 0)
                                break;
                            output.append(new String(tmp, 0, i));
                        }
                        if (channel.isClosed()) {
                            break;
                        }
                        try {
                            Thread.sleep(100);
                        } catch (Exception ee) {
                            // Ignore
                        }
                    }

                    channel.disconnect();

                    String ufwOutput = output.toString();
                    boolean isUfwActive = ufwOutput.contains("Status: active");

                    // Store UFW status in the PC object
                    computer.setUfwStatus(isUfwActive ? "ON" : "OFF");

                    System.out.println("UFW Status for " + computer.getPcName() + ": " +
                            (isUfwActive ? "ON" : "OFF"));
                } catch (Exception e) {
                    System.out.println("Error checking UFW status: " + e.getMessage());
                    computer.setUfwStatus("UNKNOWN");
                }
            } else {
                computer.setUfwStatus("UNKNOWN");
            }

            // Update in database
            pcRepository.save(computer);

            System.out.println("SSH Status for " + computer.getPcName() + ": " +
                    (isConnected ? "Connected" : "Disconnected"));

            // Close session if it's open
            if (isConnected) {
                sshSession.disconnect();
            }
        } catch (Exception e) {
            System.out.println("Error checking SSH status for " + computer.getPcName() + ": " + e.getMessage());
            computer.setSshStatus(false);
            computer.setUfwStatus("UNKNOWN");
            pcRepository.save(computer);
        }
    }

    private void updateComputerStatuses(List<PC> computers) {
        for (PC computer : computers) {
            updateComputerStatus(computer);
            System.out.println(computer.getPcName() + ": " + computer.isSshStatus());
            // Không gọi pcRepository.save(computer) để không lưu trạng thái vào database
        }
    }

        @PostMapping("/api/shutdown/{username}/{pcName}")
    @ResponseBody
    public java.util.Map<String, Object> shutdownPC(
            @PathVariable String username,
            @PathVariable String pcName,
            HttpSession session) {

        java.util.Map<String, Object> response = new java.util.HashMap<>();

        // Xác thực người dùng
        String sessionUsername = (String) session.getAttribute("username");
        if (sessionUsername == null || !sessionUsername.equals(username)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized access");
        }

        // Tìm máy tính
        Optional<PC> pcOpt = pcRepository.findByPcNameAndOwnerUsername(pcName, username);
        if (pcOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PC not found");
        }

        PC pc = pcOpt.get();

        try {
            Session sshSession = connectSSH.establishSSH(
                    pc.getIpAddress(),
                    pc.getPort(),
                    pc.getPcUsername(),
                    pc.getPassword());

            if (sshSession == null || !sshSession.isConnected()) {
                response.put("success", false);
                response.put("message", "Không thể kết nối SSH");
                return response;
            }

            // Gửi lệnh tắt máy
            String shutdownCommand = "echo '" + pc.getPassword() + "' | sudo -S shutdown -h now";

            com.jcraft.jsch.ChannelExec channel = (com.jcraft.jsch.ChannelExec) sshSession.openChannel("exec");
            channel.setCommand(shutdownCommand);
            channel.connect();

            // Đợi một chút để lệnh được thực thi
            Thread.sleep(1000);
            
            channel.disconnect();
            sshSession.disconnect();

            // Cập nhật trạng thái máy tính
            pc.setSshStatus(false);
            pc.setUfwStatus("UNKNOWN");
            pcRepository.save(pc);

            response.put("success", true);
            response.put("message", "Lệnh tắt máy đã được gửi thành công");
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Lỗi khi gửi lệnh tắt máy: " + e.getMessage());
        }

        return response;
    }

    // HomeController.java

@GetMapping("/machine/{username}/{pcName}")
public String showMachineDetail(
        @PathVariable String username,
        @PathVariable String pcName,
        HttpSession session,
        Model model) {

    // 1) Xác thực
    String sessionUsername = (String) session.getAttribute("username");
    if (sessionUsername == null || !sessionUsername.equals(username)) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized access");
    }

    // 2) Lấy máy từ DB
    Optional<PC> pcOpt = pcRepository.findByPcNameAndOwnerUsername(pcName, username);
    if (pcOpt.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PC not found");
    }
    PC pc = pcOpt.get();

    // 3) Cập nhật trạng thái SSH/UFW trước khi hiển thị (tương tự home)
    updateComputerStatus(pc);

    // 4) Đưa vào model và trả về template
    model.addAttribute("username", username);
    model.addAttribute("pc", pc);
    return "redirect:/machine_" + username + "/" + pcName;
}

// Trả về JSON chi tiết máy (không lộ password)
@GetMapping("/api/pc-detail/{username}/{pcName}")
@ResponseBody
public Map<String, Object> getPCDetail(
        @PathVariable String username,
        @PathVariable String pcName,
        HttpSession session) {

    // 1. Xác thực
    String sessionUsername = (String) session.getAttribute("username");
    if (sessionUsername == null || !sessionUsername.equals(username)) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }

    // 2. Lấy PC
    PC pc = pcRepository
               .findByPcNameAndOwnerUsername(pcName, username)
               .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PC not found"));

    // 3. Cập nhật trạng thái SSH/UFW
    updateComputerStatus(pc);

    // 4. Build JSON
    Map<String, Object> resp = new HashMap<>();
    resp.put("pcName", pc.getPcName());
    resp.put("ipAddress", pc.getIpAddress());
    resp.put("port", pc.getPort());
    resp.put("pcUsername", pc.getPcUsername());
    resp.put("sshStatus", pc.isSshStatus());
    resp.put("ufwStatus", pc.getUfwStatus());
    return resp;
}


}
