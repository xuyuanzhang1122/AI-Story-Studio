package com.ym.ai_story_studio_server.dto.scene;

import jakarta.validation.constraints.Size;

/**
 * 更新项目场景请求
 *
 * @param displayName 项目内显示名称(可选)
 * @param overrideDescription 项目内覆盖描述(可选)
 * @param thumbnailUrl 缩略图URL(可选)
 */
public record UpdateProjectSceneRequest(
        @Size(max = 100, message = "显示名称长度不能超过100个字符")
        String displayName,

        @Size(max = 2000, message = "覆盖描述不能超过2000个字符")
        String overrideDescription,

        String thumbnailUrl
) {
}
