package cn.edu.bistu.cs.ir.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 火山引擎视觉大模型配置类
 */
@Data
@Component
@ConfigurationProperties(prefix = "volcengine.vision")
public class VolcengineConfig {

    /**
     * 端点ID
     */
    private String endpointId;

    /**
     * API密钥
     */
    private String apiKey;

    /**
     * 基础URL
     */
    private String baseUrl;

    /**
     * 模型名称
     */
    private String model;

    /**
     * 超时时间（毫秒）
     */
    private Integer timeout;
}