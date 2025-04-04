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
        //Lay username tu session
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

        //Kiem tra username tu session
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
                pcRepository.save(newPC);

                PCAccount newPCAccount = new PCAccount(trimmedPcUsername, trimmedPassword);
                pcAccountRepository.save(newPCAccount);

                model.addAttribute("message", "PC added successfully and SSH connected!");

            }

        }
        // Lấy danh sách PCs để hiển thị trên giao diện
        List<PC> computers = pcRepository.findByOwnerUsername(username);
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
                    () -> model.addAttribute("error", "User not found")
            );
        } else {
            // Nếu là menu mặc định, ta load danh sách máy tính
            List<PC> computers = pcRepository.findByOwnerUsername(username);
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

        // Có thể redirect về trang thông tin, hoặc trả về template
        // 1) Redirect để tránh submit form lặp:
        return "redirect:/home_" + username + "/information";

        // 2) Hoặc trả về template trực tiếp (không redirect):
        // return "user-information";
    }

}
