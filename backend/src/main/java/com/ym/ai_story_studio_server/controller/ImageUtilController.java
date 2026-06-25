package com.ym.ai_story_studio_server.controller;

import com.ym.ai_story_studio_server.common.Result;
import com.ym.ai_story_studio_server.service.StorageService;
import com.ym.ai_story_studio_server.util.ImageMergeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 图片工具Controller
 * 提供图片拼接等工具接口
 *
 * @author Roo (Prometheus)
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/utils/images")
@RequiredArgsConstructor
public class ImageUtilController {

    private final ImageMergeUtil imageMergeUtil;
    private final StorageService storageService;

    /**
     * 拼接多张图片
     *
     * @param request 包含图片URL列表的请求
     * @return 拼接后的图片URL
     */
    @PostMapping("/merge")
    public Result<Map<String, String>> mergeImages(@RequestBody MergeImagesRequest request) {
        log.info("收到图片拼接请求，图片数量: {}", request.imageUrls().size());

        try {
            // 1. 拼接图片
            byte[] mergedImageBytes = imageMergeUtil.mergeImagesHorizontally(request.imageUrls());

            // 2. 保存到当前配置的存储服务
            String mergedImageUrl = storageService.uploadImageBytes(
                    mergedImageBytes,
                    "merged_" + System.currentTimeMillis() + ".png"
            );

            log.info("图片拼接并保存成功: {}", mergedImageUrl);

            Map<String, String> response = new HashMap<>();
            response.put("mergedImageUrl", mergedImageUrl);

            return Result.success(response);

        } catch (Exception e) {
            log.error("图片拼接失败", e);
            return Result.error(500, "图片拼接失败: " + e.getMessage());
        }
    }

    /**
     * 图片拼接请求DTO
     */
    public record MergeImagesRequest(
            List<String> imageUrls
    ) {
    }
}
