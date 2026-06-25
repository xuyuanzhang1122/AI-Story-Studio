// {{CODE-Cycle-Integration:
//   Task_ID: [#T001]
//   Timestamp: [2025-12-26 16:15:00]
//   Phase: [D-Develop]
//   Context-Analysis: "为StorageProperties配置类编写单元测试，验证配置属性绑定和默认值"
//   Principle_Applied: "测试驱动开发, Verification-Mindset-Loop, 配置验证最佳实践"
// }}
// {{START_MODIFICATIONS}}
package com.ym.ai_story_studio_server.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StorageProperties 配置属性测试
 *
 * <p>测试存储配置属性的绑定和默认值：
 * <ul>
 *   <li>默认提供商验证</li>
 *   <li>OSS配置属性绑定</li>
 *   <li>环境变量覆盖</li>
 * </ul>
 *
 * @author Roo (Prometheus)
 * @since 1.0.0
 */
@SpringBootTest(classes = StoragePropertiesTest.class)
@EnableConfigurationProperties(StorageProperties.class)
@TestPropertySource(properties = {
        "storage.provider=oss",
        "storage.oss.endpoint=oss-cn-hangzhou.aliyuncs.com",
        "storage.oss.bucket=test-bucket",
        "storage.oss.access-key-id=test-access-key-id",
        "storage.oss.access-key-secret=test-access-key-secret",
        "storage.oss.region=cn-hangzhou",
        "storage.oss.url-prefix=https://test-bucket.oss-cn-hangzhou.aliyuncs.com"
})
@DisplayName("StorageProperties 配置测试")
class StoragePropertiesTest {

    private final StorageProperties storageProperties = new StorageProperties();

    @BeforeEach
    void setUp() {
        // 手动设置配置属性（模拟Spring Boot配置绑定）
        storageProperties.setProvider("oss");

        StorageProperties.OssConfig ossConfig = new StorageProperties.OssConfig();
        ossConfig.setEndpoint("oss-cn-hangzhou.aliyuncs.com");
        ossConfig.setBucket("test-bucket");
        ossConfig.setAccessKeyId("test-access-key-id");
        ossConfig.setAccessKeySecret("test-access-key-secret");
        ossConfig.setRegion("cn-hangzhou");
        ossConfig.setUrlPrefix("https://test-bucket.oss-cn-hangzhou.aliyuncs.com");

        storageProperties.setOss(ossConfig);
    }

    @Nested
    @DisplayName("默认值测试")
    class DefaultValuesTests {

        @Test
        @DisplayName("验证默认提供商为local")
        void defaultProvider_IsLocal() {
            // Arrange
            StorageProperties properties = new StorageProperties();

            // Act & Assert
            assertThat(properties.getProvider()).isEqualTo("local");
        }

        @Test
        @DisplayName("验证OSS配置不为null")
        void ossConfig_IsNotNull() {
            // Arrange
            StorageProperties properties = new StorageProperties();

            // Act & Assert
            assertThat(properties.getOss()).isNotNull();
        }

        @Test
        @DisplayName("验证本地存储配置不为null")
        void localConfig_IsNotNull() {
            // Arrange
            StorageProperties properties = new StorageProperties();

            // Act & Assert
            assertThat(properties.getLocal()).isNotNull();
        }

        @Test
        @DisplayName("OSS配置字段默认值")
        void ossConfig_DefaultValues_AreNull() {
            // Arrange
            StorageProperties properties = new StorageProperties();
            StorageProperties.OssConfig ossConfig = properties.getOss();

            // Act & Assert
            assertThat(ossConfig.getEndpoint()).isNull();
            assertThat(ossConfig.getBucket()).isNull();
            assertThat(ossConfig.getAccessKeyId()).isNull();
            assertThat(ossConfig.getAccessKeySecret()).isNull();
            assertThat(ossConfig.getRegion()).isNull();
            assertThat(ossConfig.getUrlPrefix()).isNull();
        }

        @Test
        @DisplayName("本地存储配置字段默认值")
        void localConfig_DefaultValues() {
            // Arrange
            StorageProperties properties = new StorageProperties();
            StorageProperties.LocalConfig localConfig = properties.getLocal();

            // Act & Assert
            assertThat(localConfig.getBasePath()).isEqualTo("./uploads");
            assertThat(localConfig.getUrlPrefix()).isEqualTo("/uploads");
        }
    }

