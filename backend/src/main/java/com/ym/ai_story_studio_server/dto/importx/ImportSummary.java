package com.ym.ai_story_studio_server.dto.importx;

/**
 * Excel 分镜表格导入摘要
 */
public record ImportSummary(
        int shotsCreated,
        int charactersCreated,
        int scenesCreated,
        int propsCreated,
        int bindingsCreated
) {
}
