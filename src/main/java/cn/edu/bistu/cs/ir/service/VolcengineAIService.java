package cn.edu.bistu.cs.ir.service;

import cn.edu.bistu.cs.ir.config.VolcengineConfig;
import cn.edu.bistu.cs.ir.dto.ai.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 火山引擎AI视觉分析服务
 */
@Slf4j
@Service
public class VolcengineAIService {

    @Autowired
    private VolcengineConfig config;

    @Autowired
    private ObjectMapper objectMapper;

    private final OkHttpClient httpClient;

    public VolcengineAIService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 分析图片和视频内容
     * @param request 分析请求
     * @return AI分析结果
     */
    public String analyzeMedia(AIAnalyzeRequest request) {
        log.info("🔍 开始AI视觉分析 - 提示词长度: {}, 图片数量: {}, 视频数量: {}",
                request.getPrompt().length(),
                request.getImages() != null ? request.getImages().size() : 0,
                request.getVideos() != null ? request.getVideos().size() : 0);

        try {
            // 构造火山引擎兼容的请求体
            Map<String, Object> requestBody = buildVisionRequestBody(request);
            log.info("📝 请求体构造完成，消息数量: {}", ((List<?>) requestBody.get("messages")).size());

            // 创建HTTP请求
            RequestBody body = RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    objectMapper.writeValueAsString(requestBody)
            );

            Request httpRequest = new Request.Builder()
                    .url(config.getBaseUrl() + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + config.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-Endpoint-Id", config.getEndpointId())
                    .post(body)
                    .build();

            log.info("🌐 发送请求到火山引擎 - URL: {}, 模型: {}", config.getBaseUrl(), config.getModel());

            // 发送请求并获取响应
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                String responseBody = response.body().string();
                log.info("📥 收到响应 - 状态码: {}, 响应长度: {}", response.code(), responseBody.length());

                if (!response.isSuccessful()) {
                    log.error("❌ API调用失败 - 状态码: {}, 响应: {}", response.code(), responseBody);
                    throw new RuntimeException("火山引擎API调用失败: " + response.message());
                }

                // 解析响应并提取结果
                return extractAIResponse(responseBody);
            }

        } catch (IOException e) {
            log.error("💥 网络请求异常", e);
            throw new RuntimeException("网络请求失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("💥 AI分析过程异常", e);
            throw new RuntimeException("AI分析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构造符合OpenAI Vision格式的请求体
     */
    private Map<String, Object> buildVisionRequestBody(AIAnalyzeRequest request) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModel());

        // 构造消息列表
        List<VisionMessage> messages = new ArrayList<>();
        VisionMessage message = new VisionMessage();

        // 构造内容列表
        List<VisionContent> contentList = new ArrayList<>();

        // 添加文本内容
        VisionContent textContent = new VisionContent();
        textContent.setType("text");
        textContent.setText(request.getPrompt());
        contentList.add(textContent);

        // 添加图片内容
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            for (int i = 0; i < request.getImages().size(); i++) {
                String base64Image = request.getImages().get(i);
                log.info("📷 添加图片 - 索引: {}, Base64长度: {}", i, base64Image.length() > 50 ? "..." + base64Image.substring(base64Image.length() - 50) : base64Image);

                VisionContent imageContent = new VisionContent();
                imageContent.setType("image_url");

                ImageUrl imageUrl = new ImageUrl();
                imageUrl.setUrl("data:image/jpeg;base64," + base64Image);
                imageContent.setImageUrl(imageUrl);

                contentList.add(imageContent);
            }
        }

        // 添加视频内容
        if (request.getVideos() != null && !request.getVideos().isEmpty()) {
            for (int i = 0; i < request.getVideos().size(); i++) {
                String base64Video = request.getVideos().get(i);
                log.info("🎥 添加视频 - 索引: {}, Base64长度: {}", i, base64Video.length() > 50 ? "..." + base64Video.substring(base64Video.length() - 50) : base64Video);

                VisionContent videoContent = new VisionContent();
                videoContent.setType("image_url"); // 火山引擎视频也使用image_url类型

                ImageUrl videoUrl = new ImageUrl();
                videoUrl.setUrl("data:video/mp4;base64," + base64Video);
                videoContent.setImageUrl(videoUrl);

                contentList.add(videoContent);
            }
        }

        message.setContent(contentList);
        messages.add(message);

        requestBody.put("messages", messages);
        requestBody.put("max_tokens", request.getMaxTokens());
        requestBody.put("temperature", request.getTemperature());

        return requestBody;
    }

    /**
     * 从API响应中提取AI回复文本
     */
    private String extractAIResponse(String responseBody) {
        try {
            // 这里可以进一步解析JSON响应，暂时返回完整响应
            log.info("✅ AI分析完成 - 响应体长度: {}", responseBody.length());

            // 简单的JSON解析示例（建议使用专门的JSON解析库）
            if (responseBody.contains("\"content\"")) {
                int contentIndex = responseBody.indexOf("\"content\":");
                if (contentIndex != -1) {
                    int startIndex = responseBody.indexOf("\"", contentIndex + 10);
                    int endIndex = responseBody.indexOf("\"", startIndex + 1);
                    if (startIndex != -1 && endIndex != -1) {
                        String content = responseBody.substring(startIndex + 1, endIndex);
                        log.info("📝 提取的AI回复内容: {}", content.length() > 100 ? content.substring(0, 100) + "..." : content);
                        return content;
                    }
                }
            }

            // 如果解析失败，返回完整响应
            return responseBody;

        } catch (Exception e) {
            log.error("🔧 响应解析异常", e);
            return responseBody; // 解析失败时返回完整响应
        }
    }
}