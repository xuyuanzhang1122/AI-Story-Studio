package com.ym.ai_story_studio_server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ym.ai_story_studio_server.exception.BusinessException;
import com.ym.ai_story_studio_server.common.ResultCode;
import com.ym.ai_story_studio_server.dto.character.AddCharacterToProjectRequest;
import com.ym.ai_story_studio_server.dto.character.ProjectCharacterVO;
import com.ym.ai_story_studio_server.dto.character.ReplaceCharacterRequest;
import com.ym.ai_story_studio_server.dto.character.UpdateProjectCharacterRequest;
import com.ym.ai_story_studio_server.entity.CharacterLibrary;
import com.ym.ai_story_studio_server.entity.Project;
import com.ym.ai_story_studio_server.entity.ProjectCharacter;
import com.ym.ai_story_studio_server.entity.ShotBinding;
import com.ym.ai_story_studio_server.mapper.CharacterLibraryMapper;
import com.ym.ai_story_studio_server.mapper.ProjectCharacterMapper;
import com.ym.ai_story_studio_server.mapper.ProjectMapper;
import com.ym.ai_story_studio_server.mapper.ShotBindingMapper;
import com.ym.ai_story_studio_server.service.ProjectCharacterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 项目角色服务实现类
 *
 * @author Roo (Prometheus)
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectCharacterServiceImpl implements ProjectCharacterService {

    private final ProjectCharacterMapper projectCharacterMapper;
    private final CharacterLibraryMapper characterLibraryMapper;
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
     * 获取项目角色列表
     *
     * @param userId 当前用户ID
     * @param projectId 项目ID
     * @return 项目角色VO列表
     */
    @Override
    public List<ProjectCharacterVO> getProjectCharacterList(Long userId, Long projectId) {
        log.info("获取项目角色列表, userId: {}, projectId: {}", userId, projectId);

        // 验证项目存在且属于当前用户
        validateProjectOwnership(projectId, userId);

        // 查询项目下所有角色引用
        LambdaQueryWrapper<ProjectCharacter> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ProjectCharacter::getProjectId, projectId)
                .orderByDesc(ProjectCharacter::getCreatedAt);
        List<ProjectCharacter> projectCharacters = projectCharacterMapper.selectList(queryWrapper);

        // 转换为VO
        return projectCharacters.stream().map(pc -> {
            // 查询全局角色信息（可能为null，支持自定义角色）
            CharacterLibrary libChar = pc.getLibraryCharacterId() != null 
                    ? characterLibraryMapper.selectById(pc.getLibraryCharacterId()) 
                    : null;
            
            // 判断是否为自定义角色（未关联角色库）
            boolean isCustomCharacter = (libChar == null);

            String libraryCharacterName;
            String libraryDescription;
            String thumbnailUrl;
            
            if (isCustomCharacter) {
                // 自定义角色：使用项目角色自己的信息
                libraryCharacterName = pc.getDisplayName() != null ? pc.getDisplayName() : "自定义角色";
                libraryDescription = pc.getOverrideDescription();
                thumbnailUrl = pc.getThumbnailUrl();  // 使用项目角色的缩略图
            } else {
                // 关联角色库：使用库的信息
                libraryCharacterName = libChar.getName();
                libraryDescription = libChar.getDescription();
                // 缩略图优先级：项目角色自己的 > 角色库的
                thumbnailUrl = StringUtils.hasText(pc.getThumbnailUrl()) 
                        ? pc.getThumbnailUrl() 
                        : libChar.getThumbnailUrl();
            }

            // displayName: 优先使用项目内显示名称,否则使用全局名称
            String displayName = StringUtils.hasText(pc.getDisplayName())
                    ? pc.getDisplayName()
                    : libraryCharacterName;

            // finalDescription: 优先使用项目内覆盖描述,否则使用全局描述
            String finalDescription = StringUtils.hasText(pc.getOverrideDescription())
                    ? pc.getOverrideDescription()
                    : libraryDescription;

            return new ProjectCharacterVO(
                    pc.getId(),
                    pc.getProjectId(),
                    pc.getLibraryCharacterId(),
                    libraryCharacterName,
                    displayName,
                    finalDescription,
                    pc.getOverrideDescription(),
                    thumbnailUrl,
                    pc.getCreatedAt()
            );
        }).collect(Collectors.toList());
    }

    /**
     * 引用角色到项目
     *
     * @param userId 当前用户ID
     * @param projectId 项目ID
     * @param request 引用角色请求
     * @return 项目角色VO
     */
    @Override
    public ProjectCharacterVO addCharacterToProject(Long userId, Long projectId, AddCharacterToProjectRequest request) {
        log.info("引用角色到项目, userId: {}, projectId: {}, libraryCharacterId: {}",
                userId, projectId, request.libraryCharacterId());

        // 验证项目存在且属于当前用户
        validateProjectOwnership(projectId, userId);

        // 验证全局角色存在且未删除
        CharacterLibrary libChar = characterLibraryMapper.selectById(request.libraryCharacterId());
        if (libChar == null || libChar.getDeletedAt() != null) {
            throw new BusinessException(ResultCode.CHARACTER_NOT_FOUND);
        }

        // 验证全局角色属于当前用户
        if (!libChar.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ACCESS_DENIED);
        }

        // 检查是否已引用
        LambdaQueryWrapper<ProjectCharacter> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(ProjectCharacter::getProjectId, projectId)
                .eq(ProjectCharacter::getLibraryCharacterId, request.libraryCharacterId());
        long count = projectCharacterMapper.selectCount(checkWrapper);
        if (count > 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "该角色已被引用到此项目");
        }

        // 创建项目角色引用
        ProjectCharacter projectCharacter = new ProjectCharacter();
        projectCharacter.setProjectId(projectId);
        projectCharacter.setLibraryCharacterId(request.libraryCharacterId());
        projectCharacter.setDisplayName(request.displayName());

        // 保存到数据库
        projectCharacterMapper.insert(projectCharacter);

        log.info("角色引用成功, projectCharacterId: {}", projectCharacter.getId());

        // displayName: 优先使用项目内显示名称,否则使用全局名称
        String displayName = StringUtils.hasText(request.displayName())
                ? request.displayName()
                : libChar.getName();

        return new ProjectCharacterVO(
                projectCharacter.getId(),
                projectId,
                request.libraryCharacterId(),
                libChar.getName(),
                displayName,
                libChar.getDescription(), // finalDescription默认使用全局描述
                null, // overrideDescription为null
                libChar.getThumbnailUrl(),
                projectCharacter.getCreatedAt()
        );
    }

    /**
     * 更新项目内角色覆盖
     *
     * @param userId 当前用户ID
     * @param projectId 项目ID
     * @param projectCharacterId 项目角色ID
     * @param request 更新项目角色请求
     */
    @Override
    public void updateProjectCharacter(Long userId, Long projectId, Long projectCharacterId, UpdateProjectCharacterRequest request) {
        log.info("更新项目内角色覆盖, userId: {}, projectId: {}, projectCharacterId: {}",
                userId, projectId, projectCharacterId);

        // 查询项目角色是否存在
        ProjectCharacter projectCharacter = projectCharacterMapper.selectById(projectCharacterId);
        if (projectCharacter == null) {
            throw new BusinessException(ResultCode.CHARACTER_NOT_FOUND, "项目角色不存在");
        }

        // 验证项目角色属于指定项目
        if (!projectCharacter.getProjectId().equals(projectId)) {
            throw new BusinessException(ResultCode.ACCESS_DENIED);
        }

        // 验证项目存在且属于当前用户
        validateProjectOwnership(projectId, userId);

        // 更新字段(所有字段可选)
        if (request.displayName() != null) {
            projectCharacter.setDisplayName(request.displayName());
        }
        if (request.overrideDescription() != null) {
            projectCharacter.setOverrideDescription(request.overrideDescription());
        }
        if (request.thumbnailUrl() != null) {
            projectCharacter.setThumbnailUrl(request.thumbnailUrl());
        }
        // 支持更新角色库关联(用于重新关联已删除的角色)
        if (request.libraryCharacterId() != null) {
            // 验证新角色库角色存在且未删除
            CharacterLibrary newLibChar = characterLibraryMapper.selectById(request.libraryCharacterId());
            if (newLibChar == null || newLibChar.getDeletedAt() != null) {
                throw new BusinessException(ResultCode.CHARACTER_NOT_FOUND, "角色库角色不存在");
            }
            // 验证角色库角色属于当前用户
            if (!newLibChar.getUserId().equals(userId)) {
                throw new BusinessException(ResultCode.ACCESS_DENIED, "无权使用此角色库角色");
            }
            projectCharacter.setLibraryCharacterId(request.libraryCharacterId());
            log.info("更新角色库关联, 新libraryCharacterId: {}", request.libraryCharacterId());
        }

        // 保存到数据库
        projectCharacterMapper.updateById(projectCharacter);

        log.info("项目角色覆盖更新成功, projectCharacterId: {}", projectCharacterId);
    }

    /**
     * 移除角色引用
     *
     * @param userId 当前用户ID
     * @param projectId 项目ID
     * @param projectCharacterId 项目角色ID
     */
    @Override
    public void removeCharacterFromProject(Long userId, Long projectId, Long projectCharacterId) {
        log.info("移除角色引用, userId: {}, projectId: {}, projectCharacterId: {}",
                userId, projectId, projectCharacterId);

        // 查询项目角色是否存在
        ProjectCharacter projectCharacter = projectCharacterMapper.selectById(projectCharacterId);
        if (projectCharacter == null) {
            throw new BusinessException(ResultCode.CHARACTER_NOT_FOUND, "项目角色不存在");
        }

        // 验证项目角色属于指定项目
        if (!projectCharacter.getProjectId().equals(projectId)) {
            throw new BusinessException(ResultCode.ACCESS_DENIED);
        }

        // 验证项目存在且属于当前用户
        validateProjectOwnership(projectId, userId);

        // 检查角色是否被分镜绑定
        LambdaQueryWrapper<ShotBinding> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(ShotBinding::getBindType, "PCHAR")
                .eq(ShotBinding::getBindId, projectCharacterId);
        long bindingCount = shotBindingMapper.selectCount(checkWrapper);
        if (bindingCount > 0) {
            throw new BusinessException(ResultCode.CHARACTER_IN_USE,
                    "该角色已被" + bindingCount + "个分镜绑定,请先解绑");
        }

        // 物理删除项目角色引用
        projectCharacterMapper.deleteById(projectCharacterId);

        log.info("角色引用移除成功, projectCharacterId: {}", projectCharacterId);
    }

    /**
     * 替换为其他角色
     *
     * @param userId 当前用户ID
     * @param projectId 项目ID
     * @param projectCharacterId 项目角色ID
     * @param request 替换角色请求
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceCharacter(Long userId, Long projectId, Long projectCharacterId, ReplaceCharacterRequest request) {
        log.info("替换角色, userId: {}, projectId: {}, projectCharacterId: {}, newLibraryCharacterId: {}",
                userId, projectId, projectCharacterId, request.newLibraryCharacterId());

        // 查询当前项目角色是否存在
        ProjectCharacter oldProjectCharacter = projectCharacterMapper.selectById(projectCharacterId);
        if (oldProjectCharacter == null) {
            throw new BusinessException(ResultCode.CHARACTER_NOT_FOUND, "项目角色不存在");
        }

        // 验证项目角色属于指定项目
        if (!oldProjectCharacter.getProjectId().equals(projectId)) {
            throw new BusinessException(ResultCode.ACCESS_DENIED);
        }

        // 验证项目存在且属于当前用户
        validateProjectOwnership(projectId, userId);

        // 验证新全局角色存在且未删除
        CharacterLibrary newLibChar = characterLibraryMapper.selectById(request.newLibraryCharacterId());
        if (newLibChar == null || newLibChar.getDeletedAt() != null) {
            throw new BusinessException(ResultCode.CHARACTER_NOT_FOUND, "新角色不存在");
        }

        // 验证新全局角色属于当前用户
        if (!newLibChar.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ACCESS_DENIED);
        }

        // 检查新角色是否已被引用
        LambdaQueryWrapper<ProjectCharacter> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(ProjectCharacter::getProjectId, projectId)
                .eq(ProjectCharacter::getLibraryCharacterId, request.newLibraryCharacterId());
        long count = projectCharacterMapper.selectCount(checkWrapper);
        if (count > 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "新角色已被引用到此项目,无法替换");
        }

        // 查询所有绑定此角色的分镜
        LambdaQueryWrapper<ShotBinding> bindingWrapper = new LambdaQueryWrapper<>();
        bindingWrapper.eq(ShotBinding::getBindType, "PCHAR")
                .eq(ShotBinding::getBindId, projectCharacterId);
        List<ShotBinding> bindings = shotBindingMapper.selectList(bindingWrapper);

        log.info("找到{}个分镜绑定需要更新", bindings.size());

        // 创建新的项目角色引用
        ProjectCharacter newProjectCharacter = new ProjectCharacter();
        newProjectCharacter.setProjectId(projectId);
        newProjectCharacter.setLibraryCharacterId(request.newLibraryCharacterId());
        newProjectCharacter.setDisplayName(newLibChar.getName()); // 使用新角色的名称作为显示名称

        projectCharacterMapper.insert(newProjectCharacter);

        log.info("创建新项目角色引用成功, newProjectCharacterId: {}", newProjectCharacter.getId());

        // 更新所有分镜绑定关系(将bind_id从旧ID改为新ID)
        if (!bindings.isEmpty()) {
            LambdaUpdateWrapper<ShotBinding> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(ShotBinding::getBindType, "PCHAR")
                    .eq(ShotBinding::getBindId, projectCharacterId)
                    .set(ShotBinding::getBindId, newProjectCharacter.getId());
            shotBindingMapper.update(null, updateWrapper);

            log.info("更新{}个分镜绑定关系成功", bindings.size());
        }

        // 删除旧的项目角色引用
        projectCharacterMapper.deleteById(projectCharacterId);

        log.info("删除旧项目角色引用成功, oldProjectCharacterId: {}", projectCharacterId);
        log.info("角色替换完成");
    }
}
