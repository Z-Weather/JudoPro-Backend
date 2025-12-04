package cn.edu.bistu.cs.ir.controller;

import cn.edu.bistu.cs.ir.entity.UserFile;
import cn.edu.bistu.cs.ir.model.User;
import cn.edu.bistu.cs.ir.service.UserFileService;
import cn.edu.bistu.cs.ir.service.UserService;
import cn.edu.bistu.cs.ir.utils.FileUploadUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import java.util.*;
import java.util.Optional;

import okhttp3.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 文件上传控制器
 */
@RestController
@RequestMapping("/api/file")
@CrossOrigin(origins = "*")
public class FileUploadController {
    
    private static final Logger log = LoggerFactory.getLogger(FileUploadController.class);
    
    @Autowired
    private FileUploadUtils fileUploadUtils;
    
    @Autowired
    private UserFileService userFileService;

    @Autowired
    private UserService userService;
    
    /**
     * 获取当前登录用户ID - 支持Session认证和Spring Security认证
     */
    private Long getCurrentUserId(Authentication authentication, HttpServletRequest request) {
        log.info("🔍 开始获取当前用户ID...");

        // 步骤1: 首先尝试从Session中获取用户ID（登录时设置的）
        Long userIdFromSession = (Long) request.getSession().getAttribute("userId");
        if (userIdFromSession != null) {
            log.info("✅ 从Session获取用户ID成功: {}", userIdFromSession);
            return userIdFromSession;
        } else {
            log.warn("❌ Session中未找到userId，检查Session信息:");
            log.warn("   - Session ID: {}", request.getSession().getId());
            log.warn("   - Session中存在的属性: {}", request.getSession().getAttributeNames().toString());

            // 检查Session中是否有currentUser对象
            Object currentUser = request.getSession().getAttribute("currentUser");
            if (currentUser != null) {
                log.warn("   - currentUser对象: {}", currentUser.getClass().getName());
                if (currentUser instanceof cn.edu.bistu.cs.ir.model.User) {
                    cn.edu.bistu.cs.ir.model.User sessionUser = (cn.edu.bistu.cs.ir.model.User) currentUser;
                    log.warn("   - currentUser用户ID: {}", sessionUser.getId());
                    return sessionUser.getId();
                }
            }
        }

        // 步骤2: 如果Session中没有，尝试从Spring Security获取
        log.info("🔄 尝试从Spring Security获取用户信息...");
        log.info("   - Authentication对象: {}", authentication);
        log.info("   - Authentication是否为null: {}", authentication == null);

        if (authentication != null) {
            log.info("   - Principal: {}", authentication.getPrincipal());
            log.info("   - Principal是否为null: {}", authentication.getPrincipal() == null);
            log.info("   - Principal类型: {}", authentication.getPrincipal() != null ? authentication.getPrincipal().getClass().getName() : "null");

            Object principal = authentication.getPrincipal();
            if (principal != null) {
                if (principal instanceof cn.edu.bistu.cs.ir.model.User) {
                    Long userId = ((cn.edu.bistu.cs.ir.model.User) principal).getId();
                    log.info("✅ 从Spring Security获取用户ID成功: {}", userId);
                    return userId;
                } else if (principal instanceof String) {
                    log.warn("⚠️ Principal是字符串类型: {}", principal);
                    // 尝试通过用户名查找用户
                    try {
                        Optional<cn.edu.bistu.cs.ir.model.User> userOpt = userService.findByUsername((String) principal);
                        if (userOpt.isPresent()) {
                            Long userId = userOpt.get().getId();
                            log.info("✅ 通过用户名查找获取用户ID成功: {}", userId);
                            return userId;
                        } else {
                            log.warn("❌ 通过用户名未找到用户: {}", principal);
                        }
                    } catch (Exception e) {
                        log.error("❌ 通过用户名查找用户时出错: {}", e.getMessage(), e);
                    }
                }
            }
        }

        // 步骤3: 都没有的话，使用测试用户
        log.warn("🚨 所有认证方式都失败，使用测试用户逻辑");
        return getOrCreateTestUser();
    }

    /**
     * 获取或创建测试用户（用于未登录情况下的测试）
     */
    private Long getOrCreateTestUser() {
        try {
            // 尝试查找测试用户
            Optional<User> testUserOpt = userService.findByUsername("testuser");
            if (testUserOpt.isPresent()) {
                Long userId = testUserOpt.get().getId();
                log.info("找到测试用户，ID: {}", userId);
                return userId;
            }

            // 创建测试用户
            log.info("未找到测试用户，创建新的测试用户...");
            User testUser = userService.registerByEmail("testuser", "test@example.com", "test123");
            log.info("创建测试用户成功，ID: {}", testUser.getId());
            return testUser.getId();

        } catch (Exception e) {
            log.error("创建测试用户失败: {}", e.getMessage(), e);
            throw new IllegalStateException("无法创建测试用户: " + e.getMessage());
        }
    }
    
