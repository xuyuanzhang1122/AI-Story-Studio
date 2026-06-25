package com.ym.ai_story_studio_server.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目道具（引用全局道具库，支持项目内覆盖）
 */
@Data
@TableName("project_props")
public class ProjectProp {

    /**
     * 项目道具引用ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 引用的全局道具ID
     */
    private Long libraryPropId;

    /**
     * 项目内显示名（可覆盖全局名称）
     */
    private String displayName;

    /**
     * 项目内覆盖描述/提示词（可为空表示使用全局）
     */
    private String overrideDescription;

    /**
     * 项目道具缩略图URL（自定义道具或项目内覆盖的图片）
     */
    private String thumbnailUrl;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
