package com.ym.ai_story_studio_server.dto.prop;

/**
 * 添加道具到项目请求
 *
 * @param libraryPropId 全局道具库ID
 * @param displayName 项目内显示名(可选)
 * @param overrideDescription 覆盖描述(可选)
 * @param thumbnailUrl 缩略图URL(可选)
 */
public record AddPropToProjectRequest(
        Long libraryPropId,

        String displayName,

        String overrideDescription,

        String thumbnailUrl
) {
}
