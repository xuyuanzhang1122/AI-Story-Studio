package com.ym.ai_story_studio_server.dto.prop;

/**
 * 更新项目道具请求
 *
 * @param displayName 项目内显示名(可选)
 * @param overrideDescription 覆盖描述(可选)
 * @param thumbnailUrl 缩略图URL(可选)
 */
public record UpdateProjectPropRequest(
        String displayName,

        String overrideDescription,

        String thumbnailUrl
) {
}
