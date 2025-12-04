-- =====================================================
-- 数据库建表语句 - 根据实体类推导
-- =====================================================

-- 用户表 (根据 User.java 实体类推导)
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username VARCHAR(255) NOT NULL UNIQUE COMMENT '用户名',
    email VARCHAR(255) UNIQUE COMMENT '邮箱地址',
    password VARCHAR(255) NOT NULL COMMENT '密码',
    avatar VARCHAR(500) COMMENT '头像URL',
    real_name VARCHAR(100) COMMENT '真实姓名',
    gender VARCHAR(10) COMMENT '性别',
    birth_date VARCHAR(50) COMMENT '出生日期',
    enabled BOOLEAN DEFAULT TRUE COMMENT '账户是否启用'
);

-- 用户文件表 (根据 UserFile.java 实体类推导)
CREATE TABLE user_files (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '文件ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    original_filename VARCHAR(255) NOT NULL COMMENT '原始文件名',
    stored_filename VARCHAR(255) NOT NULL COMMENT '存储文件名',
    file_url VARCHAR(500) NOT NULL COMMENT '文件访问URL',
    file_type VARCHAR(20) NOT NULL COMMENT '文件类型(image/video)',
    file_extension VARCHAR(10) NOT NULL COMMENT '文件扩展名',
    file_size BIGINT NOT NULL COMMENT '文件大小(字节)',
    mime_type VARCHAR(100) COMMENT 'MIME类型',
    description VARCHAR(500) COMMENT '文件描述',
    download_count INT NOT NULL DEFAULT 0 COMMENT '下载次数',
    upload_time DATETIME NOT NULL COMMENT '上传时间',
    last_access_time DATETIME COMMENT '最后访问时间',
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否已删除',
    delete_time DATETIME COMMENT '删除时间',

    -- 添加外键约束（如果需要）
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 创建索引
CREATE INDEX idx_user_files_user_id ON user_files(user_id);
CREATE INDEX idx_user_files_file_type ON user_files(file_type);
CREATE INDEX idx_user_files_upload_time ON user_files(upload_time);
CREATE INDEX idx_user_files_is_deleted ON user_files(is_deleted);