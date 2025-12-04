package cn.edu.bistu.cs.ir.dto.ai;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * AI视觉分析请求DTO
 */
@Data
public class AIAnalyzeRequest {

    /**
     * 用户输入的文本提示
     */
    private String prompt;

    /**
     * 图片数据（Base64编码）
     */
    private List<String> images;

    /**
     * 视频数据（Base64编码）
     */
    private List<String> videos;

    /**
     * 最大token数
     */
    private Integer maxTokens = 4096;

    /**
     * 温度参数
     */
    private Float temperature = 0.7f;
}