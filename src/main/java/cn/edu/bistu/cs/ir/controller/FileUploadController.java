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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * AI视觉分析接口 - 同时调用外部模型和Python微服务
     */
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeImage(
            @RequestParam("image") String imageBase64,
            @RequestParam("prompt") String prompt) {

        log.info("🎯 ===== 开始双重AI分析请求 =====");
        log.info("📝 提示词: {}", prompt);
        log.info("🖼️  图片Base64长度: {} 字符", imageBase64.length());
        log.info("🔍 Base64前缀: {}", imageBase64.length() > 20 ? imageBase64.substring(0, 20) + "..." : imageBase64);

        Map<String, Object> response = new HashMap<>();

        try {
            // 初始化结果变量
            String externalModelResult = null;
            String pythonServiceResult = null;
            Map<String, Object> pythonServiceData = null;
            String annotatedImageData = null;

            log.info("🔄 ===== 开始并行调用两个模型 =====");

            // 1. 调用外部模型（火山引擎）获取文字说明
            log.info("🔥 步骤1: 开始调用外部模型获取文字说明...");
            try {
                externalModelResult = callExternalModel(imageBase64, prompt);
                log.info("✅ 外部模型调用成功 - 响应长度: {} 字符", externalModelResult.length());
            } catch (Exception e) {
                log.error("❌ 外部模型调用失败: {}", e.getMessage());
                throw e;
            }

            // 2. 调用Python微服务获取标点图片数据
            log.info("🐍 步骤2: 开始调用Python微服务获取标点图片...");
            try {
                pythonServiceResult = callPythonMicroservice(imageBase64, "image");
                log.info("✅ Python微服务调用成功 - 响应长度: {} 字符", pythonServiceResult.length());

                // 解析Python微服务响应
                pythonServiceData = objectMapper.readValue(pythonServiceResult, Map.class);
                Integer code = (Integer) pythonServiceData.get("code");
                String resultType = (String) pythonServiceData.get("result_type");
                annotatedImageData = (String) pythonServiceData.get("image_base64_data");

                log.info("📊 Python响应解析 - code: {}, result_type: {}, 是否有图片数据: {}",
                    code, resultType, annotatedImageData != null && !annotatedImageData.isEmpty());
            } catch (Exception e) {
                log.error("❌ Python微服务调用失败: {}", e.getMessage());
                throw e;
            }

            // 3. 构造组合返回结果
            log.info("🏗️  步骤3: 构造组合响应结果...");
            response.put("success", true);
            response.put("message", "双重分析成功");

            // 外部模型结果（文字说明）
            response.put("external_model_result", externalModelResult);

            // Python微服务结果（标点图片）
            Map<String, Object> pythonResult = new HashMap<>();
            if (pythonServiceData != null) {
                pythonResult.put("code", pythonServiceData.get("code"));
                pythonResult.put("result_type", pythonServiceData.get("result_type"));
                pythonResult.put("annotated_image", annotatedImageData);
            }
            response.put("python_service_result", pythonResult);

            log.info("📈 结果统计:");
            log.info("   - 外部模型文字说明: {}", externalModelResult != null ? "✅" : "❌");
            log.info("   - Python标点图片: {}", annotatedImageData != null ? "✅" : "❌");
            log.info("✅ ===== 双重AI分析请求完成 =====");

        } catch (Exception e) {
            log.error("💥 ===== 双重AI分析请求失败 =====");
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
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 调用外部模型（火山引擎）获取文字说明
     */
    private String callExternalModel(String imageBase64, String prompt) throws Exception {
        log.info("🏗️  构造外部模型请求体...");

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

        // 图片内容
        log.info("🖼️  添加图片内容...");
        Map<String, Object> imageContent = new HashMap<>();
        imageContent.put("type", "image_url");
        Map<String, String> imageUrl = new HashMap<>();
        String fullImageUrl = "data:image/jpeg;base64," + imageBase64;
        imageUrl.put("url", fullImageUrl);
        imageContent.put("image_url", imageUrl);
        content.add(imageContent);

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
     * 调用Python微服务获取标点图片数据
     */
    private String callPythonMicroservice(String imageBase64, String mediaType) throws Exception {
        log.info("🐍 构造Python微服务请求体...");

        // 按照规范构造发送给Python微服务的请求体
        Map<String, Object> pythonRequest = new HashMap<>();
        pythonRequest.put("type", mediaType); // "image" 或 "video"
        pythonRequest.put("data_base64", imageBase64);

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