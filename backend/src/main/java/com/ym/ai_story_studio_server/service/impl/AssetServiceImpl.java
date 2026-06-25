// {{CODE-Cycle-Integration:
//   Task_ID: [#T015]
//   Timestamp: [2025-12-28 16:00:00]
//   Phase: [D-Develop]
//   Context-Analysis: "创建资产版本管理服务实现类,实现版本历史查询、文件上传和版本切换功能。权限验证基于asset->project->user链路。"
//   Principle_Applied: "SOLID原则-单一职责, DRY原则, 权限验证模式"
// }}
// {{START_MODIFICATIONS}}
package com.ym.ai_story_studio_server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ym.ai_story_studio_server.common.ResultCode;
import com.ym.ai_story_studio_server.config.StorageProperties;
import com.ym.ai_story_studio_server.dto.asset.AssetVersionVO;
import com.ym.ai_story_studio_server.dto.asset.SetCurrentVersionRequest;
import com.ym.ai_story_studio_server.entity.Asset;
import com.ym.ai_story_studio_server.entity.AssetRef;
import com.ym.ai_story_studio_server.entity.AssetVersion;
import com.ym.ai_story_studio_server.entity.Project;
import com.ym.ai_story_studio_server.exception.BusinessException;
import com.ym.ai_story_studio_server.mapper.AssetMapper;
import com.ym.ai_story_studio_server.mapper.AssetRefMapper;
import com.ym.ai_story_studio_server.mapper.AssetVersionMapper;
import com.ym.ai_story_studio_server.mapper.ProjectMapper;
import com.ym.ai_story_studio_server.service.AssetService;
import com.ym.ai_story_studio_server.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 资产服务实现类
 *
 * <p>提供资产版本管理功能实现,包括版本历史查询、文件上传和版本切换
 *
 * @author Roo (Prometheus)
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetServiceImpl implements AssetService {

    private final AssetMapper assetMapper;
    private final AssetVersionMapper assetVersionMapper;
    private final AssetRefMapper assetRefMapper;
    private final ProjectMapper projectMapper;
    private final StorageService storageService;
    private final StorageProperties storageProperties;

    /**
     * 验证资产存在且用户有权限访问
     *
     * <p>权限验证链路: asset -> project -> user
     *
     * @param assetId 资产ID
     * @param userId 用户ID
     * @return 验证通过的Asset实体
     * @throws BusinessException 如果资产不存在或无权限访问
     */
    private Asset validateAssetOwnership(Long assetId, Long userId) {
        // 1. 查询资产是否存在
        Asset asset = assetMapper.selectById(assetId);
        if (asset == null) {
            throw new BusinessException(ResultCode.ASSET_NOT_FOUND);
        }

        // 2. 通过projectId验证用户权限
        Project project = projectMapper.selectById(asset.getProjectId());
        if (project == null || project.getDeletedAt() != null) {
            throw new BusinessException(ResultCode.PROJECT_NOT_FOUND);
        }
        if (!project.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ACCESS_DENIED);
        }

        return asset;
    }

    /**
     * 查询当前版本ID
     *
     * <p>根据asset的assetType和ownerId查询asset_refs表获取当前版本ID
     *
     * @param asset 资产实体
     * @return 当前版本ID,如果不存在则返回null
     */
    private Long getCurrentVersionId(Asset asset) {
        String refType = buildRefType(asset);
        LambdaQueryWrapper<AssetRef> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AssetRef::getProjectId, asset.getProjectId())
                .eq(AssetRef::getRefType, refType)
                .eq(AssetRef::getRefOwnerId, asset.getOwnerId());
        AssetRef assetRef = assetRefMapper.selectOne(wrapper);
        return assetRef != null ? assetRef.getAssetVersionId() : null;
    }

    /**
     * 获取资产版本历史列表
     *
     * @param userId 当前用户ID(用于权限验证)
     * @param assetId 资产ID
     * @return 资产版本VO列表,按版本号降序排序
     * @throws BusinessException 如果资产不存在或无权限访问
     */
    @Override
    public List<AssetVersionVO> getVersionHistory(Long userId, Long assetId) {
        log.info("获取资产版本历史列表, userId: {}, assetId: {}", userId, assetId);

        // 验证资产存在且用户有权限
        Asset asset = validateAssetOwnership(assetId, userId);

        // 查询所有版本记录,按版本号降序排序
        LambdaQueryWrapper<AssetVersion> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AssetVersion::getAssetId, assetId)
                .orderByDesc(AssetVersion::getVersionNo);
        List<AssetVersion> versions = assetVersionMapper.selectList(queryWrapper);

        // 查询当前版本ID
        Long currentVersionId = getCurrentVersionId(asset);

        // 转换为VO
        return versions.stream().map(v -> new AssetVersionVO(
                v.getId(),
                v.getAssetId(),
                v.getVersionNo(),
                v.getSource(),
                v.getProvider(),
                v.getUrl(),
                v.getObjectKey(),
                v.getPrompt(),
                v.getParamsJson(),
                v.getStatus(),
                Objects.equals(v.getId(), currentVersionId), // 标记是否为当前版本
                v.getCreatedBy(),
                v.getCreatedAt()
        )).collect(Collectors.toList());
    }

    /**
     * 上传本地图片(创建新版本)
     *
     * @param userId 当前用户ID(用于权限验证)
     * @param assetId 资产ID
     * @param file 上传的图片文件
     * @return 新创建的资产版本VO
     * @throws BusinessException 如果资产不存在、无权限访问或文件上传失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssetVersionVO uploadLocalImage(Long userId, Long assetId, MultipartFile file) {
        log.info("上传本地图片创建新版本, userId: {}, assetId: {}, fileName: {}",
                userId, assetId, file.getOriginalFilename());

        // 验证资产存在且用户有权限
        Asset asset = validateAssetOwnership(assetId, userId);

        // 验证文件类型(仅支持图片)
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException(ResultCode.ASSET_TYPE_UNSUPPORTED, "仅支持上传图片文件");
        }

        // 验证文件大小(限制10MB)
        long maxSize = 10 * 1024 * 1024; // 10MB
        if (file.getSize() > maxSize) {
            throw new BusinessException(ResultCode.ASSET_SIZE_EXCEEDED, "文件大小不能超过10MB");
        }

        // 查询最大版本号
        LambdaQueryWrapper<AssetVersion> versionWrapper = new LambdaQueryWrapper<>();
        versionWrapper.eq(AssetVersion::getAssetId, assetId)
                .orderByDesc(AssetVersion::getVersionNo)
                .last("LIMIT 1");
        AssetVersion latestVersion = assetVersionMapper.selectOne(versionWrapper);
        int newVersionNo = (latestVersion != null) ? latestVersion.getVersionNo() + 1 : 1;

        log.info("生成新版本号: {}", newVersionNo);

        // 上传文件到当前配置的存储服务
        String url;
        try {
            url = storageService.upload(
                    file.getInputStream(),
                    file.getOriginalFilename(),
                    contentType
            );
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new BusinessException(ResultCode.ASSET_UPLOAD_FAILED, "文件上传失败: " + e.getMessage());
        }

        log.info("文件上传成功, url: {}", url);

        // 创建AssetVersion记录
        AssetVersion newVersion = new AssetVersion();
        newVersion.setAssetId(assetId);
        newVersion.setVersionNo(newVersionNo);
        newVersion.setSource("UPLOAD");
        newVersion.setProvider(getStorageProvider());
        newVersion.setUrl(url);
        newVersion.setObjectKey(extractObjectKey(url)); // 从URL中提取ObjectKey
        newVersion.setStatus("READY");
        newVersion.setCreatedBy(userId);

        assetVersionMapper.insert(newVersion);

        log.info("新版本创建成功, versionId: {}, versionNo: {}", newVersion.getId(), newVersionNo);

        return new AssetVersionVO(
                newVersion.getId(),
                assetId,
                newVersionNo,
                "UPLOAD",
                getStorageProvider(),
                url,
                newVersion.getObjectKey(),
                null,
                null,
                "READY",
                false, // 新上传的版本默认不是当前版本
                userId,
                newVersion.getCreatedAt()
        );
    }

    /**
     * 从URL上传图片(创建新版本)
     *
     * @param userId 当前用户ID(用于权限验证)
     * @param assetId 资产ID
     * @param imageUrl 图片URL
     * @return 新创建的资产版本VO
     * @throws BusinessException 如果资产不存在、无权限访问或下载/上传失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssetVersionVO uploadFromUrl(Long userId, Long assetId, String imageUrl) {
        log.info("从URL上传图片创建新版本, userId: {}, assetId: {}, imageUrl: {}",
                userId, assetId, imageUrl);

        // 验证资产存在且用户有权限
        Asset asset = validateAssetOwnership(assetId, userId);

        // 查询最大版本号
        LambdaQueryWrapper<AssetVersion> versionWrapper = new LambdaQueryWrapper<>();
        versionWrapper.eq(AssetVersion::getAssetId, assetId)
                .orderByDesc(AssetVersion::getVersionNo)
                .last("LIMIT 1");
        AssetVersion latestVersion = assetVersionMapper.selectOne(versionWrapper);
        int newVersionNo = (latestVersion != null) ? latestVersion.getVersionNo() + 1 : 1;

        log.info("生成新版本号: {}", newVersionNo);

        // 从URL下载图片
        String url;
        try {
            log.info("开始从URL下载图片: {}", imageUrl);
            URL imageUrlObj = new URL(imageUrl);
            try (InputStream inputStream = imageUrlObj.openStream()) {
                // 读取所有字节到内存
                byte[] imageBytes = inputStream.readAllBytes();
                log.info("图片下载成功, 大小: {} bytes", imageBytes.length);

                // 验证文件大小(限制10MB)
                long maxSize = 10 * 1024 * 1024; // 10MB
                if (imageBytes.length > maxSize) {
                    throw new BusinessException(ResultCode.ASSET_SIZE_EXCEEDED, "文件大小不能超过10MB");
                }

                // 从 URL 提取文件名和类型
                String fileName = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
                if (!fileName.contains(".")) {
                    fileName = "image_" + System.currentTimeMillis() + ".png";
                }
                String contentType = "image/" + fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
                log.info("提取文件名: {}, contentType: {}", fileName, contentType);

                // 上传到当前配置的存储服务
                url = storageService.upload(
                        new ByteArrayInputStream(imageBytes),
                        fileName,
                        contentType
                );
            }
        } catch (IOException e) {
            log.error("从URL下载或上传图片失败", e);
            throw new BusinessException(ResultCode.ASSET_UPLOAD_FAILED, "下载或上传图片失败: " + e.getMessage());
        }

        log.info("文件上传成功, url: {}", url);

        // 创建AssetVersion记录
        AssetVersion newVersion = new AssetVersion();
        newVersion.setAssetId(assetId);
        newVersion.setVersionNo(newVersionNo);
        newVersion.setSource("UPLOAD");
        newVersion.setProvider(getStorageProvider());
        newVersion.setUrl(url);
        newVersion.setObjectKey(extractObjectKey(url));
        newVersion.setStatus("READY");
        newVersion.setCreatedBy(userId);

        assetVersionMapper.insert(newVersion);

        log.info("新版本创建成功, versionId: {}, versionNo: {}", newVersion.getId(), newVersionNo);

        return new AssetVersionVO(
                newVersion.getId(),
                assetId,
                newVersionNo,
                "UPLOAD",
                getStorageProvider(),
                url,
                newVersion.getObjectKey(),
                null,
                null,
                "READY",
                false, // 新上传的版本默认不是当前版本
                userId,
                newVersion.getCreatedAt()
        );
    }

    /**
     * 设置当前版本
     *
     * @param userId 当前用户ID(用于权限验证)
     * @param assetId 资产ID
     * @param request 设置当前版本请求(包含目标版本ID)
     * @throws BusinessException 如果资产不存在、版本不存在或无权限访问
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setCurrentVersion(Long userId, Long assetId, SetCurrentVersionRequest request) {
        log.info("设置当前版本, userId: {}, assetId: {}, versionId: {}",
                userId, assetId, request.versionId());

        // 验证资产存在且用户有权限
        Asset asset = validateAssetOwnership(assetId, userId);

        // 验证版本存在且属于指定资产
        AssetVersion version = assetVersionMapper.selectById(request.versionId());
        if (version == null) {
            throw new BusinessException(ResultCode.ASSET_VERSION_NOT_FOUND);
        }
        if (!version.getAssetId().equals(assetId)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "版本不属于指定资产");
        }

        // 查询或创建AssetRef记录
        String refType = buildRefType(asset);
        LambdaQueryWrapper<AssetRef> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AssetRef::getProjectId, asset.getProjectId())
                .eq(AssetRef::getRefType, refType)
                .eq(AssetRef::getRefOwnerId, asset.getOwnerId());
        AssetRef assetRef = assetRefMapper.selectOne(wrapper);

        if (assetRef == null) {
            // 首次设置,插入记录
            assetRef = new AssetRef();
            assetRef.setProjectId(asset.getProjectId());
            assetRef.setRefType(refType);
            assetRef.setRefOwnerId(asset.getOwnerId());
            assetRef.setAssetVersionId(request.versionId());
            assetRefMapper.insert(assetRef);
            log.info("创建AssetRef记录, refId: {}, refType: {}", assetRef.getId(), refType);
        } else {
            // 更新记录
            assetRef.setAssetVersionId(request.versionId());
            assetRefMapper.updateById(assetRef);
            log.info("更新AssetRef记录, refId: {}, refType: {}", assetRef.getId(), refType);
        }

        log.info("当前版本设置成功");
    }

    /**
     * 从URL中提取ObjectKey
     *
     * <p>示例: https://bucket.oss-cn-hangzhou.aliyuncs.com/2025/12/28/xxx.jpg
     * -> 2025/12/28/xxx.jpg
     *
     * @param url 完整URL
     * @return ObjectKey
     */
    private String extractObjectKey(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        StorageProperties.LocalConfig localConfig = storageProperties.getLocal();
        String localUrlPrefix = localConfig != null && localConfig.getUrlPrefix() != null
                ? localConfig.getUrlPrefix().replaceAll("/+$", "")
                : "/uploads";
        if (!localUrlPrefix.startsWith("/")) {
            localUrlPrefix = "/" + localUrlPrefix;
        }
        String normalizedUrl = url.replace('\\', '/');
        int localPrefixIndex = normalizedUrl.indexOf(localUrlPrefix + "/");
        if (localPrefixIndex >= 0) {
            return normalizedUrl.substring(localPrefixIndex + localUrlPrefix.length() + 1);
        }

        // 简单实现:提取域名后的路径部分
        int domainEndIndex = url.indexOf('/', url.indexOf("//") + 2);
        if (domainEndIndex != -1 && domainEndIndex < url.length() - 1) {
            return url.substring(domainEndIndex + 1);
        }
        return null;
    }

    private String getStorageProvider() {
        String provider = storageProperties.getProvider();
        return provider == null || provider.isBlank() ? "LOCAL" : provider.toUpperCase();
    }

    /**
     * 构建AssetRef的refType
     * 
     * <p>根据资产类型和归属对象类型构建refType:
     * - LIB_CHAR的IMAGE资产 -> LIB_CHAR_CURRENT
     * - PCHAR的IMAGE资产 -> PCHAR_CURRENT
     * - LIB_SCENE的IMAGE资产 -> LIB_SCENE_CURRENT
     * - PSCENE的IMAGE资产 -> PSCENE_CURRENT
     * - SHOT的SHOT_IMG资产 -> SHOT_IMG_CURRENT
     * - SHOT的VIDEO资产 -> SHOT_VIDEO_CURRENT
     * - PPROP的IMAGE资产 -> PPROP_CURRENT
     * 
     * @param asset 资产实体
     * @return refType字符串
     */
    private String buildRefType(Asset asset) {
        String ownerType = asset.getOwnerType();
        String assetType = asset.getAssetType();
        
        // 对于SHOT类型的owner，需要根据assetType区分
        if ("SHOT".equals(ownerType)) {
            if ("SHOT_IMG".equals(assetType)) {
                return "SHOT_IMG_CURRENT";
            } else if ("VIDEO".equals(assetType)) {
                return "SHOT_VIDEO_CURRENT";
            }
        }
        
        // 对于LIB_SCENE类型的owner
        if ("LIB_SCENE".equals(ownerType)) {
            return "LIB_SCENE_CURRENT";
        }
        
        // 对于PSCENE类型的owner
        if ("PSCENE".equals(ownerType)) {
            return "PSCENE_CURRENT";
        }
        
        // 其他类型（LIB_CHAR、PCHAR、PPROP等）直接使用ownerType + "_CURRENT"
        return ownerType + "_CURRENT";
    }
}
// {{END_MODIFICATIONS}}
