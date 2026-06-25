package com.ym.ai_story_studio_server.dto.character;

import jakarta.validation.constraints.Size;

/**
 * 更新项目角色请求
 *
 * @param displayName 项目内显示名称
 * @param overrideDescription 项目内覆盖描述
 * @param libraryCharacterId 新的角色库ID(用于重新关联)
 * @param thumbnailUrl 缩略图URL(可选)
 * @author Roo (Prometheus)
 * @since 1.0.0
 */
public record UpdateProjectCharacterRequest(
        @Size(min = 1, max = 100, message = "显示名称长度必须在1-100个字符之间")
        String displayName,

        @Size(max = 5000, message = "覆盖描述不能超过5000个字符")
        String overrideDescription,
        
        Long libraryCharacterId,

        String thumbnailUrl
) {
}
