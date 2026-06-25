package com.ym.ai_story_studio_server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ym.ai_story_studio_server.exception.BusinessException;
import com.ym.ai_story_studio_server.common.ResultCode;
import com.ym.ai_story_studio_server.dto.scene.AddSceneToProjectRequest;
import com.ym.ai_story_studio_server.dto.scene.ProjectSceneVO;
import com.ym.ai_story_studio_server.dto.scene.ReplaceSceneRequest;
import com.ym.ai_story_studio_server.dto.scene.UpdateProjectSceneRequest;
import com.ym.ai_story_studio_server.entity.SceneLibrary;
import com.ym.ai_story_studio_server.entity.Project;
import com.ym.ai_story_studio_server.entity.ProjectScene;
import com.ym.ai_story_studio_server.entity.ShotBinding;
import com.ym.ai_story_studio_server.mapper.SceneLibraryMapper;
import com.ym.ai_story_studio_server.mapper.ProjectSceneMapper;
import com.ym.ai_story_studio_server.mapper.ProjectMapper;
import com.ym.ai_story_studio_server.mapper.ShotBindingMapper;
import com.ym.ai_story_studio_server.service.ProjectSceneService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 项目场景服务实现类
 *
 * <p>提供项目场景管理功能实现
 *
 * @author Roo (Prometheus)
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectSceneServiceImpl implements ProjectSceneService {

    private final ProjectSceneMapper projectSceneMapper;
    private final SceneLibraryMapper sceneLibraryMapper;
    private final ProjectMapper projectMapper;
    private final ShotBindingMapper shotBindingMapper;

    /**
     * 验证项目存在且属于当前用户
     *
     * @param projectId 项目ID
     * @param userId 用户ID
     */
    private void validateProjectOwnership(Long projectId, Long userId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw new BusinessException(ResultCode.PROJECT_NOT_FOUND);
        }
        if (!project.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ACCESS_DENIED);
        }
    }

    /**
     * 获取项目场景列表
     *
     * @param userId 当前用户ID
     * @param projectId 项目ID
     * @return 项目场景VO列表
     */
    @Override
    public List<ProjectSceneVO> getProjectSceneList(Long userId, Long projectId) {
        log.info("获取项目场景列表, userId: {}, projectId: {}", userId, projectId);

        // 验证项目存在且属于当前用户
        validateProjectOwnership(projectId, userId);

        // 查询项目下所有场景引用
        LambdaQueryWrapper<ProjectScene> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ProjectScene::getProjectId, projectId)
                .orderByDesc(ProjectScene::getCreatedAt);
        List<ProjectScene> projectScenes = projectSceneMapper.selectList(queryWrapper);

        // 转换为VO
        return projectScenes.stream().map(ps -> {
            // 查询全局场景信息（可能为null，自定义场景不关联场景库）
            SceneLibrary libScene = ps.getLibrarySceneId() != null 
                    ? sceneLibraryMapper.selectById(ps.getLibrarySceneId()) 
                    : null;

            // librarySceneName: 优先使用场景库名称，自定义场景显示"自定义场景"
            String librarySceneName = (libScene != null) ? libScene.getName() : "自定义场景";
            String libraryDescription = (libScene != null) ? libScene.getDescription() : null;
            
            // thumbnailUrl: 优先使用项目场景的缩略图，其次使用场景库的缩略图
            String thumbnailUrl = StringUtils.hasText(ps.getThumbnailUrl()) 
                    ? ps.getThumbnailUrl() 
                    : (libScene != null ? libScene.getThumbnailUrl() : null);

            // displayName: 优先使用项目内显示名称,否则使用全局名称
            String displayName = StringUtils.hasText(ps.getDisplayName())
                    ? ps.getDisplayName()
                    : librarySceneName;

            // finalDescription: 优先使用项目内覆盖描述,否则使用全局描述
            String finalDescription = StringUtils.hasText(ps.getOverrideDescription())
                    ? ps.getOverrideDescription()
                    : libraryDescription;

            return new ProjectSceneVO(
                    ps.getId(),
                    ps.getProjectId(),
                    ps.getLibrarySceneId(),
                    librarySceneName,
                    displayName,
                    finalDescription,
                    ps.getOverrideDescription(),
                    thumbnailUrl,
                    ps.getCreatedAt()
            );
        }).collect(Collectors.toList());
    }

    /**
     * 引用场景到项目
     *
     * @param userId 当前用户ID
     * @param projectId 项目ID
     * @param request 引用场景请求
     * @return 项目场景VO
     */
    @Override
    public ProjectSceneVO addSceneToProject(Long userId, Long projectId, AddSceneToProjectRequest request) {
        log.info("引用场景到项目, userId: {}, projectId: {}, librarySceneId: {}",
                userId, projectId, request.librarySceneId());

        // 验证项目存在且属于当前用户
        validateProjectOwnership(projectId, userId);

        // 验证全局场景存在且未删除
        SceneLibrary libScene = sceneLibraryMapper.selectById(request.librarySceneId());
        if (libScene == null || libScene.getDeletedAt() != null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "场景不存在");
        }

        // 验证全局场景属于当前用户
        if (!libScene.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ACCESS_DENIED);
        }

        // 检查是否已引用
        LambdaQueryWrapper<ProjectScene> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(ProjectScene::getProjectId, projectId)
                .eq(ProjectScene::getLibrarySceneId, request.librarySceneId());
        long count = projectSceneMapper.selectCount(checkWrapper);
        if (count > 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "该场景已被引用到此项目");
        }

        // 创建项目场景引用
        ProjectScene projectScene = new ProjectScene();
        projectScene.setProjectId(projectId);
        projectScene.setLibrarySceneId(request.librarySceneId());
        projectScene.setDisplayName(request.displayName());

        // 保存到数据库
        projectSceneMapper.insert(projectScene);

        log.info("场景引用成功, projectSceneId: {}", projectScene.getId());

        // displayName: 优先使用项目内显示名称,否则使用全局名称
        String displayName = StringUtils.hasText(request.displayName())
                ? request.displayName()
                : libScene.getName();

        return new ProjectSceneVO(
                projectScene.getId(),
                projectId,
                request.librarySceneId(),
                libScene.getName(),
                displayName,
                libScene.getDescription(), // finalDescription默认使用全局描述
                null, // overrideDescription为null
                libScene.getThumbnailUrl(), // thumbnailUrl
                projectScene.getCreatedAt()
        );
    }

    /**
     * 更新项目内场景覆盖
     *
     * @param userId 当前用户ID
     * @param projectId 项目ID
     * @param projectSceneId 项目场景ID
     * @param request 更新项目场景请求
     */
    @Override
    public void updateProjectScene(Long userId, Long projectId, Long projectSceneId, UpdateProjectSceneRequest request) {
        log.info("更新项目内场景覆盖, userId: {}, projectId: {}, projectSceneId: {}",
                userId, projectId, projectSceneId);

        // 查询项目场景是否存在
        ProjectScene projectScene = projectSceneMapper.selectById(projectSceneId);
        if (projectScene == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "项目场景不存在");
        }

        // 验证项目场景属于指定项目
        if (!projectScene.getProjectId().equals(projectId)) {
            throw new BusinessException(ResultCode.ACCESS_DENIED);
        }

        // 验证项目存在且属于当前用户
        validateProjectOwnership(projectId, userId);

        // 更新字段(所有字段可选)
        if (request.displayName() != null) {
            projectScene.setDisplayName(request.displayName());
        }
        if (request.overrideDescription() != null) {
            projectScene.setOverrideDescription(request.overrideDescription());
        }
        if (request.thumbnailUrl() != null) {
            projectScene.setThumbnailUrl(request.thumbnailUrl());
        }

        // 保存到数据库
        projectSceneMapper.updateById(projectScene);

        log.info("项目场景覆盖更新成功, projectSceneId: {}", projectSceneId);
    }

    /**
     * 移除场景引用
     *
     * @param userId 当前用户ID
     * @param projectId 项目ID
     * @param projectSceneId 项目场景ID
     */
    @Override
    public void removeSceneFromProject(Long userId, Long projectId, Long projectSceneId) {
        log.info("移除场景引用, userId: {}, projectId: {}, projectSceneId: {}",
                userId, projectId, projectSceneId);

        // 查询项目场景是否存在
        ProjectScene projectScene = projectSceneMapper.selectById(projectSceneId);
        if (projectScene == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "项目场景不存在");
        }

        // 验证项目场景属于指定项目
        if (!projectScene.getProjectId().equals(projectId)) {
            throw new BusinessException(ResultCode.ACCESS_DENIED);
        }

        // 验证项目存在且属于当前用户
        validateProjectOwnership(projectId, userId);

        // 检查场景是否被分镜绑定
        LambdaQueryWrapper<ShotBinding> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(ShotBinding::getBindType, "PSCENE")
                .eq(ShotBinding::getBindId, projectSceneId);
        long bindingCount = shotBindingMapper.selectCount(checkWrapper);
        if (bindingCount > 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "该场景已被" + bindingCount + "个分镜绑定,请先解绑");
        }

        // 物理删除项目场景引用
        projectSceneMapper.deleteById(projectSceneId);

        log.info("场景引用移除成功, projectSceneId: {}", projectSceneId);
    }

    /**
     * 替换为其他场景
     *
     * @param userId 当前用户ID
     * @param projectId 项目ID
     * @param projectSceneId 项目场景ID
     * @param request 替换场景请求
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceScene(Long userId, Long projectId, Long projectSceneId, ReplaceSceneRequest request) {
        log.info("替换场景, userId: {}, projectId: {}, projectSceneId: {}, newLibrarySceneId: {}",
                userId, projectId, projectSceneId, request.newLibrarySceneId());

        // 查询当前项目场景是否存在
        ProjectScene oldProjectScene = projectSceneMapper.selectById(projectSceneId);
        if (oldProjectScene == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "项目场景不存在");
        }

        // 验证项目场景属于指定项目
        if (!oldProjectScene.getProjectId().equals(projectId)) {
            throw new BusinessException(ResultCode.ACCESS_DENIED);
        }

        // 验证项目存在且属于当前用户
        validateProjectOwnership(projectId, userId);

        // 验证新全局场景存在且未删除
        SceneLibrary newLibScene = sceneLibraryMapper.selectById(request.newLibrarySceneId());
        if (newLibScene == null || newLibScene.getDeletedAt() != null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "新场景不存在");
        }

        // 验证新全局场景属于当前用户
        if (!newLibScene.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ACCESS_DENIED);
        }

        // 检查新场景是否已被引用
        LambdaQueryWrapper<ProjectScene> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(ProjectScene::getProjectId, projectId)
                .eq(ProjectScene::getLibrarySceneId, request.newLibrarySceneId());
        long count = projectSceneMapper.selectCount(checkWrapper);
        if (count > 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "新场景已被引用到此项目,无法替换");
        }

        // 查询所有绑定此场景的分镜
        LambdaQueryWrapper<ShotBinding> bindingWrapper = new LambdaQueryWrapper<>();
        bindingWrapper.eq(ShotBinding::getBindType, "PSCENE")
                .eq(ShotBinding::getBindId, projectSceneId);
        List<ShotBinding> bindings = shotBindingMapper.selectList(bindingWrapper);

        log.info("找到{}个分镜绑定需要更新", bindings.size());

        // 创建新的项目场景引用
        ProjectScene newProjectScene = new ProjectScene();
        newProjectScene.setProjectId(projectId);
        newProjectScene.setLibrarySceneId(request.newLibrarySceneId());
        newProjectScene.setDisplayName(newLibScene.getName()); // 使用新场景的名称作为显示名称

        projectSceneMapper.insert(newProjectScene);

        log.info("创建新项目场景引用成功, newProjectSceneId: {}", newProjectScene.getId());

        // 更新所有分镜绑定关系(将bind_id从旧ID改为新ID)
        if (!bindings.isEmpty()) {
            LambdaUpdateWrapper<ShotBinding> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(ShotBinding::getBindType, "PSCENE")
                    .eq(ShotBinding::getBindId, projectSceneId)
                    .set(ShotBinding::getBindId, newProjectScene.getId());
            shotBindingMapper.update(null, updateWrapper);

            log.info("更新{}个分镜绑定关系成功", bindings.size());
        }

        // 删除旧的项目场景引用
        projectSceneMapper.deleteById(projectSceneId);

        log.info("删除旧项目场景引用成功, oldProjectSceneId: {}", projectSceneId);
        log.info("场景替换完成");
    }
}
