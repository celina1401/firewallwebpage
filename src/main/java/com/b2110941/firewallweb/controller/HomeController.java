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
import java.util.List;
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
    public String home(@PathVariable String username, Model model, HttpSession session) {
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
            Model model,
            HttpSession session) {

        // Kiem tra username tu session
        String sessionUsername = (String) session.getAttribute("username");

        if (sessionUsername == null || !sessionUsername.equals(username)) {
            model.addAttribute("error", "Unauthorized access! You can only add PCs to your own account");
            return "error";
        }

        // Loại bỏ khoảng trắng ở đầu và cuối cho tất cả các trường nhập liệu
        String trimmedPcName = pcName.trim();
        String trimmedPcUsername = pcUsername.trim();
        String trimmedIpAddress = ipAddress.trim();
        String trimmedPassword = password.trim();

        // Kiểm tra xem các trường có rỗng sau khi trim không
        if (trimmedPcName.isEmpty() || trimmedPcUsername.isEmpty()
                || trimmedIpAddress.isEmpty() || trimmedPassword.isEmpty()) {
            model.addAttribute("error", "All fields are required and cannot be empty!");
            List<PC> computers = pcRepository.findByOwnerUsername(username);
            model.addAttribute("computers", computers);
            model.addAttribute("username", username);
            return "home";
        }

        Optional<PC> existPC = pcRepository.findByPcNameAndOwnerUsername(trimmedPcName, username);

        if (existPC.isPresent() && existPC.get().getOwnerUsername().equals(username)) {
            model.addAttribute("error", "PC name already exists");
            return "home";
        } else {
            // Kiểm tra kết nối SSH trước khi lưu
            boolean sshSuccess = connectSSH.checkConnectSSH(trimmedIpAddress, port, trimmedPcUsername, trimmedPassword);
            if (!sshSuccess) {
                model.addAttribute("error", "SSH connection failed!");
            } else {
                // Luu vao db
                PC newPC = new PC(trimmedPcName, trimmedPcUsername, trimmedIpAddress, port, trimmedPassword, username);
                updateComputerStatus(newPC); // Cập nhật trạng thái cho máy mới thêm
                pcRepository.save(newPC);

                PCAccount newPCAccount = new PCAccount(trimmedPcUsername, trimmedPassword);
                pcAccountRepository.save(newPCAccount);

                model.addAttribute("message", "PC added successfully and SSH connected!");

            }

        }
        // Lấy danh sách PCs để hiển thị trên giao diện
        List<PC> computers = pcRepository.findByOwnerUsername(username);
        updateComputerStatuses(computers); // Cập nhật trạng thái sau khi thêm máy mới
        model.addAttribute("computers", computers);
        model.addAttribute("username", username);

        return "redirect:/home_{username}";
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
            Model model) {

        // Lấy user từ CSDL dựa trên username
        Optional<User> updateUser = userRepository.findByUsername(username);
        Optional<UserAccount> updateUserAcc = userAccountRepository.findByUsername(username);
        if (updateUser == null) {
            // Xử lý nếu không tìm thấy user, trả về trang lỗi hoặc redirect
            return "error-page";
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

        return "redirect:/home_" + username + "/information";

    }

    @GetMapping("/delete-account/{username}")
    public String deleteAccount(@PathVariable String username,
            HttpSession session,
            Model model) {
        try {
            // Check if user exists
            if (!userRepository.existsByUsername(username)) {
                model.addAttribute("error", "User not found");
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

            // Clear session
            session.invalidate();

            return "redirect:/";
        } catch (Exception e) {
            model.addAttribute("error", "Failed to delete account. Please try again.");
            return "redirect:/home_" + username + "/information";
        }
    }

    @GetMapping("/delete-pc/{username}/{pcName}")
    public String deletePC(@PathVariable String username,
            @PathVariable String pcName,
            HttpSession session,
            Model model) {
        String sessionUsername = (String) session.getAttribute("username");

        // Kiểm tra đăng nhập
        if (sessionUsername == null || !sessionUsername.equals(username)) {
            model.addAttribute("error", "Unauthorized access! You can only delete your own PC.");
            return "error";
        }

        try {
            // Tìm máy theo pcName và username
            Optional<PC> optionalPC = pcRepository.findByPcNameAndOwnerUsername(pcName, username);
            if (optionalPC.isEmpty()) {
                model.addAttribute("error", "PC not found or does not belong to this user.");
                return "redirect:/home_" + username;
            }

            PC pc = optionalPC.get();

            // Xoá tài khoản SSH của máy (nếu có)
            pcAccountRepository.deleteByUsername(pc.getPcUsername());

            // Xoá máy
            pcRepository.delete(pc);

            model.addAttribute("message", "PC deleted successfully.");
        } catch (Exception e) {
            model.addAttribute("error", "Failed to delete PC. Please try again.");
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
}
