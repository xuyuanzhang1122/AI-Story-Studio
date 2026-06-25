package com.ym.ai_story_studio_server.service;

import com.ym.ai_story_studio_server.dto.importx.ImportSummary;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * Excel 分镜表格导入服务
 *
 * <p>支持解析符合 MochiAni 导出格式的 .xlsx：
 * <ul>
 *   <li>Sheet "分镜"：序号 | 剧本 | 出场人物 | 场景 | 道具 | 分镜图</li>
 *   <li>Sheet "出场人物"：名称 | 描述词 | 图片</li>
 *   <li>Sheet "场景"：名称 | 描述词 | 图片</li>
 *   <li>Sheet "道具"：名称 | 描述词 | 图片</li>
 * </ul>
 *
 * <p>采用追加模式：保留项目现有分镜，新分镜接在末尾。
 */
public interface ImportService {

    /**
     * 导入 Excel 分镜表格到指定项目
     *
     * @param userId    当前用户 ID
     * @param projectId 项目 ID
     * @param file      .xlsx 文件
     * @return 导入结果摘要
     */
    ImportSummary importStoryboardExcel(Long userId, Long projectId, MultipartFile file, List<MultipartFile> assetFiles);

    /**
     * 为已导入项目补充上传角色/场景/道具图片。
     *
     * <p>图片文件按名称匹配项目内资源，支持类似 {@code 001_黎呦呦_gpt-image-2.png}
     * 的导出文件名。
     *
     * @param userId     当前用户 ID
     * @param projectId  项目 ID
     * @param assetFiles 图片文件列表
     * @return 导入结果摘要
     */
    ImportSummary importAssetImages(Long userId, Long projectId, List<MultipartFile> assetFiles);
}
