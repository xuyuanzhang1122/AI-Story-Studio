package com.ym.ai_story_studio_server.service;

import com.ym.ai_story_studio_server.config.StorageProperties;
import com.ym.ai_story_studio_server.entity.Asset;
import com.ym.ai_story_studio_server.entity.AssetVersion;
import com.ym.ai_story_studio_server.mapper.AssetMapper;
import com.ym.ai_story_studio_server.mapper.AssetVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 资产创建服务
 * 
 * <p>专门负责AI生成资产的创建逻辑
 * 
 * @author AI Story Studio
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetCreationService {

    private final AssetMapper assetMapper;
    private final AssetVersionMapper assetVersionMapper;
    private final StorageProperties storageProperties;

    /**
     * 创建资产并保存第一个版本
     * 
     * @param projectId 项目ID
     * @param ownerType 归属对象类型 (如: SHOT, PCHAR, PSCENE, LIB_CHAR, LIB_SCENE)
     * @param ownerId 归属对象ID
     * @param assetType 资产类型 (如: SHOT_IMG, VIDEO)
     * @param ossUrl 当前存储服务返回的URL
     * @param prompt 生成提示词
     * @param model AI模型
     * @param aspectRatio 画幅比例
     * @param userId 创建用户ID
     * @return 创建的Asset对象
     */
    @Transactional(rollbackFor = Exception.class)
    public Asset createAssetWithVersion(
            Long projectId,
            String ownerType,
            Long ownerId,
            String assetType,
            String ossUrl,
            String prompt,
            String model,
            String aspectRatio,
            Long userId
    ) {
        log.debug("创建资产 - ownerType: {}, ownerId: {}, assetType: {}", ownerType, ownerId, assetType);

        // 1. 创建Asset主记录
        Asset asset = new Asset();
        asset.setProjectId(projectId);
        asset.setOwnerType(ownerType);
        asset.setOwnerId(ownerId);
        asset.setAssetType(assetType);
        assetMapper.insert(asset);

        log.info("Asset创建成功 - assetId: {}, ownerType: {}, ownerId: {}", asset.getId(), ownerType, ownerId);

        // 2. 创建第一个版本
        AssetVersion version = new AssetVersion();
        version.setAssetId(asset.getId());
        version.setVersionNo(1);
        version.setSource("AI");
        version.setProvider(getStorageProvider());
        version.setUrl(ossUrl);
        version.setPrompt(prompt);
        version.setStatus("READY");
        version.setCreatedBy(userId);

        // 3. 保存生成参数JSON
        if (model != null || aspectRatio != null) {
            String paramsJson = buildParamsJson(model, aspectRatio);
            version.setParamsJson(paramsJson);
        }

        assetVersionMapper.insert(version);

        log.info("AssetVersion创建成功 - versionId: {}, assetId: {}, url: {}", 
                version.getId(), asset.getId(), ossUrl);

        return asset;
    }

    /**
     * 构建参数JSON字符串
     */
    private String buildParamsJson(String model, String aspectRatio) {
        StringBuilder json = new StringBuilder("{");
        if (model != null) {
            json.append("\"model\":\"").append(model).append("\"");
        }
        if (aspectRatio != null) {
            if (model != null) {
                json.append(",");
            }
            json.append("\"aspectRatio\":\"").append(aspectRatio).append("\"");
        }
        json.append("}");
        return json.toString();
    }

    private String getStorageProvider() {
        String provider = storageProperties.getProvider();
        return provider == null || provider.isBlank() ? "LOCAL" : provider.toUpperCase();
    }
}