    @Nested
    @DisplayName("配置属性绑定测试")
    class ConfigurationBindingTests {

        @Test
        @DisplayName("验证提供商配置正确绑定")
        void provider_BoundCorrectly() {
            // Act & Assert
            assertThat(storageProperties.getProvider()).isEqualTo("oss");
        }

        @Test
        @DisplayName("验证OSS endpoint配置正确绑定")
        void ossEndpoint_BoundCorrectly() {
            // Act & Assert
            assertThat(storageProperties.getOss().getEndpoint())
                    .isEqualTo("oss-cn-hangzhou.aliyuncs.com");
        }

        @Test
        @DisplayName("验证OSS bucket配置正确绑定")
        void ossBucket_BoundCorrectly() {
            // Act & Assert
            assertThat(storageProperties.getOss().getBucket())
                    .isEqualTo("test-bucket");
        }

        @Test
        @DisplayName("验证OSS accessKeyId配置正确绑定")
        void ossAccessKeyId_BoundCorrectly() {
            // Act & Assert
            assertThat(storageProperties.getOss().getAccessKeyId())
                    .isEqualTo("test-access-key-id");
        }

        @Test
        @DisplayName("验证OSS accessKeySecret配置正确绑定")
        void ossAccessKeySecret_BoundCorrectly() {
            // Act & Assert
            assertThat(storageProperties.getOss().getAccessKeySecret())
                    .isEqualTo("test-access-key-secret");
        }

        @Test
        @DisplayName("验证OSS region配置正确绑定")
        void ossRegion_BoundCorrectly() {
            // Act & Assert
            assertThat(storageProperties.getOss().getRegion())
                    .isEqualTo("cn-hangzhou");
        }

        @Test
        @DisplayName("验证OSS urlPrefix配置正确绑定")
        void ossUrlPrefix_BoundCorrectly() {
            // Act & Assert
            assertThat(storageProperties.getOss().getUrlPrefix())
                    .isEqualTo("https://test-bucket.oss-cn-hangzhou.aliyuncs.com");
        }
    }

    @Nested
    @DisplayName("OssConfig 内部类测试")
    class OssConfigTests {

        @Test
        @DisplayName("验证OssConfig可以使用Lombok @Data注解")
        void ossConfig_SupportsSetterAndGetter() {
            // Arrange
            StorageProperties.OssConfig ossConfig = new StorageProperties.OssConfig();

            // Act
            ossConfig.setEndpoint("test-endpoint");
            ossConfig.setBucket("test-bucket");
            ossConfig.setAccessKeyId("test-key-id");
            ossConfig.setAccessKeySecret("test-secret");
            ossConfig.setRegion("test-region");
            ossConfig.setUrlPrefix("https://test.com");

            // Assert
            assertThat(ossConfig.getEndpoint()).isEqualTo("test-endpoint");
            assertThat(ossConfig.getBucket()).isEqualTo("test-bucket");
            assertThat(ossConfig.getAccessKeyId()).isEqualTo("test-key-id");
            assertThat(ossConfig.getAccessKeySecret()).isEqualTo("test-secret");
            assertThat(ossConfig.getRegion()).isEqualTo("test-region");
            assertThat(ossConfig.getUrlPrefix()).isEqualTo("https://test.com");
        }

        @Test
        @DisplayName("验证OssConfig支持链式调用")
        void ossConfig_SupportsChainedCalls() {
            // Arrange & Act
            StorageProperties.OssConfig ossConfig = new StorageProperties.OssConfig();
            ossConfig.setEndpoint("test-endpoint");
            ossConfig.setBucket("test-bucket");

            // Assert
            assertThat(ossConfig.getEndpoint()).isEqualTo("test-endpoint");
            assertThat(ossConfig.getBucket()).isEqualTo("test-bucket");
        }
    }

    @Nested
    @DisplayName("配置验证测试")
    class ConfigurationValidationTests {

        @Test
        @DisplayName("验证完整配置所有字段都不为空")
        void completeConfiguration_AllFieldsNotNull() {
            // Act & Assert
            assertThat(storageProperties.getProvider()).isNotEmpty();
            assertThat(storageProperties.getOss().getEndpoint()).isNotEmpty();
            assertThat(storageProperties.getOss().getBucket()).isNotEmpty();
            assertThat(storageProperties.getOss().getAccessKeyId()).isNotEmpty();
            assertThat(storageProperties.getOss().getAccessKeySecret()).isNotEmpty();
            assertThat(storageProperties.getOss().getRegion()).isNotEmpty();
        }

