// {{CODE-Cycle-Integration:
//   Task_ID: [#T001]
//   Timestamp: [2025-12-26 15:30:00]
//   Phase: [D-Develop]
//   Context-Analysis: "分析了JwtProperties配置模式，创建类型安全的存储配置类。支持多存储提供商扩展。"
//   Principle_Applied: "SOLID原则-单一职责, DRY原则-复用配置模式, 安全性-环境变量支持"
// }}
// {{START_MODIFICATIONS}}
package com.ym.ai_story_studio_server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 存储服务配置属性类
 *
 * <p>从application.yml中绑定storage配置项，支持多种存储提供商
 *
 * <p>配置示例：
 * <pre>
 * storage:
 *   provider: oss
 *   oss:
 *     endpoint: oss-cn-hangzhou.aliyuncs.com
 *     bucket: yuanmeng-logo
 *     access-key-id: ${OSS_ACCESS_KEY_ID:默认值}
 *     access-key-secret: ${OSS_ACCESS_KEY_SECRET:默认值}
 *     region: cn-hangzhou
 *     url-prefix: https://yuanmeng-logo.oss-cn-hangzhou.aliyuncs.com
 *   local:
 *     base-path: ./uploads
 *     url-prefix: /uploads
 * </pre>
 *
 * @author Roo (Prometheus)
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    /**
     * 存储提供商类型
     * <p>支持: local (本地文件), oss (阿里云OSS), minio (MinIO) - V2规划
     */
    private String provider = "local";

    /**
     * 阿里云OSS配置
     */
    private OssConfig oss = new OssConfig();

    /**
     * 本地文件存储配置
     */
    private LocalConfig local = new LocalConfig();

    /**
     * 阿里云OSS配置项
     */
    @Data
    public static class OssConfig {

        /**
         * OSS访问端点
         * <p>根据区域选择，例如: oss-cn-hangzhou.aliyuncs.com
         */
        private String endpoint;

        /**
         * 存储桶名称
         */
        private String bucket;

        /**
         * 访问密钥ID
         * <p>生产环境强烈建议使用环境变量: ${OSS_ACCESS_KEY_ID}
         */
        private String accessKeyId;

        /**
         * 访问密钥Secret
         * <p>生产环境强烈建议使用环境变量: ${OSS_ACCESS_KEY_SECRET}
         */
        private String accessKeySecret;

        /**
         * 区域代码
         * <p>例如: cn-hangzhou
         */
        private String region;

        /**
         * URL前缀（可选）
         * <p>用于CDN加速或自定义域名，例如: https://cdn.example.com
         * <p>如果为空，则使用默认OSS域名
         */
        private String urlPrefix;
    }

    /**
     * 本地文件存储配置项
     */
    @Data
    public static class LocalConfig {

        /**
         * 本地文件保存根目录
         * <p>支持相对路径和绝对路径。相对路径基于应用启动目录解析。
         */
        private String basePath = "./uploads";

        /**
         * 对外访问URL前缀
         * <p>默认由Spring静态资源映射暴露为 /uploads/**
         */
        private String urlPrefix = "/uploads";
    }
}
// {{END_MODIFICATIONS}}
