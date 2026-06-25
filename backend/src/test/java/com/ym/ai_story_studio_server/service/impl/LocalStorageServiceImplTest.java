package com.ym.ai_story_studio_server.service.impl;

import com.ym.ai_story_studio_server.config.StorageProperties;
import com.ym.ai_story_studio_server.exception.StorageException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LocalStorageServiceImpl 单元测试")
class LocalStorageServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("成功保存、下载并删除图片")
    void uploadDownloadDelete_Success() throws Exception {
        LocalStorageServiceImpl storageService = createStorageService();
        byte[] content = "fake image content".getBytes(StandardCharsets.UTF_8);

        String url = storageService.upload(
                new ByteArrayInputStream(content),
                "avatar.png",
                "image/png"
        );

        assertThat(url).startsWith("/uploads/");
        assertThat(url).endsWith("_avatar.png");

        try (InputStream inputStream = storageService.download(url)) {
            assertThat(inputStream.readAllBytes()).isEqualTo(content);
        }

        storageService.delete(url);
        assertThat(Files.walk(tempDir).filter(Files::isRegularFile).count()).isZero();
    }

    @Test
    @DisplayName("拒绝不支持的文件类型")
    void upload_Fails_UnsupportedContentType() {
        LocalStorageServiceImpl storageService = createStorageService();

        assertThatThrownBy(() -> storageService.upload(
                new ByteArrayInputStream("bad".getBytes(StandardCharsets.UTF_8)),
                "bad.exe",
                "application/x-msdownload"
        )).isInstanceOf(StorageException.class)
                .hasMessageContaining("不支持的文件类型");
    }

    @Test
    @DisplayName("清理带路径和特殊字符的文件名")
    void upload_CleansFileName() {
        LocalStorageServiceImpl storageService = createStorageService();

        String url = storageService.upload(
                new ByteArrayInputStream("image".getBytes(StandardCharsets.UTF_8)),
                "../人物 头像@1.png",
                "image/png"
        );

        assertThat(url).doesNotContain("..");
        assertThat(url).doesNotContain("@");
        assertThat(url).endsWith("______1.png");
    }

    private LocalStorageServiceImpl createStorageService() {
        StorageProperties properties = new StorageProperties();
        properties.setProvider("local");
        properties.getLocal().setBasePath(tempDir.toString());
        properties.getLocal().setUrlPrefix("/uploads");

        LocalStorageServiceImpl storageService = new LocalStorageServiceImpl(properties);
        storageService.init();
        return storageService;
    }
}
