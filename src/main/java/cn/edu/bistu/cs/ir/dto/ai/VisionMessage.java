package cn.edu.bistu.cs.ir.dto.ai;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * OpenAI Vision格式的消息结构
 */
@Data
public class VisionMessage {

    /**
     * 消息角色
     */
    @JsonProperty("role")
    private String role = "user";

    /**
     * 消息内容列表
     */
    @JsonProperty("content")
    private List<VisionContent> content;
}