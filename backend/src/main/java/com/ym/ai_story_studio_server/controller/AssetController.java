// {{CODE-Cycle-Integration:
//   Task_ID: [#T015]
//   Timestamp: [2025-12-28 16:10:00]
//   Phase: [D-Develop]
//   Context-Analysis: "创建资产控制器,提供资产版本管理的HTTP接口。所有接口需要JWT认证,通过UserContext获取当前用户ID。"
//   Principle_Applied: "RESTful规范, 统一响应格式Result<T>, 参数校验@Valid"
// }}
// {{START_MODIFICATIONS}}
package com.ym.ai_story_studio_server.controller;

import com.ym.ai_story_studio_server.common.Result;
import com.ym.ai_story_studio_server.util.UserContext;
import com.ym.ai_story_studio_server.dto.asset.AssetVersionVO;
import com.ym.ai_story_studio_server.dto.asset.SetCurrentVersionRequest;
import com.ym.ai_story_studio_server.service.AssetService;
import com.ym.ai_story_studio_server.service.StorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URL;
import java.util.List;

/**
 * 资产控制器
 *
 * <p>提供资产版本管理的HTTP接口,包括:
 * <ul>
 *   <li>GET /api/assets/{id}/versions - 获取资产版本历史列表</li>
 *   <li>POST /api/assets/{id}/versions/upload - 上传本地图片创建新版本</li>
 *   <li>PUT /api/assets/{id}/current - 设置当前版本</li>
 * </ul>
 *
 * <p>所有接口都需要JWT认证,通过UserContext获取当前用户ID进行权限验证
 *
 * @author Roo (Prometheus)
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;
    private final StorageService storageService;

    /**
     * 获取资产版本历史列表
     *
     * <p>返回指定资产的所有版本记录,按版本号降序排序(最新版本在前),
     * 并标记当前正在使用的版本。
     *
     * @param assetId 资产ID(路径参数)
     * @return 资产版本VO列表,按版本号降序排序
     */
    @GetMapping("/{id}/versions")
    public Result<List<AssetVersionVO>> getVersionHistory(@PathVariable("id") Long assetId) {
        log.info("收到获取资产版本历史列表请求, assetId: {}", assetId);
        Long userId = UserContext.getUserId();
        List<AssetVersionVO> versions = assetService.getVersionHistory(userId, assetId);
        log.info("返回{}个版本记录", versions.size());
        return Result.success(versions);
    }

    /**
     * 上传本地图片(创建新版本)
     *
     * <p>用户上传本地图片文件,系统自动生成新版本号(最大版本号+1),
     * 上传文件到对象存储(OSS/MinIO),创建新的AssetVersion记录。
     *
     * <p>版本来源标记为"UPLOAD",状态为"READY"。
     *
     * @param assetId 资产ID(路径参数)
     * @param file 上传的图片文件(multipart/form-data,参数名为"file")
     * @return 新创建的资产版本VO
     */
    @PostMapping("/{id}/versions/upload")
    public Result<AssetVersionVO> uploadLocalImage(
            @PathVariable("id") Long assetId,
            @RequestParam("file") MultipartFile file) {
        log.info("收到上传本地图片请求, assetId: {}, fileName: {}, fileSize: {}",
                assetId, file.getOriginalFilename(), file.getSize());
        Long userId = UserContext.getUserId();
        AssetVersionVO version = assetService.uploadLocalImage(userId, assetId, file);
        log.info("图片上传成功, versionId: {}, versionNo: {}", version.id(), version.versionNo());
        return Result.success("上传成功", version);
    }

    /**
     * 从URL上传图片(创建新版本)
     *
     * <p>仏URL下载图片,然后上传到对象存储并创建新版本。
     * 解决前端跨域问题。
     *
     * @param assetId 资产ID(路径参数)
     * @param imageUrl 图片URL
     * @return 新创建的资产版本VO
     */
    @PostMapping("/{id}/versions/upload-from-url")
    public Result<AssetVersionVO> uploadFromUrl(
            @PathVariable("id") Long assetId,
            @RequestParam("imageUrl") String imageUrl) {
        log.info("收到从URL上传图片请求, assetId: {}, imageUrl: {}", assetId, imageUrl);
        Long userId = UserContext.getUserId();
        AssetVersionVO version = assetService.uploadFromUrl(userId, assetId, imageUrl);
        log.info("图片上传成功, versionId: {}, versionNo: {}", version.id(), version.versionNo());
        return Result.success("上传成功", version);
    }

    /**
     * 设置当前版本
     *
     * <p>设置资产的当前使用版本,支持版本回退(切换到历史版本)。
     * 更新asset_refs表中的current_version_id字段。
     *
     * @param assetId 资产ID(路径参数)
     * @param request 设置当前版本请求(包含目标版本ID)
     * @return 操作成功响应
     */
    @PutMapping("/{id}/current")
    public Result<Void> setCurrentVersion(
            @PathVariable("id") Long assetId,
            @Valid @RequestBody SetCurrentVersionRequest request) {
        log.info("收到设置当前版本请求, assetId: {}, versionId: {}", assetId, request.versionId());
        Long userId = UserContext.getUserId();
        assetService.setCurrentVersion(userId, assetId, request);
        log.info("当前版本设置成功");
        return Result.success();
    }

    /**
     * 从 URL 下载资源（返回 blob 流）
     *
     * <p>通过后端代理下载资源，解决前端CORS问题。
     * 主要用于复制图片/视频功能。
     *
     * @param request 下载请求
     * @return 资源二进制流
     */
    @PostMapping("/download-from-url")
    public ResponseEntity<byte[]> downloadFromUrl(@RequestBody DownloadFromUrlRequest request) {
        try {
            log.info("收到下载图片请求, url: {}", request.url());
            
            if (request.url().startsWith("/")) {
                try (InputStream inputStream = storageService.download(request.url())) {
                    byte[] imageBytes = inputStream.readAllBytes();
                    String contentType = inferContentType(request.url());

                    log.info("本地资源读取成功, 大小: {} bytes, contentType: {}", imageBytes.length, contentType);

                    return ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType(contentType))
                            .body(imageBytes);
                }
            }

            URL imageUrl = new URL(request.url());
            java.net.URLConnection connection = imageUrl.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            try (InputStream inputStream = connection.getInputStream()) {
                byte[] imageBytes = inputStream.readAllBytes();

                // 先尝试从响应获取 content type，再按扩展名兜底
                String contentType = connection.getContentType();
                if (contentType == null || contentType.isBlank()) {
                    contentType = inferContentType(request.url());
                }

                log.info("资源下载成功, 大小: {} bytes, contentType: {}", imageBytes.length, contentType);

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .body(imageBytes);
            }
        } catch (Exception e) {
            log.error("下载图片失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 下载图片请求DTO
     */
    public record DownloadFromUrlRequest(String url) {}

    private String inferContentType(String url) {
        String urlStr = url.toLowerCase();
        if (urlStr.endsWith(".png")) {
            return "image/png";
        } else if (urlStr.endsWith(".jpg") || urlStr.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (urlStr.endsWith(".gif")) {
            return "image/gif";
        } else if (urlStr.endsWith(".webp")) {
            return "image/webp";
        } else if (urlStr.endsWith(".mp4")) {
            return "video/mp4";
        }
        return "application/octet-stream";
    }
}
// {{END_MODIFICATIONS}}