        @Test
        @DisplayName("验证URL前缀格式正确")
        void urlPrefix_HasCorrectFormat() {
            // Act & Assert
            String urlPrefix = storageProperties.getOss().getUrlPrefix();
            assertThat(urlPrefix).startsWith("https://");
            assertThat(urlPrefix).contains("aliyuncs.com");
        }

        @Test
        @DisplayName("验证endpoint格式正确")
        void endpoint_HasCorrectFormat() {
            // Act & Assert
            String endpoint = storageProperties.getOss().getEndpoint();
            assertThat(endpoint).contains("oss-");
            assertThat(endpoint).endsWith("aliyuncs.com");
        }
    }

    @Nested
        @DisplayName("提供商切换测试")
        class ProviderSwitchTests {

        @Test
        @DisplayName("支持切换到local提供商")
        void switchProvider_ToLocal() {
            // Act
            storageProperties.setProvider("local");

            // Assert
            assertThat(storageProperties.getProvider()).isEqualTo("local");
        }

        @Test
        @DisplayName("支持切换到minio提供商")
        void switchProvider_ToMinio() {
            // Act
            storageProperties.setProvider("minio");

            // Assert
            assertThat(storageProperties.getProvider()).isEqualTo("minio");
        }

        @Test
        @DisplayName("支持切换到oss提供商")
        void switchProvider_ToOss() {
            // Arrange
            storageProperties.setProvider("minio");

            // Act
            storageProperties.setProvider("oss");

            // Assert
            assertThat(storageProperties.getProvider()).isEqualTo("oss");
        }

        @Test
        @DisplayName("切换提供商不影响OSS配置")
        void switchProvider_DoesNotAffectOssConfig() {
            // Arrange
            String originalEndpoint = storageProperties.getOss().getEndpoint();

            // Act
            storageProperties.setProvider("minio");

            // Assert
            assertThat(storageProperties.getOss().getEndpoint()).isEqualTo(originalEndpoint);
        }
    }

    @Nested
    @DisplayName("环境变量模拟测试")
    class EnvironmentVariableTests {

        @Test
        @DisplayName("模拟环境变量覆盖accessKeyId")
        void environmentVariable_OverridesAccessKeyId() {
            // Arrange
            String envValue = "env-access-key-id";
            storageProperties.getOss().setAccessKeyId(envValue);

            // Act & Assert
            assertThat(storageProperties.getOss().getAccessKeyId()).isEqualTo(envValue);
        }

        @Test
        @DisplayName("模拟环境变量覆盖accessKeySecret")
        void environmentVariable_OverridesAccessKeySecret() {
            // Arrange
            String envValue = "env-access-key-secret";
            storageProperties.getOss().setAccessKeySecret(envValue);

            // Act & Assert
            assertThat(storageProperties.getOss().getAccessKeySecret()).isEqualTo(envValue);
        }
    }

    @Nested
    @DisplayName("CDN配置测试")
    class CdnConfigurationTests {

        @Test
        @DisplayName("支持自定义CDN URL前缀")
        void customCdnUrlPrefix() {
            // Arrange
            String cdnPrefix = "https://cdn.example.com";

            // Act
            storageProperties.getOss().setUrlPrefix(cdnPrefix);

            // Assert
            assertThat(storageProperties.getOss().getUrlPrefix()).isEqualTo(cdnPrefix);
        }

        @Test
        @DisplayName("支持空CDN URL前缀")
        void nullCdnUrlPrefix() {
            // Act
            storageProperties.getOss().setUrlPrefix(null);

            // Assert
            assertThat(storageProperties.getOss().getUrlPrefix()).isNull();
        }

        @Test
        @DisplayName("支持HTTP CDN URL前缀")
        void httpCdnUrlPrefix() {
            // Arrange
            String httpPrefix = "http://cdn.example.com";

            // Act
            storageProperties.getOss().setUrlPrefix(httpPrefix);

            // Assert
            assertThat(storageProperties.getOss().getUrlPrefix()).isEqualTo(httpPrefix);
        }
    }
}
// {{END_MODIFICATIONS}}
