package cn.edu.bistu.cs.ir.controller;

import cn.edu.bistu.cs.ir.dto.ai.AIAnalyzeRequest;
import cn.edu.bistu.cs.ir.service.VolcengineAIService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * AI视觉分析控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AIController {

    @Autowired
    private VolcengineAIService volcengineAIService;

    /**
     * 分析图片和视频内容
     * @param request 分析请求
     * @return AI分析结果
     */
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeMedia(@RequestBody AIAnalyzeRequest request) {
        log.info("🚀 接收到AI分析请求 - 提示词: {}, 媒体数量: {}",
                request.getPrompt(),
                (request.getImages() != null ? request.getImages().size() : 0) +
                (request.getVideos() != null ? request.getVideos().size() : 0));

        Map<String, Object> response = new HashMap<>();

        try {
            // 参数验证
            if (request.getPrompt() == null || request.getPrompt().trim().isEmpty()) {
                log.warn("⚠️ 提示词为空");
                response.put("success", false);
                response.put("message", "提示词不能为空");
                return ResponseEntity.badRequest().body(response);
            }

            // 检查是否有媒体数据
            boolean hasMedia = (request.getImages() != null && !request.getImages().isEmpty()) ||
                             (request.getVideos() != null && !request.getVideos().isEmpty());

            if (!hasMedia) {
                log.warn("⚠️ 未提供图片或视频数据");
                response.put("success", false);
                response.put("message", "请提供至少一张图片或一个视频");
                return ResponseEntity.badRequest().body(response);
            }

            // 调用AI服务进行分析
            String analysisResult = volcengineAIService.analyzeMedia(request);

            // 构造成功响应
            response.put("success", true);
            response.put("message", "分析成功");
            response.put("data", analysisResult);
            response.put("model", "doubao-seed-1-6-vision");

            log.info("✅ AI分析请求处理成功 - 响应长度: {}", analysisResult.length());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("💥 AI分析请求处理失败", e);
            response.put("success", false);
            response.put("message", "分析失败: " + e.getMessage());
            response.put("error", e.getClass().getSimpleName());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "healthy");
        response.put("service", "volcengine-vision");
        response.put("model", "doubao-seed-1-6-vision");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }
}