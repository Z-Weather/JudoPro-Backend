-- AI分析结果持久化功能数据库迁移脚本
-- 执行日期：2025-12-04
-- 功能：存储AI模型分析后的结果数据

-- 创建AI分析结果表
CREATE TABLE IF NOT EXISTS ai_analysis (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '分析结果ID',
    user_file_id BIGINT NOT NULL COMMENT '关联的用户文件ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    external_model_result LONGTEXT COMMENT '外部模型（火山引擎）返回的文字描述',
    external_model_result_url VARCHAR(500) COMMENT '外部模型文字描述文件的URL',
    external_model_result_filename VARCHAR(255) COMMENT '外部模型文字描述文件名',
    python_service_result LONGTEXT COMMENT 'Python微服务返回的完整结果JSON',
    annotated_media_url VARCHAR(500) COMMENT '标注后的媒体文件URL',
    annotated_filename VARCHAR(255) COMMENT '标注媒体文件的存储文件名',
    analysis_status VARCHAR(50) NOT NULL DEFAULT 'pending' COMMENT '分析状态：pending-进行中，completed-完成，failed-失败',
    analysis_time DATETIME COMMENT '分析完成时间',
    error_message VARCHAR(1000) COMMENT '错误消息（如果分析失败）',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    media_type VARCHAR(20) NOT NULL COMMENT '媒体类型（image/video）',
    prompt VARCHAR(1000) COMMENT '分析提示词',

    INDEX idx_user_file_id (user_file_id) COMMENT '用户文件ID索引',
    INDEX idx_user_id (user_id) COMMENT '用户ID索引',
    INDEX idx_media_type (media_type) COMMENT '媒体类型索引',
    INDEX idx_analysis_status (analysis_status) COMMENT '分析状态索引',
    INDEX idx_analysis_time (analysis_time) COMMENT '分析时间索引',
    INDEX idx_created_time (created_time) COMMENT '创建时间索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI分析结果表';

-- 如果表已存在，则修改字段类型
ALTER TABLE ai_analysis
MODIFY COLUMN external_model_result LONGTEXT COMMENT '外部模型（火山引擎）返回的文字描述';

ALTER TABLE ai_analysis
MODIFY COLUMN python_service_result LONGTEXT COMMENT 'Python微服务返回的完整结果JSON';

-- 添加外键约束（可选，根据实际需要）
-- ALTER TABLE ai_analysis ADD CONSTRAINT fk_ai_analysis_user_file
-- FOREIGN KEY (user_file_id) REFERENCES user_files(id) ON DELETE CASCADE;

-- ALTER TABLE ai_analysis ADD CONSTRAINT fk_ai_analysis_user
-- FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;