    /**
     * 上传图片
     */
    @PostMapping("/upload/image")
    public ResponseEntity<Map<String, Object>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "category", required = false, defaultValue = "general") String category,
            Authentication authentication,
            HttpServletRequest request) {

        log.info("=== 图片上传API调用 ===");
        log.info("文件名: {}", file != null ? file.getOriginalFilename() : "null");
        log.info("文件大小: {} bytes", file != null ? file.getSize() : "null");
        log.info("描述: {}", description);
        log.info("分类: {}", category);

        Map<String, Object> response = new HashMap<>();

        try {
            if (file == null || file.isEmpty()) {
                log.warn("❌ 上传文件为空");
                response.put("success", false);
                response.put("message", "请选择要上传的文件");
                return ResponseEntity.badRequest().body(response);
            }

            // 获取当前用户ID
            Long userId = getCurrentUserId(authentication, request);
            log.info("当前用户ID: {}", userId);

            // 上传文件到服务器
            log.info("开始上传文件到服务器...");
            String fileUrl = fileUploadUtils.uploadImage(file);
            log.info("文件上传成功，URL: {}", fileUrl);

            // 上传图片并保存到数据库
            log.info("开始保存文件信息到数据库...");
            UserFile userFile = userFileService.saveImageFile(userId, file, fileUrl);
            log.info("文件信息保存成功，数据库ID: {}", userFile.getId());

            response.put("success", true);
            response.put("message", "图片上传成功");
            response.put("data", Map.of(
                "id", userFile.getId(),
                "url", userFile.getFileUrl(),
                "filename", userFile.getOriginalFilename(),
                "size", userFile.getFormattedFileSize(),
                "type", "image",
                "uploadTime", userFile.getUploadTime(),
                "description", description != null ? description : "",
                "category", category,
                "downloadCount", userFile.getDownloadCount()
            ));

            log.info("✅ 图片上传完成，文件ID: {}, URL: {}", userFile.getId(), userFile.getFileUrl());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("❌ 图片上传参数错误：{}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (IllegalStateException e) {
            log.warn("用户认证错误：{}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);

        } catch (Exception e) {
            log.error("图片上传失败", e);
            response.put("success", false);
            response.put("message", "图片上传失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 上传视频
     */
    @PostMapping("/upload/video")
    public ResponseEntity<Map<String, Object>> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description,
            Authentication authentication,
            HttpServletRequest request) {

        Map<String, Object> response = new HashMap<>();

        try {
            // 获取当前用户ID
            Long userId = getCurrentUserId(authentication, request);

            // 上传文件到服务器
            String fileUrl = fileUploadUtils.uploadVideo(file);

            // 上传视频并保存到数据库
            UserFile userFile = userFileService.saveVideoFile(userId, file, fileUrl);

            response.put("success", true);
            response.put("message", "视频上传成功");
            response.put("data", Map.of(
                "id", userFile.getId(),
                "url", userFile.getFileUrl(),
                "filename", userFile.getOriginalFilename(),
                "size", userFile.getFormattedFileSize(),
                "type", "video",
                "uploadTime", userFile.getUploadTime(),
                "description", description != null ? description : "",
                "downloadCount", userFile.getDownloadCount()
            ));

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("视频上传参数错误：{}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (IllegalStateException e) {
            log.warn("用户认证错误：{}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);

        } catch (Exception e) {
            log.error("视频上传失败", e);
            response.put("success", false);
            response.put("message", "视频上传失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 获取文件信息
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getFileInfo(
            @RequestParam("url") String fileUrl) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            FileUploadUtils.FileInfo fileInfo = fileUploadUtils.getFileInfo(fileUrl);
            
            if (fileInfo != null) {
                response.put("success", true);
                response.put("data", Map.of(
                    "filename", fileInfo.getFilename(),
                    "size", fileInfo.getFormattedSize(),
                    "extension", fileInfo.getExtension(),
                    "url", fileInfo.getUrl()
                ));
            } else {
                response.put("success", false);
                response.put("message", "文件不存在");
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取文件信息失败", e);
            response.put("success", false);
            response.put("message", "获取文件信息失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 删除文件（通过文件ID）
     */
    @DeleteMapping("/delete/{fileId}")
    public ResponseEntity<Map<String, Object>> deleteFile(
            @PathVariable Long fileId,
            Authentication authentication,
            HttpServletRequest request) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 获取当前用户ID
            Long userId = getCurrentUserId(authentication, request);
            
            // 删除用户文件
            boolean deleted = userFileService.deleteUserFile(userId, fileId);
            
            if (deleted) {
                response.put("success", true);
                response.put("message", "文件删除成功");
            } else {
                response.put("success", false);
                response.put("message", "文件删除失败，文件不存在或无权限删除");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalStateException e) {
            log.warn("用户认证错误：{}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            
        } catch (Exception e) {
            log.error("删除文件失败", e);
            response.put("success", false);
            response.put("message", "删除文件失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 删除文件（通过文件URL，兼容旧接口）
     */
    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, Object>> deleteFileByUrl(
            @RequestParam("url") String fileUrl,
            Authentication authentication,
            HttpServletRequest request) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 获取当前用户ID
            Long userId = getCurrentUserId(authentication, request);
            
            // 通过URL删除用户文件
            boolean deleted = userFileService.deleteUserFileByUrl(userId, fileUrl);
            
            if (deleted) {
                response.put("success", true);
                response.put("message", "文件删除成功");
            } else {
                response.put("success", false);
                response.put("message", "文件删除失败，文件不存在或无权限删除");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalStateException e) {
            log.warn("用户认证错误：{}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            
        } catch (Exception e) {
            log.error("删除文件失败", e);
            response.put("success", false);
            response.put("message", "删除文件失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 批量上传图片
     */
    @PostMapping("/upload/images")
    public ResponseEntity<Map<String, Object>> uploadImages(
            @RequestParam("files") MultipartFile[] files,
            Authentication authentication,
            HttpServletRequest request) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 获取当前用户ID
            Long userId = getCurrentUserId(authentication, request);
            
            if (files == null || files.length == 0) {
                response.put("success", false);
                response.put("message", "请选择要上传的图片文件");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (files.length > 10) {
                response.put("success", false);
                response.put("message", "一次最多上传10个文件");
                return ResponseEntity.badRequest().body(response);
            }
            
            Map<String, Object> results = new HashMap<>();
            int successCount = 0;
            
            for (int i = 0; i < files.length; i++) {
                MultipartFile file = files[i];
                try {
                    // 上传文件到服务器
                    String fileUrl = fileUploadUtils.uploadImage(file);
                    UserFile userFile = userFileService.saveImageFile(userId, file, fileUrl);
                    
                    results.put("file_" + i, Map.of(
                        "success", true,
                        "id", userFile.getId(),
                        "url", userFile.getFileUrl(),
                        "filename", userFile.getOriginalFilename(),
                        "size", userFile.getFormattedFileSize()
                    ));
                    successCount++;
                    
                } catch (Exception e) {
                    results.put("file_" + i, Map.of(
                        "success", false,
                        "filename", file.getOriginalFilename(),
                        "error", e.getMessage()
                    ));
                }
            }
            
            response.put("success", true);
            response.put("message", String.format("批量上传完成，成功：%d，失败：%d", 
                successCount, files.length - successCount));
            response.put("data", results);
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalStateException e) {
            log.warn("用户认证错误：{}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            
        } catch (Exception e) {
            log.error("批量上传图片失败", e);
            response.put("success", false);
            response.put("message", "批量上传失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 获取用户文件列表
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getUserFiles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String filename,
            Authentication authentication,
            HttpServletRequest request) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 获取当前用户ID
            Long userId = getCurrentUserId(authentication, request);
            
            // 创建分页对象
            Pageable pageable = PageRequest.of(page, size);

            // 获取用户文件列表
            Page<UserFile> userFiles;
            if (type != null && !type.isEmpty()) {
                userFiles = userFileService.getUserFilesByType(userId, type, pageable);
            } else if (filename != null && !filename.isEmpty()) {
                userFiles = userFileService.searchUserFiles(userId, filename, pageable);
            } else {
                userFiles = userFileService.getUserFiles(userId, pageable);
            }
            
            response.put("success", true);
            response.put("data", Map.of(
                "files", userFiles.getContent(),
                "totalElements", userFiles.getTotalElements(),
                "totalPages", userFiles.getTotalPages(),
                "currentPage", userFiles.getNumber(),
                "size", userFiles.getSize(),
                "hasNext", userFiles.hasNext(),
                "hasPrevious", userFiles.hasPrevious()
            ));
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalStateException e) {
            log.warn("用户认证错误：{}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            
        } catch (Exception e) {
            log.error("获取用户文件列表失败", e);
            response.put("success", false);
            response.put("message", "获取文件列表失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 获取文件详情
     */
    @GetMapping("/detail/{fileId}")
    public ResponseEntity<Map<String, Object>> getFileDetail(
            @PathVariable Long fileId,
            Authentication authentication,
            HttpServletRequest request) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 获取当前用户ID
            Long userId = getCurrentUserId(authentication, request);
            
            // 获取文件详情
            UserFile userFile = userFileService.getUserFile(userId, fileId).orElse(null);
            
            if (userFile != null) {
                response.put("success", true);
                response.put("data", userFile);
            } else {
                response.put("success", false);
                response.put("message", "文件不存在或无权限访问");
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalStateException e) {
            log.warn("用户认证错误：{}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            
        } catch (Exception e) {
            log.error("获取文件详情失败", e);
            response.put("success", false);
            response.put("message", "获取文件详情失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 下载文件（增加下载次数）
     */
    @GetMapping("/download/{fileId}")
    public ResponseEntity<Map<String, Object>> downloadFile(
            @PathVariable Long fileId,
            Authentication authentication,
            HttpServletRequest request) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 获取当前用户ID
            Long userId = getCurrentUserId(authentication, request);
            
            // 增加下载次数并获取文件信息
            boolean success = userFileService.incrementDownloadCount(fileId);
            if (!success) {
                response.put("success", false);
                response.put("message", "文件不存在或无权限下载");
                return ResponseEntity.notFound().build();
            }

            UserFile userFile = userFileService.getUserFile(userId, fileId).orElse(null);
            
            if (userFile != null) {
                response.put("success", true);
                response.put("message", "文件下载成功");
                response.put("data", Map.of(
                    "url", userFile.getFileUrl(),
                    "filename", userFile.getOriginalFilename(),
                    "downloadCount", userFile.getDownloadCount()
                ));
            } else {
                response.put("success", false);
                response.put("message", "文件不存在或无权限下载");
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalStateException e) {
            log.warn("用户认证错误：{}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            
        } catch (Exception e) {
            log.error("文件下载失败", e);
            response.put("success", false);
            response.put("message", "文件下载失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 获取用户文件统计信息
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getUserFileStatistics(
            Authentication authentication,
            HttpServletRequest request) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 获取当前用户ID
            Long userId = getCurrentUserId(authentication, request);
            
            // 获取用户文件统计信息
            UserFileService.UserFileStatistics statistics = userFileService.getUserFileStatistics(userId);
            
            response.put("success", true);
            response.put("data", statistics);
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalStateException e) {
            log.warn("用户认证错误：{}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            
        } catch (Exception e) {
            log.error("获取用户文件统计失败", e);
            response.put("success", false);
            response.put("message", "获取统计信息失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 获取最近上传的文件
     */
    @GetMapping("/recent")
    public ResponseEntity<Map<String, Object>> getRecentFiles(
            Authentication authentication,
            HttpServletRequest request) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 获取当前用户ID
            Long userId = getCurrentUserId(authentication, request);
            
            // 获取最近上传的文件
            List<UserFile> recentFiles = userFileService.getRecentFiles(userId);
            
            response.put("success", true);
            response.put("data", recentFiles);
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalStateException e) {
            log.warn("用户认证错误：{}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            
        } catch (Exception e) {
            log.error("获取最近文件失败", e);
            response.put("success", false);
            response.put("message", "获取最近文件失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 获取热门下载文件
     */
    @GetMapping("/popular")
    public ResponseEntity<Map<String, Object>> getPopularFiles(
            Authentication authentication,
            HttpServletRequest request) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 获取当前用户ID
            Long userId = getCurrentUserId(authentication, request);
            
            // 获取热门下载文件
            List<UserFile> popularFiles = userFileService.getPopularFiles(userId);
            
            response.put("success", true);
            response.put("data", popularFiles);
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalStateException e) {
            log.warn("用户认证错误：{}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            
        } catch (Exception e) {
            log.error("获取热门文件失败", e);
            response.put("success", false);
            response.put("message", "获取热门文件失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 更新文件描述
     */
    @PutMapping("/description/{fileId}")
    public ResponseEntity<Map<String, Object>> updateFileDescription(
            @PathVariable Long fileId,
            @RequestParam String description,
            Authentication authentication,
            HttpServletRequest request) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 获取当前用户ID
            Long userId = getCurrentUserId(authentication, request);
            
            // 更新文件描述
            boolean updated = userFileService.updateFileDescription(userId, fileId, description);
            
            if (updated) {
                response.put("success", true);
                response.put("message", "文件描述更新成功");
            } else {
                response.put("success", false);
                response.put("message", "文件描述更新失败，文件不存在或无权限修改");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalStateException e) {
            log.warn("用户认证错误：{}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            
        } catch (Exception e) {
            log.error("更新文件描述失败", e);
            response.put("success", false);
            response.put("message", "更新文件描述失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * AI媒体分析接口 - 支持图片和视频，同时调用外部模型和Python微服务
     * 支持两种模式：
     * 1. Base64模式：直接传入Base64数据（向后兼容）
     * 2. URL模式：传入已上传文件的URL（推荐）
     */
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeMedia(
            @RequestParam(value = "image", required = false) String mediaBase64,  // Base64数据（可选）
            @RequestParam(value = "fileUrl", required = false) String fileUrl,     // 文件URL（可选）
            @RequestParam("prompt") String prompt,
            @RequestParam(value = "mediaType", defaultValue = "image") String mediaType) {

        log.info("🎯 ===== 开始双重AI媒体分析请求 =====");
        log.info("📝 提示词: {}", prompt);
        log.info("🎬 媒体类型: {}", mediaType);

        // 验证输入参数
        if (mediaBase64 == null && fileUrl == null) {
            log.error("❌ 缺少必需参数：必须提供 image (Base64) 或 fileUrl 中的一个");
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "缺少必需参数：必须提供 image (Base64) 或 fileUrl 中的一个");
            return ResponseEntity.badRequest().body(response);
        }

        if (mediaBase64 != null && fileUrl != null) {
            log.error("❌ 参数冲突：不能同时提供 image 和 fileUrl 参数");
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "参数冲突：不能同时提供 image 和 fileUrl 参数");
            return ResponseEntity.badRequest().body(response);
        }

        // 确定传输模式
        boolean useBase64Mode = mediaBase64 != null;
        log.info("📋 传输模式: {}", useBase64Mode ? "Base64模式" : "URL模式");

        if (useBase64Mode) {
            log.info("📁 Base64数据长度: {} 字符", mediaBase64.length());
            log.info("🔍 Base64前缀: {}", mediaBase64.length() > 20 ? mediaBase64.substring(0, 20) + "..." : mediaBase64);
        } else {
            log.info("🔗 文件URL: {}", fileUrl);
        }

        Map<String, Object> response = new HashMap<>();

        try {
            // 验证媒体类型
            if (!"image".equals(mediaType) && !"video".equals(mediaType)) {
                throw new IllegalArgumentException("不支持的媒体类型: " + mediaType + "。仅支持 'image' 或 'video'");
            }

            // 初始化结果变量
            String externalModelResult = null;
            String pythonServiceResult = null;
            Map<String, Object> pythonServiceData = null;
            String annotatedMediaData = null;

            log.info("🔄 ===== 开始并行调用两个模型 =====");

            // 根据传输模式获取媒体数据
            String mediaDataForProcessing;
            if (useBase64Mode) {
                mediaDataForProcessing = mediaBase64;
            } else {
                // 从文件URL读取Base64数据
                try {
                    mediaDataForProcessing = readBase64FromUrl(fileUrl, mediaType);
                    log.info("📥 从文件URL读取Base64数据成功 - 长度: {} 字符", mediaDataForProcessing.length());
                } catch (Exception e) {
                    log.error("❌ 从文件URL读取数据失败: {}", e.getMessage());
                    throw new RuntimeException("无法从文件URL读取数据: " + e.getMessage(), e);
                }
            }

            // 1. 调用外部模型（火山引擎）获取文字说明
            log.info("🔥 步骤1: 开始调用外部模型获取{}文字说明...", mediaType);
            try {
                externalModelResult = callExternalModel(mediaDataForProcessing, prompt, mediaType);
                log.info("✅ 外部模型调用成功 - 响应长度: {} 字符", externalModelResult.length());
            } catch (Exception e) {
                log.error("❌ 外部模型调用失败: {}", e.getMessage());
                throw e;
            }

            // 2. 调用Python微服务获取标点媒体数据
            log.info("🐍 步骤2: 开始调用Python微服务获取标点{}...", mediaType);
            try {
                pythonServiceResult = callPythonMicroservice(mediaDataForProcessing, mediaType);
                log.info("✅ Python微服务调用成功 - 响应长度: {} 字符", pythonServiceResult.length());

                // 解析Python微服务响应
                pythonServiceData = objectMapper.readValue(pythonServiceResult, Map.class);
                Integer code = (Integer) pythonServiceData.get("code");
                String resultType = (String) pythonServiceData.get("result_type");

                // 根据媒体类型提取相应数据
                if ("image".equals(mediaType)) {
                    annotatedMediaData = (String) pythonServiceData.get("image_base64_data");
                } else if ("video".equals(mediaType)) {
                    annotatedMediaData = (String) pythonServiceData.get("video_base64_data");
                }

                log.info("📊 Python响应解析 - code: {}, result_type: {}, 是否有标点{}数据: {}",
                    code, resultType, mediaType, annotatedMediaData != null && !annotatedMediaData.isEmpty());
            } catch (Exception e) {
                log.error("❌ Python微服务调用失败: {}", e.getMessage());
                throw e;
            }

            // 3. 构造组合返回结果
            log.info("🏗️  步骤3: 构造组合响应结果...");
            response.put("success", true);
            response.put("message", "双重分析成功");
            response.put("media_type", mediaType);
            response.put("transfer_mode", useBase64Mode ? "Base64模式" : "URL模式");

            // 外部模型结果（文字说明）
            response.put("external_model_result", externalModelResult);

            // Python微服务结果（标点媒体）
            Map<String, Object> pythonResult = new HashMap<>();
            if (pythonServiceData != null) {
                pythonResult.put("code", pythonServiceData.get("code"));
                pythonResult.put("result_type", pythonServiceData.get("result_type"));

                // 动态设置标点数据字段
                if ("image".equals(mediaType)) {
                    pythonResult.put("annotated_image", annotatedMediaData);
                } else if ("video".equals(mediaType)) {
                    pythonResult.put("annotated_video", annotatedMediaData);
                }
                pythonResult.put("annotated_media", annotatedMediaData); // 通用字段
            }
            response.put("python_service_result", pythonResult);

            log.info("📈 结果统计:");
            log.info("   - 外部模型文字说明: {}", externalModelResult != null ? "✅" : "❌");
            log.info("   - Python标点{}: {}", mediaType, annotatedMediaData != null ? "✅" : "❌");
            log.info("✅ ===== 双重AI{}分析请求完成 =====", mediaType);

        } catch (Exception e) {
            log.error("💥 ===== 双重AI{}分析请求失败 =====", mediaType != null ? mediaType : "未知");
            log.error("❌ 异常类型: {}", e.getClass().getSimpleName());
            log.error("❌ 异常消息: {}", e.getMessage());

            // 打印异常堆栈的关键信息
            StackTraceElement[] stackTrace = e.getStackTrace();
            for (int i = 0; i < Math.min(3, stackTrace.length); i++) {
                log.error("❌ 堆栈[{}]: {}.{}():{}",
                    i,
                    stackTrace[i].getClassName(),
                    stackTrace[i].getMethodName(),
                    stackTrace[i].getLineNumber());
            }

            response.put("success", false);
            response.put("message", "双重分析失败: " + e.getMessage());
            response.put("error_type", e.getClass().getSimpleName());
            response.put("media_type", mediaType);
            response.put("transfer_mode", useBase64Mode ? "Base64模式" : "URL模式");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * AI媒体分析接口（异步版本）- 适用于大文件处理
     * 立即返回任务ID，后台异步处理分析任务
     */
    @PostMapping("/analyze-async")
    public ResponseEntity<Map<String, Object>> analyzeMediaAsync(
            @RequestParam(value = "image", required = false) String mediaBase64,  // Base64数据（可选）
            @RequestParam(value = "fileUrl", required = false) String fileUrl,     // 文件URL（可选）
            @RequestParam("prompt") String prompt,
            @RequestParam(value = "mediaType", defaultValue = "image") String mediaType,
            @RequestParam(value = "callbackUrl", required = false) String callbackUrl) {  // 回调URL（可选）

        log.info("🚀 ===== 开始异步AI媒体分析请求 =====");
        log.info("📝 提示词: {}", prompt);
        log.info("🎬 媒体类型: {}", mediaType);
        log.info("📞 回调URL: {}", callbackUrl != null ? callbackUrl : "无");

        // 验证输入参数
        if (mediaBase64 == null && fileUrl == null) {
            log.error("❌ 缺少必需参数：必须提供 image (Base64) 或 fileUrl 中的一个");
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "缺少必需参数：必须提供 image (Base64) 或 fileUrl 中的一个");
            return ResponseEntity.badRequest().body(response);
        }

        if (mediaBase64 != null && fileUrl != null) {
            log.error("❌ 参数冲突：不能同时提供 image 和 fileUrl 参数");
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "参数冲突：不能同时提供 image 和 fileUrl 参数");
            return ResponseEntity.badRequest().body(response);
        }

        // 验证媒体类型
        if (!"image".equals(mediaType) && !"video".equals(mediaType)) {
            log.error("❌ 不支持的媒体类型: {}", mediaType);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "不支持的媒体类型: " + mediaType + "。仅支持 'image' 或 'video'");
            return ResponseEntity.badRequest().body(response);
        }

        // 生成任务ID
        String taskId = UUID.randomUUID().toString();

        // 确定传输模式
        boolean useBase64Mode = mediaBase64 != null;
        log.info("📋 传输模式: {}", useBase64Mode ? "Base64模式" : "URL模式");
        log.info("🆔 任务ID: {}", taskId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "异步分析任务已启动");
        response.put("task_id", taskId);
        response.put("status", "processing");
        response.put("media_type", mediaType);
        response.put("transfer_mode", useBase64Mode ? "Base64模式" : "URL模式");

        // 异步启动分析任务
        CompletableFuture.runAsync(() -> {
            try {
                log.info("🔄 [{}] 开始执行异步分析任务...", taskId);
                executeAsyncAnalysis(taskId, mediaBase64, fileUrl, prompt, mediaType, callbackUrl);
                log.info("✅ [{}] 异步分析任务执行完成", taskId);
            } catch (Exception e) {
                log.error("💥 [{}] 异步分析任务执行失败: {}", taskId, e.getMessage(), e);

                // 如果有回调URL，发送失败通知
                if (callbackUrl != null && !callbackUrl.isEmpty()) {
                    sendFailureCallback(callbackUrl, taskId, e.getMessage());
                }
            }
        });

        log.info("🎯 ===== 异步AI媒体分析请求已接受 =====");
        return ResponseEntity.ok(response);
    }

    /**
     * 从文件URL读取Base64数据
     */
    private String readBase64FromUrl(String fileUrl, String mediaType) throws IOException {
        log.info("📥 开始从URL读取文件: {}", fileUrl);

        // 如果是本地文件路径
        if (fileUrl.startsWith("/") || fileUrl.startsWith("file:")) {
            String filePath = fileUrl.startsWith("file:") ? fileUrl.substring(5) : fileUrl;
            java.io.File file = new java.io.File(filePath);

            if (!file.exists()) {
                throw new IOException("文件不存在: " + filePath);
            }

            log.info("📁 读取本地文件: {} (大小: {} bytes)", filePath, file.length());

            try (java.io.FileInputStream fis = new java.io.FileInputStream(file);
                 java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    bos.write(buffer, 0, bytesRead);
                }

                String base64Data = java.util.Base64.getEncoder().encodeToString(bos.toByteArray());
                log.info("✅ 本地文件Base64编码完成 - 长度: {} 字符", base64Data.length());
                return base64Data;
            }
        }

        // 如果是HTTP/HTTPS URL
        if (fileUrl.startsWith("http://") || fileUrl.startsWith("https://")) {
            log.info("🌐 下载远程文件: {}", fileUrl);

            java.net.URL url = new java.net.URL(fileUrl);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("HTTP请求失败: " + responseCode);
            }

            try (java.io.InputStream is = connection.getInputStream();
                 java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {

                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalBytes = 0;

                while ((bytesRead = is.read(buffer)) != -1) {
                    bos.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }

                String base64Data = java.util.Base64.getEncoder().encodeToString(bos.toByteArray());
                log.info("✅ 远程文件下载完成 - 大小: {} bytes, Base64长度: {} 字符", totalBytes, base64Data.length());
                return base64Data;
            } finally {
                connection.disconnect();
            }
        }

        // 如果是相对路径（基于上传目录）
        String uploadDir = fileUploadUtils.getUploadPath();
        String fullPath = uploadDir + "/" + fileUrl;
        java.io.File file = new java.io.File(fullPath);

        if (file.exists()) {
            log.info("📁 读取上传目录文件: {} (大小: {} bytes)", fullPath, file.length());

            try (java.io.FileInputStream fis = new java.io.FileInputStream(file);
                 java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    bos.write(buffer, 0, bytesRead);
                }

                String base64Data = java.util.Base64.getEncoder().encodeToString(bos.toByteArray());
                log.info("✅ 上传文件Base64编码完成 - 长度: {} 字符", base64Data.length());
                return base64Data;
            }
        }

        throw new IOException("无法识别的文件URL格式: " + fileUrl);
    }

    /**
     * 执行异步分析任务
     */
    private void executeAsyncAnalysis(String taskId, String mediaBase64, String fileUrl,
                                     String prompt, String mediaType, String callbackUrl) {
        log.info("🔄 [{}] 开始异步分析任务执行", taskId);

        Map<String, Object> analysisResult = new HashMap<>();

        try {
            // 根据传输模式获取媒体数据
            String mediaDataForProcessing;
            boolean useBase64Mode = mediaBase64 != null;

            if (useBase64Mode) {
                mediaDataForProcessing = mediaBase64;
            } else {
                // 从文件URL读取Base64数据
                mediaDataForProcessing = readBase64FromUrl(fileUrl, mediaType);
                log.info("📥 [{}] 从文件URL读取Base64数据成功", taskId);
            }

            // 1. 调用外部模型获取文字说明
            log.info("🔥 [{}] 步骤1: 调用外部模型", taskId);
            String externalModelResult = callExternalModel(mediaDataForProcessing, prompt, mediaType);

            // 2. 调用Python微服务获取标点媒体数据
            log.info("🐍 [{}] 步骤2: 调用Python微服务", taskId);
            String pythonServiceResult = callPythonMicroservice(mediaDataForProcessing, mediaType);

            // 解析Python微服务响应
            Map<String, Object> pythonServiceData = objectMapper.readValue(pythonServiceResult, Map.class);
            String annotatedMediaData = null;

            if ("image".equals(mediaType)) {
                annotatedMediaData = (String) pythonServiceData.get("image_base64_data");
            } else if ("video".equals(mediaType)) {
                annotatedMediaData = (String) pythonServiceData.get("video_base64_data");
            }

            // 构造分析结果
            analysisResult.put("success", true);
            analysisResult.put("message", "异步分析完成");
            analysisResult.put("media_type", mediaType);
            analysisResult.put("transfer_mode", useBase64Mode ? "Base64模式" : "URL模式");
            analysisResult.put("external_model_result", externalModelResult);

            Map<String, Object> pythonResult = new HashMap<>();
            pythonResult.put("code", pythonServiceData.get("code"));
            pythonResult.put("result_type", pythonServiceData.get("result_type"));

            if ("image".equals(mediaType)) {
                pythonResult.put("annotated_image", annotatedMediaData);
            } else if ("video".equals(mediaType)) {
                pythonResult.put("annotated_video", annotatedMediaData);
            }
            pythonResult.put("annotated_media", annotatedMediaData);
            analysisResult.put("python_service_result", pythonResult);

            log.info("✅ [{}] 异步分析任务成功完成", taskId);

            // 发送成功回调
            if (callbackUrl != null && !callbackUrl.isEmpty()) {
                sendSuccessCallback(callbackUrl, taskId, analysisResult);
            }

        } catch (Exception e) {
            log.error("💥 [{}] 异步分析任务执行失败: {}", taskId, e.getMessage(), e);
            analysisResult.put("success", false);
            analysisResult.put("message", "异步分析失败: " + e.getMessage());
            analysisResult.put("error_type", e.getClass().getSimpleName());

            // 发送失败回调
            if (callbackUrl != null && !callbackUrl.isEmpty()) {
                sendFailureCallback(callbackUrl, taskId, e.getMessage());
            }
        }
    }

    /**
     * 发送成功回调通知
     */
    private void sendSuccessCallback(String callbackUrl, String taskId, Map<String, Object> result) {
        try {
            Map<String, Object> callbackData = new HashMap<>();
            callbackData.put("task_id", taskId);
            callbackData.put("status", "completed");
            callbackData.put("success", true);
            callbackData.put("result", result);
            callbackData.put("timestamp", System.currentTimeMillis());

            String jsonResult = objectMapper.writeValueAsString(callbackData);

            okhttp3.MediaType mediaType = okhttp3.MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(mediaType, jsonResult);

            Request request = new Request.Builder()
                .url(callbackUrl)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

            try (okhttp3.Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    log.info("📞 [{}] 成功回调通知已发送 - 状态码: {}", taskId, response.code());
                } else {
                    log.warn("📞 [{}] 回调通知发送失败 - 状态码: {}", taskId, response.code());
                }
            }
        } catch (Exception e) {
            log.error("💥 [{}] 发送成功回调时出错: {}", taskId, e.getMessage(), e);
        }
    }

    /**
     * 发送失败回调通知
     */
    private void sendFailureCallback(String callbackUrl, String taskId, String errorMessage) {
        try {
            Map<String, Object> callbackData = new HashMap<>();
            callbackData.put("task_id", taskId);
            callbackData.put("status", "failed");
            callbackData.put("success", false);
            callbackData.put("error_message", errorMessage);
            callbackData.put("timestamp", System.currentTimeMillis());

            String jsonResult = objectMapper.writeValueAsString(callbackData);

            okhttp3.MediaType mediaType = okhttp3.MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(mediaType, jsonResult);

            Request request = new Request.Builder()
                .url(callbackUrl)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

            try (okhttp3.Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    log.info("📞 [{}] 失败回调通知已发送 - 状态码: {}", taskId, response.code());
                } else {
                    log.warn("📞 [{}] 失败回调通知发送失败 - 状态码: {}", taskId, response.code());
                }
            }
        } catch (Exception e) {
            log.error("💥 [{}] 发送失败回调时出错: {}", taskId, e.getMessage(), e);
        }
    }

    /**
     * 调用外部模型（火山引擎）获取文字说明 - 支持图片和视频
     */
    private String callExternalModel(String mediaBase64, String prompt, String mediaType) throws Exception {
        log.info("🏗️  构造外部模型请求体 - 媒体类型: {}", mediaType);

        // 根据媒体类型确定MIME前缀
        String mimePrefix;
        if ("image".equals(mediaType)) {
            mimePrefix = "data:image/jpeg;base64,";
        } else if ("video".equals(mediaType)) {
            mimePrefix = "data:video/mp4;base64,";
        } else {
            throw new IllegalArgumentException("不支持的媒体类型: " + mediaType);
        }

        log.info("🔍 使用MIME前缀: {}", mimePrefix);

        // 构造请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "ep-20251204141721-tgjzh");

        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");

        List<Map<String, Object>> content = new ArrayList<>();

        // 文本内容
        log.info("📝 添加文本内容...");
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        textContent.put("text", prompt);
        content.add(textContent);

        // 媒体内容（图片或视频）
        log.info("🎬 添加{}内容...", mediaType);
        Map<String, Object> mediaContent = new HashMap<>();
        mediaContent.put("type", "image_url"); // 外部模型统一使用image_url类型
        Map<String, String> mediaUrl = new HashMap<>();
        String fullMediaUrl = mimePrefix + mediaBase64;
        mediaUrl.put("url", fullMediaUrl);
        mediaContent.put("image_url", mediaUrl);
        content.add(mediaContent);

        message.put("content", content);
        messages.add(message);
        requestBody.put("messages", messages);

        log.info("✅ 外部模型请求体构造完成 - 包含 {} 个消息对象", messages.size());
        log.info("📤 发送请求到火山引擎...");

        // 发送HTTP请求
        String result = sendHttpRequest(requestBody);

        log.info("📨 收到火山引擎响应 - 响应长度: {} 字符", result.length());
        log.info("🔍 响应预览: {}", result.length() > 100 ? result.substring(0, 100) + "..." : result);

        return result;
    }

    /**
     * 调用Python微服务获取标点媒体数据 - 支持图片和视频
     */
    private String callPythonMicroservice(String mediaBase64, String mediaType) throws Exception {
        log.info("🐍 构造Python微服务请求体 - 媒体类型: {}", mediaType);

        // 按照规范构造发送给Python微服务的请求体
        Map<String, Object> pythonRequest = new HashMap<>();
        pythonRequest.put("type", mediaType); // "image" 或 "video"
        pythonRequest.put("data_base64", mediaBase64);

        log.info("📊 Python微服务请求参数:");
        log.info("   - type: {}", mediaType);
        log.info("   - data_base64长度: {} 字符", mediaBase64.length());
        log.info("📤 准备发送请求到Python微服务 (http://127.0.0.1:5000/analyze)...");

        // 发送HTTP请求到Python微服务
        String pythonResult = callPythonService(pythonRequest);

        log.info("📨 收到Python微服务响应 - 响应长度: {} 字符", pythonResult.length());
        log.info("🔍 响应预览: {}", pythonResult.length() > 200 ? pythonResult.substring(0, 200) + "..." : pythonResult);

        return pythonResult;
    }

    /**
     * 发送HTTP请求到Python微服务
     */
    private String callPythonService(Map<String, Object> requestBody) throws Exception {
        long startTime = System.currentTimeMillis();

        try {
            // 打印请求体详情
            String requestBodyStr = objectMapper.writeValueAsString(requestBody);
            log.info("🚀 Python微服务请求详情:");
            log.info("   URL: http://127.0.0.1:5000/analyze");
            log.info("   Method: POST");
            log.info("   Content-Type: application/json");
            log.info("   Body大小: {} 字符", requestBodyStr.length());
            log.info("   Body内容: {}", requestBodyStr);

            okhttp3.MediaType mediaType = okhttp3.MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(mediaType, requestBodyStr);

            Request request = new Request.Builder()
                .url("http://127.0.0.1:5000/analyze")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

            log.info("📤 发送请求到Python微服务...");

            try (okhttp3.Response response = client.newCall(request).execute()) {
                long endTime = System.currentTimeMillis();
                long duration = endTime - startTime;

                log.info("📥 收到Python微服务响应:");
                log.info("   状态码: {}", response.code());
                log.info("   响应时间: {}ms", duration);
                log.info("   响应消息: {}", response.message());
                log.info("   响应头: {}", response.headers());

                if (!response.isSuccessful()) {
                    log.error("❌ Python微服务请求失败 - 状态码: {}", response.code());
                    throw new RuntimeException("Python微服务返回错误: " + response.code() + " " + response.message());
                }

                String responseBody = response.body().string();
                log.info("📄 响应体大小: {} 字符", responseBody.length());
                log.info("📄 响应体内容: {}", responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody);

                return responseBody;
            }
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            log.error("💥 Python微服务请求异常 - 耗时: {}ms", duration, e);
            log.error("❌ 异常类型: {}", e.getClass().getSimpleName());
            log.error("❌ 异常消息: {}", e.getMessage());

            // 打印异常堆栈的详细信息
            StackTraceElement[] stackTrace = e.getStackTrace();
            if (stackTrace.length > 0) {
                log.error("❌ 异常位置: {}.{}():{}",
                    stackTrace[0].getClassName(),
                    stackTrace[0].getMethodName(),
                    stackTrace[0].getLineNumber());
            }

            throw new RuntimeException("Python微服务调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发送HTTP请求到火山引擎
     */
    private String sendHttpRequest(Map<String, Object> requestBody) {
        long startTime = System.currentTimeMillis();

        try {
            // 打印请求体详情
            String requestBodyStr = objectMapper.writeValueAsString(requestBody);
            log.info("🚀 HTTP请求详情:");
            log.info("   URL: https://ark.cn-beijing.volces.com/api/v3/chat/completions");
            log.info("   Method: POST");
            log.info("   Headers: Authorization=Bearer [HIDDEN], Content-Type=application/json");
            log.info("   Body大小: {} 字符", requestBodyStr.length());
            log.info("   Body预览: {}", requestBodyStr.length() > 200 ? requestBodyStr.substring(0, 200) + "..." : requestBodyStr);

            okhttp3.MediaType mediaType = okhttp3.MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(mediaType, requestBodyStr);

            Request request = new Request.Builder()
                .url("https://ark.cn-beijing.volces.com/api/v3/chat/completions")
                .addHeader("Authorization", "Bearer 80e00021-76e9-4844-b98b-2d342a17e164")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

            log.info("📤 发送HTTP请求...");

            try (okhttp3.Response response = client.newCall(request).execute()) {
                long endTime = System.currentTimeMillis();
                long duration = endTime - startTime;

                log.info("📥 收到HTTP响应:");
                log.info("   状态码: {}", response.code());
                log.info("   响应时间: {}ms", duration);
                log.info("   响应消息: {}", response.message());
                log.info("   响应头: {}", response.headers());

                if (!response.isSuccessful()) {
                    log.error("❌ HTTP请求失败 - 状态码: {}", response.code());
                }

                String responseBody = response.body().string();
                log.info("📄 响应体大小: {} 字符", responseBody.length());
                log.info("📄 响应体内容: {}", responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody);

                return responseBody;
            }
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            log.error("💥 HTTP请求异常 - 耗时: {}ms", duration, e);
            log.error("❌ 异常类型: {}", e.getClass().getSimpleName());
            log.error("❌ 异常消息: {}", e.getMessage());

            // 打印异常堆栈的详细信息
            StackTraceElement[] stackTrace = e.getStackTrace();
            if (stackTrace.length > 0) {
                log.error("❌ 异常位置: {}.{}():{}",
                    stackTrace[0].getClassName(),
                    stackTrace[0].getMethodName(),
                    stackTrace[0].getLineNumber());
            }

            throw new RuntimeException("HTTP请求失败: " + e.getMessage(), e);
        }
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build();
}