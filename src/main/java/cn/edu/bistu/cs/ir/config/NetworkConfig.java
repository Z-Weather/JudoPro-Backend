package cn.edu.bistu.cs.ir.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 网络配置统一管理类
 * 用于管理所有服务间通信的基础URL地址
 */
@Component
public class NetworkConfig {

    private static final Logger log = LoggerFactory.getLogger(NetworkConfig.class);

    // 服务器基础配置
    @Value("${server.base.host:10.199.201.199}")
    private String serverHost;

    @Value("${server.base.port:8080}")
    private String serverPort;

    // Python微服务配置
    @Value("${python.microservice.host:10.199.201.199}")
    private String pythonMicroserviceHost;

    @Value("${python.microservice.binary.port:8000}")
    private String pythonBinaryPort;

    @Value("${python.microservice.base64.port:5000}")
    private String pythonBase64Port;

    // Python微服务端点
    @Value("${python.microservice.binary.endpoint:/analyze_binary}")
    private String pythonBinaryEndpoint;

    @Value("${python.microservice.base64.endpoint:/analyze}")
    private String pythonBase64Endpoint;

    // 静态配置常量 - 实际运行时使用注入的配置
    public static String SERVER_BASE_URL;
    public static String PYTHON_BINARY_URL;
    public static String PYTHON_BASE64_URL;

    /**
     * Spring Boot启动后初始化静态配置
     */
    @PostConstruct
    public void init() {
        // 构建服务器基础URL
        SERVER_BASE_URL = String.format("http://%s:%s", serverHost, serverPort);

        // 构建Python微服务URL
        PYTHON_BINARY_URL = String.format("http://%s:%s%s",
            pythonMicroserviceHost, pythonBinaryPort, pythonBinaryEndpoint);
        PYTHON_BASE64_URL = String.format("http://%s:%s%s",
            pythonMicroserviceHost, pythonBase64Port, pythonBase64Endpoint);

        // 调试日志：打印加载的配置信息
        log.info("🌐 [网络配置] 初始化网络基础地址配置");
        log.info("🌐 [网络配置] 服务器地址: {}", SERVER_BASE_URL);
        log.info("🌐 [网络配置] Python微服务(二进制): {}", PYTHON_BINARY_URL);
        log.info("🌐 [网络配置] Python微服务(Base64): {}", PYTHON_BASE64_URL);
        log.info("✅ [网络配置] 所有网络基础地址配置加载完成");
    }

    /**
     * 获取服务器基础URL
     */
    public static String getServerBaseUrl() {
        return SERVER_BASE_URL;
    }

    /**
     * 获取Python微服务二进制流URL
     */
    public static String getPythonBinaryUrl() {
        return PYTHON_BINARY_URL;
    }

    /**
     * 获取Python微服务Base64传输URL
     */
    public static String getPythonBase64Url() {
        return PYTHON_BASE64_URL;
    }

    /**
     * 获取完整的服务器资源URL
     * @param resourcePath 资源路径（如：/api/file/upload/image）
     * @return 完整URL
     */
    public static String getFullServerUrl(String resourcePath) {
        if (resourcePath.startsWith("/")) {
            return SERVER_BASE_URL + resourcePath;
        } else {
            return SERVER_BASE_URL + "/" + resourcePath;
        }
    }
}