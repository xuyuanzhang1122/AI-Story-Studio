package com.ym.ai_story_studio_server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ym.ai_story_studio_server.common.ResultCode;
import com.ym.ai_story_studio_server.config.StorageProperties;
import com.ym.ai_story_studio_server.dto.ai.ImageGenerateRequest;
import com.ym.ai_story_studio_server.dto.ai.ImageGenerateResponse;
import com.ym.ai_story_studio_server.dto.ai.TextGenerateRequest;
import com.ym.ai_story_studio_server.dto.ai.TextGenerateResponse;
import com.ym.ai_story_studio_server.dto.ai.VideoGenerateRequest;
import com.ym.ai_story_studio_server.dto.ai.VideoGenerateResponse;
import com.ym.ai_story_studio_server.dto.toolbox.ToolboxGenerateRequest;
import com.ym.ai_story_studio_server.dto.toolbox.ToolboxGenerateResponse;
import com.ym.ai_story_studio_server.dto.toolbox.ToolboxHistoryVO;
import com.ym.ai_story_studio_server.entity.Asset;
import com.ym.ai_story_studio_server.entity.AssetVersion;
import com.ym.ai_story_studio_server.entity.Job;
import com.ym.ai_story_studio_server.entity.UsageCharge;
import com.ym.ai_story_studio_server.exception.BusinessException;
import com.ym.ai_story_studio_server.mapper.AssetMapper;
import com.ym.ai_story_studio_server.mapper.AssetVersionMapper;
import com.ym.ai_story_studio_server.mapper.JobMapper;
import com.ym.ai_story_studio_server.mapper.UsageChargeMapper;
import com.ym.ai_story_studio_server.service.AiImageService;
import com.ym.ai_story_studio_server.service.AiTextService;
import com.ym.ai_story_studio_server.service.AiVideoService;
import com.ym.ai_story_studio_server.service.ToolboxService;
import com.ym.ai_story_studio_server.util.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * AI工具箱服务实现
 *
 * <p>实现AI工具箱的核心业务逻辑,包括:
 * <ul>
 *   <li>统一生成接口实现(TEXT/IMAGE/VIDEO路由)</li>
 *   <li>历史记录查询(7天内,分页)</li>
 *   <li>历史记录删除(物理删除,Job表不支持软删除)</li>
 *   <li>保存到资产库(创建Asset和AssetVersion)</li>
 * </ul>
 *
 * <p><strong>依赖服务:</strong>
 * <ul>
 *   <li>AiTextService - 文本生成服务</li>
 *   <li>AiImageService - 图片生成服务</li>
 *   <li>AiVideoService - 视频生成服务</li>
 *   <li>JobMapper - 任务数据访问</li>
 *   <li>UsageChargeMapper - 使用扣费数据访问</li>
 *   <li>AssetMapper - 资产数据访问</li>
 *   <li>AssetVersionMapper - 资产版本数据访问</li>
 * </ul>
 *
 * @author Roo (Prometheus)
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolboxServiceImpl implements ToolboxService {

    private final AiTextService aiTextService;
    private final AiImageService aiImageService;
    private final AiVideoService aiVideoService;
    private final JobMapper jobMapper;
    private final UsageChargeMapper usageChargeMapper;
    private final AssetMapper assetMapper;
    private final AssetVersionMapper assetVersionMapper;
    private final ObjectMapper objectMapper;
    private final StorageProperties storageProperties;

    /**
     * 执行AI生成(统一入口)
     *
     * <p>根据type参数路由到对应的AI服务,并转换请求/响应格式
     *
     * @param request 统一生成请求参数
     * @return 生成响应结果
     * @throws BusinessException 当类型不支持或AI服务调用失败时抛出
     */
    @Override
    public ToolboxGenerateResponse generate(ToolboxGenerateRequest request) {
        Long userId = UserContext.getUserId();
        log.info("工具箱生成请求 - userId: {}, type: {}, promptLength: {}",
                userId, request.type(), request.prompt().length());

        // 根据type路由到不同的AI服务
        return switch (request.type()) {
            case "TEXT" -> generateText(request);
            case "IMAGE" -> generateImage(request);
            case "VIDEO" -> generateVideo(request);
            default -> throw new BusinessException(
                    ResultCode.PARAM_INVALID,
                    "不支持的生成类型: " + request.type()
            );
        };
    }

   /**
     * 文本生成实现（同步）
     *
     * @param request 工具箱生成请求
     * @return 工具箱生成响应
     */
    private ToolboxGenerateResponse generateText(ToolboxGenerateRequest request) {
        // 转换为TextGenerateRequest(参数顺序: prompt, temperature, topP, projectId)
        TextGenerateRequest textRequest = new TextGenerateRequest(
                request.prompt(),
                null,  // temperature使用默认值
                null,  // topP使用默认值
                0L     // projectId=0表示工具箱任务
        );

        // 调用AI文本生成服务（同步）
        TextGenerateResponse textResponse = aiTextService.generateText(textRequest);

        log.info("工具箱文本生成完成 - textLength: {}", textResponse.text() != null ? textResponse.text().length() : 0);

        // 转换为工具箱响应格式（同步任务，直接返回结果，不返回jobId）
        return new ToolboxGenerateResponse(
                null,  // 同步任务不返回jobId
                "SUCCEEDED",
                "TEXT",
                textResponse.model(),
                textResponse.text(),  // 同步任务有结果
                null,  // 文本没有resultUrl
                null,  // 文本没有aspectRatio
                textResponse.costPoints()  // 同步任务扣费完成
        );
    }

    /**
     * 图片生成实现（异步）
     *
     * @param request 工具箱生成请求
     * @return 工具箱生成响应
     */
    private ToolboxGenerateResponse generateImage(ToolboxGenerateRequest request) {
        // 转换为ImageGenerateRequest
        ImageGenerateRequest imageRequest = new ImageGenerateRequest(
                request.prompt(),
                request.model(),  // 使用用户指定的模型
                request.aspectRatio(),
                request.referenceImageUrlList(),
                request.referenceImageUrl(),
                0L   // projectId=0表示工具箱任务
        );

        // 调用AI图片生成服务（异步）
        ImageGenerateResponse imageResponse = aiImageService.generateImageAsync(imageRequest);

        log.info("工具箱图片生成任务已提交 - jobId: {}", imageResponse.jobId());

        // 转换为工具箱响应格式（返回jobId，前端轮询）
        return new ToolboxGenerateResponse(
                imageResponse.jobId(),
                "PENDING",
                "IMAGE",
                imageResponse.model(),
                null,  // 异步任务暂无结果
                null,  // 异步任务暂无resultUrl
                imageResponse.aspectRatio(),
                null   // 异步任务提交时不扣费
        );
    }

    /**
     * 视频生成实现(异步)
     *
     * @param request 工具箱生成请求
     * @return 工具箱生成响应
     */
    private ToolboxGenerateResponse generateVideo(ToolboxGenerateRequest request) {
        // 转换为VideoGenerateRequest(不需要projectId)
        String referenceImageUrl = request.referenceImageUrlList().isEmpty()
                ? request.referenceImageUrl()
                : request.referenceImageUrlList().get(0);

        VideoGenerateRequest videoRequest = new VideoGenerateRequest(
                request.prompt(),
                request.aspectRatio(),
                request.duration(),
                null,
                referenceImageUrl,
                null   // projectId为null(工具箱不关联项目)
        );

        // 调用AI视频生成服务(异步)
        VideoGenerateResponse videoResponse = aiVideoService.generateVideo(videoRequest);

        log.info("工具箱视频生成任务已提交 - jobId: {}, status: {}",
                videoResponse.jobId(), videoResponse.status());

        // 转换为工具箱响应格式
        return new ToolboxGenerateResponse(
                videoResponse.jobId(),
                videoResponse.status(),
                "VIDEO",
                videoResponse.model(),
                null,  // 视频没有text
                null,  // 异步任务,暂时没有resultUrl
                videoResponse.aspectRatio(),
                videoResponse.costPoints()  // 异步任务,提交时为null
        );
    }

    /**
     * 获取生成历史记录(分页)
     *
     * <p>查询7天内的工具箱生成历史,并关联查询积分消耗
     *
     * @param page 页码
     * @param size 每页大小
     * @return 历史记录分页结果
     */
    @Override
    public Page<ToolboxHistoryVO> getHistory(Integer page, Integer size) {
        Long userId = UserContext.getUserId();
        log.info("查询工具箱历史 - userId: {}, page: {}, size: {}", userId, page, size);

        // 构建分页对象
        Page<Job> jobPage = new Page<>(page, size);

        // 构建查询条件(Job表不支持软删除,无需检查deletedAt)
// 工具箱生成的任务特征: projectId=0 且 job_type为TEXT_GENERATION/IMAGE_GENERATION/VIDEO_GENERATION
        LambdaQueryWrapper<Job> query = new LambdaQueryWrapper<>();
        query.eq(Job::getUserId, userId)
                .eq(Job::getProjectId, 0L)  // 工具箱任务projectId为0
                .in(Job::getJobType, "TEXT_GENERATION", "IMAGE_GENERATION", "VIDEO_GENERATION")
                .ge(Job::getCreatedAt, LocalDateTime.now().minusDays(7))  // 7天内
                .orderByDesc(Job::getCreatedAt);

        // 执行查询(配置分页插件后会自动执行COUNT查询获取total)
        Page<Job> result = jobMapper.selectPage(jobPage, query);

        // 使用convert方法转换,保留所有分页信息(total, pages等)
        IPage<ToolboxHistoryVO> voPage = result.convert(this::convertToHistoryVO);

        // 手动构造新的Page对象以确保返回类型正确
        Page<ToolboxHistoryVO> pageResult = new Page<>(voPage.getCurrent(), voPage.getSize(), voPage.getTotal());
        pageResult.setRecords(voPage.getRecords());
        return pageResult;
    }

    /**
     * 将Job实体转换为ToolboxHistoryVO
     *
     * @param job Job实体
     * @return ToolboxHistoryVO
     */
    private ToolboxHistoryVO convertToHistoryVO(Job job) {
        // 解析meta_json
        Map<String, Object> metaData = parseMetaJson(job.getMetaJson());

        // 提取字段
        String type = extractType(job.getJobType());
        String model = (String) metaData.get("model");
        String prompt = (String) metaData.get("prompt");
        String aspectRatio = (String) metaData.get("aspectRatio");
        String resultContent = extractResultContent(type, metaData);

        // 查询积分消耗
        Integer costPoints = queryCostPoints(job.getId());

        // 计算过期时间
        LocalDateTime expireAt = job.getCreatedAt().plusDays(7);

        return new ToolboxHistoryVO(
                job.getId(),
                type,
                model,
                prompt,
                aspectRatio,
                resultContent,
                job.getStatus(),
                costPoints,
                job.getCreatedAt(),
                expireAt
        );
    }

    /**
     * 解析meta_json字段
     *
     * @param metaJson JSON字符串
     * @return Map对象
     */
    private Map<String, Object> parseMetaJson(String metaJson) {
        if (metaJson == null || metaJson.isBlank()) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(metaJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("解析meta_json失败: {}", metaJson, e);
            return Map.of();
        }
    }

    /**
     * 从job_type提取生成类型
     *
     * @param jobType job_type字段值
     * @return TEXT/IMAGE/VIDEO
     */
    private String extractType(String jobType) {
        if (jobType.contains("TEXT")) {
            return "TEXT";
        } else if (jobType.contains("IMAGE")) {
            return "IMAGE";
        } else if (jobType.contains("VIDEO")) {
            return "VIDEO";
        }
        return "UNKNOWN";
    }

    /**
     * 提取生成结果内容
     *
     * @param type 生成类型
     * @param metaData 元数据
     * @return 结果内容
     */
    private String extractResultContent(String type, Map<String, Object> metaData) {
        return switch (type) {
            case "TEXT" -> (String) metaData.get("text");
            case "IMAGE", "VIDEO" -> (String) metaData.get("resultUrl");
            default -> null;
        };
    }

    /**
     * 查询任务的积分消耗
     *
     * @param jobId 任务ID
     * @return 积分消耗,如果未找到则返回null
     */
    private Integer queryCostPoints(Long jobId) {
        LambdaQueryWrapper<UsageCharge> query = new LambdaQueryWrapper<>();
        query.eq(UsageCharge::getJobId, jobId)
                .select(UsageCharge::getTotalCost);

        UsageCharge usageCharge = usageChargeMapper.selectOne(query);
        return usageCharge != null ? usageCharge.getTotalCost() : null;
    }

    /**
     * 删除历史记录(物理删除)
     *
     * <p>只删除自己的记录,直接从数据库删除(Job表不支持软删除)
     *
     * @param jobId 任务ID
     * @throws BusinessException 当任务不存在或无权限时抛出
     */
    @Override
    public void deleteHistory(Long jobId) {
        Long userId = UserContext.getUserId();
        log.info("删除工具箱历史 - userId: {}, jobId: {}", userId, jobId);

        // 查询任务
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException(ResultCode.JOB_NOT_FOUND, "任务不存在");
        }

        // 验证权限
        if (!job.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ACCESS_DENIED, "无权删除他人的历史记录");
        }

        // 物理删除(Job表不支持软删除)
        jobMapper.deleteById(jobId);

        log.info("工具箱历史删除成功 - jobId: {}", jobId);
    }

    /**
     * 保存到资产库
     *
     * <p>将工具箱生成的图片或视频保存为Asset记录
     *
     * @param jobId 任务ID
     * @return 创建的资产ID
     * @throws BusinessException 当任务不存在、状态错误或类型不支持时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveToAsset(Long jobId) {
        Long userId = UserContext.getUserId();
        log.info("保存工具箱结果到资产库 - userId: {}, jobId: {}", userId, jobId);

        // 查询任务
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException(ResultCode.JOB_NOT_FOUND, "任务不存在");
        }

        // 验证权限
        if (!job.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ACCESS_DENIED, "无权操作他人的任务");
        }

        // 验证状态
        if (!"SUCCEEDED".equals(job.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "只能保存已成功完成的任务");
        }

        // 提取type和resultUrl
        Map<String, Object> metaData = parseMetaJson(job.getMetaJson());
        String type = extractType(job.getJobType());
        String resultUrl = (String) metaData.get("resultUrl");

        // 验证类型
        if ("TEXT".equals(type)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "文本生成结果不支持保存到资产库");
        }

        if (resultUrl == null || resultUrl.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "任务结果URL为空,无法保存");
        }

        // 创建Asset记录(Asset表没有userId字段,只有projectId)
        Asset asset = new Asset();
        asset.setAssetType(type);  // IMAGE或VIDEO
        asset.setProjectId(null);  // 工具箱生成的资产不关联项目
        asset.setOwnerType("USER");  // 归属类型为用户
        asset.setOwnerId(userId);  // 归属用户ID
        assetMapper.insert(asset);

        // 创建AssetVersion记录(字段名: versionNo, url, source)
        AssetVersion version = new AssetVersion();
        version.setAssetId(asset.getId());
        version.setVersionNo(1);  // 使用versionNo而非version
        version.setUrl(resultUrl);  // 使用url而非storageUrl
        version.setSource("AI");  // 使用source而非uploadSource,值为AI
        version.setProvider(getStorageProvider());  // 设置存储提供方
        version.setStatus("READY");  // 设置状态为READY
        version.setCreatedBy(userId);  // 设置创建人
        assetVersionMapper.insert(version);

        log.info("工具箱结果保存成功 - assetId: {}, versionId: {}", asset.getId(), version.getId());

        return asset.getId();
    }

    private String getStorageProvider() {
        String provider = storageProperties.getProvider();
        return provider == null || provider.isBlank() ? "LOCAL" : provider.toUpperCase();
    }
}
