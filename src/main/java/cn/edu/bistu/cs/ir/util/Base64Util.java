package cn.edu.bistu.cs.ir.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Base64;

/**
 * Base64编码工具类
 */
@Slf4j
@Component
public class Base64Util {

    /**
     * 将字节数组编码为Base64字符串
     * @param data 字节数组
     * @return Base64编码字符串
     */
    public static String encodeToBase64(byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            String base64 = Base64.getEncoder().encodeToString(data);
            log.debug("Base64编码完成 - 原始大小: {}KB, 编码后大小: {}KB",
                    data.length / 1024.0, base64.length() / 1024.0);
            return base64;
        } catch (Exception e) {
            log.error("Base64编码失败", e);
            return null;
        }
    }

    /**
     * 从Base64字符串解码为字节数组
     * @param base64String Base64字符串
     * @return 解码后的字节数组
     */
    public static byte[] decodeFromBase64(String base64String) {
        if (base64String == null || base64String.trim().isEmpty()) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(base64String);
        } catch (Exception e) {
            log.error("Base64解码失败", e);
            return null;
        }
    }

    /**
     * 判断字符串是否为有效的Base64格式
     * @param str 待检查的字符串
     * @return 是否为有效的Base64格式
     */
    public static boolean isValidBase64(String str) {
        if (str == null || str.trim().isEmpty()) {
            return false;
        }
        try {
            Base64.getDecoder().decode(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}