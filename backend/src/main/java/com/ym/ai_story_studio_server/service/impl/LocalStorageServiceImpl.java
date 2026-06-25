package com.ym.ai_story_studio_server.service.impl;

import com.ym.ai_story_studio_server.config.StorageProperties;
import com.ym.ai_story_studio_server.exception.StorageException;
import com.ym.ai_story_studio_server.service.StorageService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

/**
 * 本地文件存储服务实现。
 *
 * <p>用于本地化部署阶段替代OSS，把上传的角色图片、资产图片等保存到本机目录，
 * 并通过 /uploads/** 静态资源路径对外访问。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageServiceImpl implements StorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif",
            "image/webp", "image/bmp", "image/svg+xml",
            "video/mp4", "video/webm", "video/ogg", "video/avi",
            "video/quicktime", "video/x-msvideo",
            "audio/mpeg", "audio/mp3", "audio/wav", "audio/ogg", "audio/webm"
    );

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final StorageProperties storageProperties;
    private Path rootPath;

    public LocalStorageServiceImpl(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @PostConstruct
    public void init() {
        StorageProperties.LocalConfig localConfig = storageProperties.getLocal();
        String basePath = localConfig != null && StringUtils.hasText(localConfig.getBasePath())
                ? localConfig.getBasePath()
                : "./uploads";

        try {
            rootPath = Paths.get(basePath).toAbsolutePath().normalize();
            Files.createDirectories(rootPath);
            log.info("本地存储服务初始化成功: rootPath={}, urlPrefix={}",
                    rootPath, getUrlPrefix());
        } catch (IOException e) {
            throw new StorageException("LOCAL_STORAGE_INIT_FAILED",
                    "本地存储目录初始化失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String upload(InputStream inputStream, String fileName, String contentType) {
        validateContentType(contentType);

        String fileKey = generateFileKey(fileName);
        Path targetPath = resolveFileKey(fileKey);

        try {
            Files.createDirectories(targetPath.getParent());
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            String url = buildFileUrl(fileKey);
            log.info("本地文件保存成功: fileKey={}, path={}, url={}", fileKey, targetPath, url);
            return url;
        } catch (IOException e) {
            log.error("本地文件保存失败: fileName={}", fileName, e);
            throw new StorageException("UPLOAD_FAILED",
                    "本地文件保存失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String uploadImageBytes(byte[] imageBytes, String fileName) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes)) {
            return upload(bais, fileName, "image/png");
        } catch (IOException e) {
            throw new StorageException("UPLOAD_FAILED",
                    "本地图片保存失败: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream download(String fileUrl) {
        String fileKey = extractFileKey(fileUrl);
        Path filePath = resolveFileKey(fileKey);

        try {
            return Files.newInputStream(filePath);
        } catch (IOException e) {
            throw new StorageException("DOWNLOAD_FAILED",
                    "本地文件读取失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String fileUrl) {
        String fileKey = extractFileKey(fileUrl);
        Path filePath = resolveFileKey(fileKey);

        try {
            Files.deleteIfExists(filePath);
            log.info("本地文件删除成功: fileKey={}, path={}", fileKey, filePath);
        } catch (IOException e) {
            throw new StorageException("DELETE_FAILED",
                    "本地文件删除失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String generatePresignedUrl(String fileKey, int expirationMinutes) {
        return buildFileUrl(normalizeFileKey(fileKey));
    }

    public Path getRootPath() {
        if (rootPath == null) {
            init();
        }
        return rootPath;
    }

    private void validateContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            throw new StorageException("INVALID_CONTENT_TYPE", "文件类型不能为空");
        }

        String mainType = contentType.split(";")[0].trim().toLowerCase();
        if (!ALLOWED_CONTENT_TYPES.contains(mainType)) {
            throw new StorageException("INVALID_CONTENT_TYPE",
                    "不支持的文件类型: " + contentType);
        }
    }

    private String generateFileKey(String originalFileName) {
        String datePath = LocalDateTime.now().format(DATE_FORMATTER);
        String uuid = UUID.randomUUID().toString();
        return datePath + "/" + uuid + "_" + cleanFileName(originalFileName);
    }

    private String cleanFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "unnamed_file";
        }

        String name = fileName;
        int lastSlash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private Path resolveFileKey(String fileKey) {
        Path resolvedPath = getRootPath().resolve(normalizeFileKey(fileKey)).normalize();
        if (!resolvedPath.startsWith(getRootPath())) {
            throw new StorageException("INVALID_FILE_KEY", "非法文件路径");
        }
        return resolvedPath;
    }

    private String normalizeFileKey(String fileKey) {
        if (!StringUtils.hasText(fileKey)) {
            throw new StorageException("INVALID_FILE_KEY", "文件Key不能为空");
        }
        return fileKey.replace('\\', '/').replaceFirst("^/+", "");
    }

    private String buildFileUrl(String fileKey) {
        return getUrlPrefix() + "/" + normalizeFileKey(fileKey);
    }

    private String getUrlPrefix() {
        StorageProperties.LocalConfig localConfig = storageProperties.getLocal();
        String urlPrefix = localConfig != null && StringUtils.hasText(localConfig.getUrlPrefix())
                ? localConfig.getUrlPrefix()
                : "/uploads";
        String normalizedPrefix = urlPrefix.replaceAll("/+$", "");
        return normalizedPrefix.startsWith("/") ? normalizedPrefix : "/" + normalizedPrefix;
    }

    private String extractFileKey(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            throw new StorageException("INVALID_URL", "文件URL不能为空");
        }

        String normalizedUrl = fileUrl.replace('\\', '/');
        String urlPrefix = getUrlPrefix();
        int prefixIndex = normalizedUrl.indexOf(urlPrefix + "/");
        if (prefixIndex >= 0) {
            return normalizedUrl.substring(prefixIndex + urlPrefix.length() + 1);
        }

        if (normalizedUrl.startsWith("/")) {
            return normalizedUrl.substring(1);
        }
        return normalizedUrl;
    }
}
