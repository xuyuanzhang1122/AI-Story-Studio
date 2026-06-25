package com.ym.ai_story_studio_server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ym.ai_story_studio_server.exception.BusinessException;
import com.ym.ai_story_studio_server.common.ResultCode;
import com.ym.ai_story_studio_server.dto.character.CharacterVO;
import com.ym.ai_story_studio_server.dto.character.CreateCharacterRequest;
import com.ym.ai_story_studio_server.dto.character.UpdateCharacterRequest;
import com.ym.ai_story_studio_server.entity.CharacterCategory;
import com.ym.ai_story_studio_server.entity.CharacterLibrary;
import com.ym.ai_story_studio_server.entity.ProjectCharacter;
import com.ym.ai_story_studio_server.entity.ShotBinding;
import com.ym.ai_story_studio_server.mapper.CharacterCategoryMapper;
import com.ym.ai_story_studio_server.mapper.ShotBindingMapper;
import com.ym.ai_story_studio_server.mapper.CharacterLibraryMapper;
import com.ym.ai_story_studio_server.mapper.ProjectCharacterMapper;
import com.ym.ai_story_studio_server.service.CharacterLibraryService;
import com.ym.ai_story_studio_server.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色库服务实现类
 *
 * @author Roo (Prometheus)
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterLibraryServiceImpl implements CharacterLibraryService {

    private final CharacterLibraryMapper characterLibraryMapper;
    private final CharacterCategoryMapper categoryMapper;
    private final ProjectCharacterMapper projectCharacterMapper;
    private final ShotBindingMapper shotBindingMapper;
    private final StorageService storageService;

    /**
     * 获取角色库列表(支持搜索和筛选)
     *
     * @param userId 当前用户ID
     * @param categoryId 分类ID(可选)
     * @param keyword 搜索关键词(可选)
     * @return 角色VO列表
     */
    @Override
    public List<CharacterVO> getCharacterList(Long userId, Long categoryId, String keyword) {
        log.info("获取角色库列表, userId: {}, categoryId: {}, keyword: {}", userId, categoryId, keyword);

        // 构建查询条件
        LambdaQueryWrapper<CharacterLibrary> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CharacterLibrary::getUserId, userId)
                .isNull(CharacterLibrary::getDeletedAt);

        // 动态条件
        if (categoryId != null) {
            queryWrapper.eq(CharacterLibrary::getCategoryId, categoryId);
        }
        if (StringUtils.hasText(keyword)) {
            queryWrapper.like(CharacterLibrary::getName, keyword);
        }

        queryWrapper.orderByDesc(CharacterLibrary::getUpdatedAt);

        // 执行查询
        List<CharacterLibrary> characters = characterLibraryMapper.selectList(queryWrapper);

        // 转换为VO
        return characters.stream().map(character -> {
            // 查询分类名称
            String categoryName = null;
            if (character.getCategoryId() != null) {
                CharacterCategory category = categoryMapper.selectById(character.getCategoryId());
                if (category != null) {
                    categoryName = category.getName();
                }
            }

            // 统计引用次数
            LambdaQueryWrapper<ProjectCharacter> countWrapper = new LambdaQueryWrapper<>();
            countWrapper.eq(ProjectCharacter::getLibraryCharacterId, character.getId());
            long referenceCount = projectCharacterMapper.selectCount(countWrapper);

            return new CharacterVO(
                    character.getId(),
                    character.getCategoryId(),
                    categoryName,
                    character.getName(),
                    character.getDescription(),
                    character.getThumbnailUrl(),
                    (int) referenceCount,
                    character.getCreatedAt(),
                    character.getUpdatedAt()
            );
        }).collect(Collectors.toList());
    }

    /**
     * 创建角色
     *
     * @param userId 当前用户ID
     * @param request 创建角色请求
     * @return 角色VO
     */
    @Override
    public CharacterVO createCharacter(Long userId, CreateCharacterRequest request) {
        log.info("创建角色, userId: {}, name: {}", userId, request.name());

        // 如果指定了分类,验证分类是否存在且属于当前用户
        if (request.categoryId() != null) {
            CharacterCategory category = categoryMapper.selectById(request.categoryId());
            if (category == null) {
                throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "角色分类不存在");
            }
            if (!category.getUserId().equals(userId)) {
                throw new BusinessException(ResultCode.ACCESS_DENIED);
            }
        }

        // 创建角色实体
        CharacterLibrary character = new CharacterLibrary();
        character.setUserId(userId);
        character.setCategoryId(request.categoryId());
        character.setName(request.name());
        character.setDescription(request.description());

        // 保存到数据库
        characterLibraryMapper.insert(character);

        log.info("角色创建成功, characterId: {}", character.getId());

        // 查询分类名称
        String categoryName = null;
        if (character.getCategoryId() != null) {
            CharacterCategory category = categoryMapper.selectById(character.getCategoryId());
            if (category != null) {
                categoryName = category.getName();
            }
        }

        return new CharacterVO(
                character.getId(),
                character.getCategoryId(),
                categoryName,
                character.getName(),
                character.getDescription(),
                character.getThumbnailUrl(),
                0, // 新创建的角色引用次数为0
                character.getCreatedAt(),
                character.getUpdatedAt()
        );
    }

    /**
     * 更新角色
     *
     * @param userId 当前用户ID
     * @param characterId 角色ID
     * @param request 更新角色请求
     */
    @Override
    public void updateCharacter(Long userId, Long characterId, UpdateCharacterRequest request) {
        log.info("更新角色, userId: {}, characterId: {}", userId, characterId);

        // 查询角色是否存在且未删除
        CharacterLibrary character = characterLibraryMapper.selectById(characterId);
        if (character == null || character.getDeletedAt() != null) {
            throw new BusinessException(ResultCode.CHARACTER_NOT_FOUND);
        }

        // 验证是否属于当前用户
        if (!character.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ACCESS_DENIED);
        }

        // 如果指定了新分类,验证分类是否存在且属于当前用户
        if (request.categoryId() != null) {
            CharacterCategory category = categoryMapper.selectById(request.categoryId());
            if (category == null) {
                throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "角色分类不存在");
            }
            if (!category.getUserId().equals(userId)) {
                throw new BusinessException(ResultCode.ACCESS_DENIED);
            }
        }

        // 更新字段(所有字段可选)
        if (request.name() != null) {
            character.setName(request.name());
        }
        if (request.categoryId() != null) {
            character.setCategoryId(request.categoryId());
        }
        if (request.description() != null) {
            character.setDescription(request.description());
        }
        // 更新缩略图URL（可以设置为null来清除）
        if (request.thumbnailUrl() != null) {
            character.setThumbnailUrl(request.thumbnailUrl());
        }

        // 保存到数据库
        characterLibraryMapper.updateById(character);

        log.info("角色更新成功, characterId: {}", characterId);
    }

    /**
     * 删除角色(软删除)
     *
     * @param userId 当前用户ID
     * @param characterId 角色ID
     */
    @Override
    public void deleteCharacter(Long userId, Long characterId) {
        log.info("删除角色, userId: {}, characterId: {}", userId, characterId);

        // 查询角色是否存在且未删除
        CharacterLibrary character = characterLibraryMapper.selectById(characterId);
        if (character == null || character.getDeletedAt() != null) {
            throw new BusinessException(ResultCode.CHARACTER_NOT_FOUND);
        }

        // 验证是否属于当前用户
        if (!character.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ACCESS_DENIED);
        }

        // 检查是否被项目引用
        LambdaQueryWrapper<ProjectCharacter> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(ProjectCharacter::getLibraryCharacterId, characterId);
        List<ProjectCharacter> projectCharacters = projectCharacterMapper.selectList(checkWrapper);
        
        if (!projectCharacters.isEmpty()) {
            // 检查这些项目角色是否被分镜绑定
            List<Long> projectCharacterIds = projectCharacters.stream()
                    .map(ProjectCharacter::getId)
                    .collect(Collectors.toList());
            
            LambdaQueryWrapper<ShotBinding> bindingWrapper = new LambdaQueryWrapper<>();
            bindingWrapper.eq(ShotBinding::getBindType, "PCHAR")
                    .in(ShotBinding::getBindId, projectCharacterIds);
            long bindingCount = shotBindingMapper.selectCount(bindingWrapper);
            
            if (bindingCount > 0) {
                log.warn("角色被分镜绑定,无法删除, characterId: {}, bindingCount: {}", characterId, bindingCount);
                throw new BusinessException(ResultCode.CHARACTER_IN_USE, 
                        "该角色已被" + bindingCount + "个分镜绑定,请先解绑后再删除");
            }
            
            log.info("角色被{}个项目引用,但未被分镜绑定,允许删除, characterId: {}", projectCharacters.size(), characterId);
        }

        // 软删除(设置deleted_at)
        character.setDeletedAt(LocalDateTime.now());
        characterLibraryMapper.updateById(character);

        log.info("角色删除成功, characterId: {}", characterId);
    }

    /**
     * 上传角色缩略图
     *
     * @param userId 当前用户ID
     * @param characterId 角色ID
     * @param file 图片文件
     * @return 上传后的URL
     */
    @Override
    public String uploadThumbnail(Long userId, Long characterId, MultipartFile file) {
        log.info("上传角色缩略图, userId: {}, characterId: {}", userId, characterId);

        // 查询角色是否存在且未删除
        CharacterLibrary character = characterLibraryMapper.selectById(characterId);
        if (character == null || character.getDeletedAt() != null) {
            throw new BusinessException(ResultCode.CHARACTER_NOT_FOUND);
        }

        // 验证是否属于当前用户
        if (!character.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ACCESS_DENIED);
        }

        try {
            // 保存文件到当前配置的存储服务
            String url = storageService.upload(
                    file.getInputStream(),
                    "character_" + characterId + "_" + file.getOriginalFilename(),
                    file.getContentType()
            );

            // 更新角色的缩略图URL
            character.setThumbnailUrl(url);
            characterLibraryMapper.updateById(character);

            log.info("角色缩略图上传成功, characterId: {}, url: {}", characterId, url);
            return url;
        } catch (IOException e) {
            log.error("角色缩略图上传失败", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "图片上传失败");
        }
    }
}
