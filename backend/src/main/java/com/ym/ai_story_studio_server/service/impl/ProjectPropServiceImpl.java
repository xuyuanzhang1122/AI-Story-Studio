package com.ym.ai_story_studio_server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ym.ai_story_studio_server.exception.BusinessException;
import com.ym.ai_story_studio_server.common.ResultCode;
import com.ym.ai_story_studio_server.dto.prop.AddPropToProjectRequest;
import com.ym.ai_story_studio_server.dto.prop.ProjectPropVO;
import com.ym.ai_story_studio_server.dto.prop.UpdateProjectPropRequest;
import com.ym.ai_story_studio_server.entity.Project;
import com.ym.ai_story_studio_server.entity.PropLibrary;
import com.ym.ai_story_studio_server.entity.ProjectProp;
import com.ym.ai_story_studio_server.mapper.ProjectMapper;
import com.ym.ai_story_studio_server.mapper.PropLibraryMapper;
import com.ym.ai_story_studio_server.mapper.ProjectPropMapper;
import com.ym.ai_story_studio_server.service.ProjectPropService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 项目道具服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectPropServiceImpl implements ProjectPropService {

    private final ProjectPropMapper projectPropMapper;
    private final PropLibraryMapper propLibraryMapper;
    private final ProjectMapper projectMapper;

    @Override
    public List<ProjectPropVO> getProjectProps(Long projectId) {
        log.info("获取项目道具列表, projectId: {}", projectId);

        LambdaQueryWrapper<ProjectProp> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ProjectProp::getProjectId, projectId)
                .orderByDesc(ProjectProp::getCreatedAt);

        List<ProjectProp> projectProps = projectPropMapper.selectList(queryWrapper);

        return projectProps.stream().map(pp -> {
            PropLibrary lib = pp.getLibraryPropId() != null ? propLibraryMapper.selectById(pp.getLibraryPropId()) : null;
            String name = lib != null ? lib.getName() : null;
            String description = pp.getOverrideDescription() != null ? pp.getOverrideDescription() :
                    (lib != null ? lib.getDescription() : null);
            String thumbnailUrl = StringUtils.hasText(pp.getThumbnailUrl())
                    ? pp.getThumbnailUrl()
                    : (lib != null ? lib.getThumbnailUrl() : null);

            return new ProjectPropVO(
                    pp.getId(),
                    pp.getLibraryPropId(),
                    pp.getDisplayName(),
                    name,
                    description,
                    thumbnailUrl,
                    pp.getCreatedAt()
            );
        }).collect(Collectors.toList());
    }

    @Override
    public ProjectPropVO addPropToProject(Long userId, Long projectId, AddPropToProjectRequest request) {
        log.info("添加道具到项目, userId: {}, projectId: {}, libraryPropId: {}", userId, projectId, request.libraryPropId());

        // 验证项目归属
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "项目不存在");
        }
        if (!project.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ACCESS_DENIED);
        }

        PropLibrary lib = null;
        if (request.libraryPropId() != null) {
            // 验证道具库存在
            lib = propLibraryMapper.selectById(request.libraryPropId());
            if (lib == null || lib.getDeletedAt() != null) {
                throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "道具库不存在");
            }
            if (!lib.getUserId().equals(userId)) {
                throw new BusinessException(ResultCode.ACCESS_DENIED, "无权访问该道具");
            }

            // 检查是否已添加
            LambdaQueryWrapper<ProjectProp> checkWrapper = new LambdaQueryWrapper<>();
            checkWrapper.eq(ProjectProp::getProjectId, projectId)
                    .eq(ProjectProp::getLibraryPropId, request.libraryPropId());
            if (projectPropMapper.selectCount(checkWrapper) > 0) {
                throw new BusinessException(ResultCode.DUPLICATE_RESOURCE, "该道具已添加到项目中");
            }
        }

        ProjectProp pp = new ProjectProp();
        pp.setProjectId(projectId);
        pp.setLibraryPropId(request.libraryPropId());
        pp.setDisplayName(request.displayName());
        pp.setOverrideDescription(request.overrideDescription());
        pp.setThumbnailUrl(request.thumbnailUrl());

        projectPropMapper.insert(pp);

        log.info("道具添加到项目成功, projectPropId: {}", pp.getId());

        String description = pp.getOverrideDescription() != null ? pp.getOverrideDescription() : (lib != null ? lib.getDescription() : null);
        String thumbnailUrl = StringUtils.hasText(pp.getThumbnailUrl()) ? pp.getThumbnailUrl() : (lib != null ? lib.getThumbnailUrl() : null);

        return new ProjectPropVO(
                pp.getId(),
                pp.getLibraryPropId(),
                pp.getDisplayName(),
                lib != null ? lib.getName() : pp.getDisplayName(),
                description,
                thumbnailUrl,
                pp.getCreatedAt()
        );
    }

    @Override
    public void updateProjectProp(Long userId, Long projectId, Long propId, UpdateProjectPropRequest request) {
        log.info("更新项目道具, userId: {}, projectId: {}, propId: {}", userId, projectId, propId);

        // 验证项目归属
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "项目不存在");
        }
        if (!project.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ACCESS_DENIED);
        }

        ProjectProp pp = projectPropMapper.selectById(propId);
        if (pp == null || !pp.getProjectId().equals(projectId)) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "项目道具不存在");
        }

        if (request.displayName() != null) {
            pp.setDisplayName(request.displayName());
        }
        if (request.overrideDescription() != null) {
            pp.setOverrideDescription(request.overrideDescription());
        }
        if (request.thumbnailUrl() != null) {
            pp.setThumbnailUrl(request.thumbnailUrl());
        }

        projectPropMapper.updateById(pp);

        log.info("项目道具更新成功, propId: {}", propId);
    }

    @Override
    public void removeFromProject(Long userId, Long projectId, Long propId) {
        log.info("从项目移除道具, userId: {}, projectId: {}, propId: {}", userId, projectId, propId);

        // 验证项目归属
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "项目不存在");
        }
        if (!project.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ACCESS_DENIED);
        }

        ProjectProp pp = projectPropMapper.selectById(propId);
        if (pp == null || !pp.getProjectId().equals(projectId)) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "项目道具不存在");
        }

        projectPropMapper.deleteById(propId);

        log.info("道具从项目移除成功, propId: {}", propId);
    }
}
