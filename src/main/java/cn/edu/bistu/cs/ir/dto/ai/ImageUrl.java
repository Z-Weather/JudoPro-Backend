package cn.edu.bistu.cs.ir.dto.ai;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OpenAI Vision格式的图片URL结构
 */
@Data
public class ImageUrl {

    /**
     * URL类型：base64
     */
    @JsonProperty("url")
    private String url;
}