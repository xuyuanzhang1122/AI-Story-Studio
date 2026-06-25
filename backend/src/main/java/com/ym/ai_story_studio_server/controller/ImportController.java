package com.ym.ai_story_studio_server.controller;

import com.ym.ai_story_studio_server.common.Result;
import com.ym.ai_story_studio_server.dto.importx.ImportSummary;
import com.ym.ai_story_studio_server.service.ImportService;
import com.ym.ai_story_studio_server.util.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 剧本/分镜导入控制器
 *
 * <p>提供从 MochiAni 风格 .xlsx 导入分镜表格到指定项目的入口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/import")
public class ImportController {

    private final ImportService importService;

    /**
     * 导入分镜表格 Excel
     *
     * <p>解析 4 个 Sheet（分镜/出场人物/场景/道具），按行追加分镜，
     * 同名角色/场景/道具按名称合并，已存在则跳过新建仅补全描述
     *
     * @param projectId 项目 ID
     * @param file      .xlsx 文件
     * @return 导入摘要
     */
    @PostMapping(value = "/excel", consumes = "multipart/form-data")
    public Result<ImportSummary> importExcel(
            @PathVariable("projectId") Long projectId,
            @RequestParam("file") MultipartFile file) {
        Long userId = UserContext.getUserId();
        log.info("导入分镜表格: userId={}, projectId={}, fileName={}, size={}",
                userId, projectId, file.getOriginalFilename(), file.getSize());
        ImportSummary summary = importService.importStoryboardExcel(userId, projectId, file);
        return Result.success("导入成功", summary);
    }
}
