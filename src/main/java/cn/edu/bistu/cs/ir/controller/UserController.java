package cn.edu.bistu.cs.ir.controller;

import cn.edu.bistu.cs.ir.model.User;
import cn.edu.bistu.cs.ir.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "*")
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        log.info("=== 用户注册API调用 ===");
        log.info("接收到的用户数据: username={}, email={}", user.getUsername(), user.getEmail());
        log.info("密码长度: {}", user.getPassword() != null ? user.getPassword().length() : 0);

        try {
            User registeredUser = userService.registerByEmail(user.getUsername(), user.getEmail(), user.getPassword());
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "注册成功");
            response.put("user", registeredUser);

            log.info("注册成功，返回用户ID: {}", registeredUser.getId());
            log.info("响应数据: {}", response);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("注册失败，错误信息: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "注册失败: " + e.getMessage());
            log.info("错误响应数据: {}", response);
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginRequest) {
        String username = loginRequest.get("username");
        String password = loginRequest.get("password");

        // 首先尝试用用户名查找
        Optional<User> userOpt = userService.findByUsername(username);

        // 如果用户名找不到，尝试用邮箱查找
        if (!userOpt.isPresent()) {
            userOpt = userService.findByEmail(username);
        }

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (userService.getPasswordEncoder().matches(password, user.getPassword())) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "登录成功");
                response.put("user", user);
                return ResponseEntity.ok(response);
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "用户名或密码错误");
        return ResponseEntity.status(401).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        return ResponseEntity.ok(authentication.getPrincipal());
    }

    @GetMapping("/info/{userId}")
    public ResponseEntity<?> getUserInfo(@PathVariable Long userId) {
        try {
            Optional<User> userOpt = userService.findById(userId);
            if (userOpt.isPresent()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "获取用户信息成功");
                response.put("user", userOpt.get());
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "用户不存在");
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取用户信息失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}