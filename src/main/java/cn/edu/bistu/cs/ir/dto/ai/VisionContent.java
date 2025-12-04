package cn.edu.bistu.cs.ir.dto.ai;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OpenAI Vision格式的内容结构
 */
@Data
public class VisionContent {

    /**
     * 内容类型：text 或 image_url
     */
    @JsonProperty("type")
    private String type;

    /**
     * 文本内容（当type为text时使用）
     */
    private String text;

    /**
     * 图片URL信息（当type为image_url时使用）
     */
    private ImageUrl imageUrl;
}