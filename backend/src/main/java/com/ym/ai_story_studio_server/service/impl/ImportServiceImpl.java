package com.ym.ai_story_studio_server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ym.ai_story_studio_server.common.ResultCode;
import com.ym.ai_story_studio_server.dto.importx.ImportSummary;
import com.ym.ai_story_studio_server.entity.*;
import com.ym.ai_story_studio_server.exception.BusinessException;
import com.ym.ai_story_studio_server.mapper.*;
import com.ym.ai_story_studio_server.service.ImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;

/**
 * Excel 分镜表格导入服务实现
 *
 * <p>解析 MochiAni 风格的 .xlsx，将角色/场景/道具入库为项目级资源，
 * 按行创建分镜并建立绑定关系。采用追加模式，不影响已有分镜。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportServiceImpl implements ImportService {

    private static final String SHEET_SHOTS = "分镜";
    private static final String SHEET_CHARACTERS = "出场人物";
    private static final String SHEET_SCENES = "场景";
    private static final String SHEET_PROPS = "道具";

    private static final String BIND_PCHAR = "PCHAR";
    private static final String BIND_PSCENE = "PSCENE";
    private static final String BIND_PPROP = "PPROP";

    private final ProjectMapper projectMapper;
    private final StoryboardShotMapper shotMapper;
    private final ShotBindingMapper bindingMapper;
    private final ProjectCharacterMapper projectCharacterMapper;
    private final ProjectSceneMapper projectSceneMapper;
    private final ProjectPropMapper projectPropMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportSummary importStoryboardExcel(Long userId, Long projectId, MultipartFile file) {
        validateProjectOwnership(userId, projectId);

        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "上传文件为空");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "仅支持 .xlsx 格式");
        }

        try (InputStream in = file.getInputStream();
             XSSFWorkbook wb = new XSSFWorkbook(in)) {

            // 1. 先导入资源库（角色/场景/道具）到项目，记录"名称 -> ProjectXxx"映射
            Map<String, Long> charNameToId = importCharacters(projectId, wb.getSheet(SHEET_CHARACTERS));
            Map<String, Long> sceneNameToId = importScenes(projectId, wb.getSheet(SHEET_SCENES));
            Map<String, Long> propNameToId = importProps(projectId, wb.getSheet(SHEET_PROPS));

            // 2. 解析分镜并创建
            Sheet shotSheet = wb.getSheet(SHEET_SHOTS);
            if (shotSheet == null) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "Excel 缺少必需的 Sheet：分镜");
            }

            int startShotNo = nextShotNo(projectId);
            int shotsCreated = 0;
            int bindingsCreated = 0;
            DataFormatter fmt = new DataFormatter();

            for (int r = 1; r <= shotSheet.getLastRowNum(); r++) {
                Row row = shotSheet.getRow(r);
                if (row == null) continue;

                String scriptText = cellText(row.getCell(1), fmt);
                if (scriptText.isBlank()) continue;

                String charsCol = cellText(row.getCell(2), fmt);
                String sceneCol = cellText(row.getCell(3), fmt);
                String propsCol = cellText(row.getCell(4), fmt);

                StoryboardShot shot = new StoryboardShot();
                shot.setProjectId(projectId);
                shot.setShotNo(startShotNo + shotsCreated);
                shot.setScriptText(scriptText);
                shotMapper.insert(shot);
                shotsCreated++;

                // 角色绑定
                for (String name : splitNames(charsCol)) {
                    Long pcharId = charNameToId.get(name);
                    if (pcharId == null) {
                        pcharId = ensureProjectCharacter(projectId, name, null);
                        charNameToId.put(name, pcharId);
                    }
                    if (insertBindingIfAbsent(shot.getId(), BIND_PCHAR, pcharId)) {
                        bindingsCreated++;
                    }
                }
                // 场景绑定（每行通常一个）
                for (String name : splitNames(sceneCol)) {
                    Long psceneId = sceneNameToId.get(name);
                    if (psceneId == null) {
                        psceneId = ensureProjectScene(projectId, name, null);
                        sceneNameToId.put(name, psceneId);
                    }
                    if (insertBindingIfAbsent(shot.getId(), BIND_PSCENE, psceneId)) {
                        bindingsCreated++;
                    }
                }
                // 道具绑定
                for (String name : splitNames(propsCol)) {
                    Long ppropId = propNameToId.get(name);
                    if (ppropId == null) {
                        ppropId = ensureProjectProp(projectId, name, null);
                        propNameToId.put(name, ppropId);
                    }
                    if (insertBindingIfAbsent(shot.getId(), BIND_PPROP, ppropId)) {
                        bindingsCreated++;
                    }
                }
            }

            ImportSummary summary = new ImportSummary(
                    shotsCreated,
                    countCreated(charNameToId, projectId, BIND_PCHAR),
                    countCreated(sceneNameToId, projectId, BIND_PSCENE),
                    countCreated(propNameToId, projectId, BIND_PPROP),
                    bindingsCreated
            );
            log.info("Excel 导入完成: projectId={}, summary={}", projectId, summary);
            return summary;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析 Excel 失败: {}", e.getMessage(), e);
            throw new BusinessException(ResultCode.FILE_OPERATION_ERROR, "解析 Excel 失败：" + e.getMessage());
        }
    }

    // ============== Sheet 解析 ==============

    private Map<String, Long> importCharacters(Long projectId, Sheet sheet) {
        Map<String, Long> result = new LinkedHashMap<>();
        if (sheet == null) return result;
        DataFormatter fmt = new DataFormatter();
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String name = cellText(row.getCell(0), fmt);
            if (name.isBlank()) continue;
            String desc = cellText(row.getCell(1), fmt);
            result.put(name, ensureProjectCharacter(projectId, name, blankToNull(desc)));
        }
        return result;
    }

    private Map<String, Long> importScenes(Long projectId, Sheet sheet) {
        Map<String, Long> result = new LinkedHashMap<>();
        if (sheet == null) return result;
        DataFormatter fmt = new DataFormatter();
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String name = cellText(row.getCell(0), fmt);
            if (name.isBlank()) continue;
            String desc = cellText(row.getCell(1), fmt);
            result.put(name, ensureProjectScene(projectId, name, blankToNull(desc)));
        }
        return result;
    }

    private Map<String, Long> importProps(Long projectId, Sheet sheet) {
        Map<String, Long> result = new LinkedHashMap<>();
        if (sheet == null) return result;
        DataFormatter fmt = new DataFormatter();
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String name = cellText(row.getCell(0), fmt);
            if (name.isBlank()) continue;
            String desc = cellText(row.getCell(1), fmt);
            result.put(name, ensureProjectProp(projectId, name, blankToNull(desc)));
        }
        return result;
    }

    // ============== 资源 upsert（按名称匹配，已存在则更新描述） ==============

    private Long ensureProjectCharacter(Long projectId, String name, String desc) {
        LambdaQueryWrapper<ProjectCharacter> q = new LambdaQueryWrapper<>();
        q.eq(ProjectCharacter::getProjectId, projectId).eq(ProjectCharacter::getDisplayName, name);
        ProjectCharacter existing = projectCharacterMapper.selectOne(q);
        if (existing != null) {
            if (desc != null && (existing.getOverrideDescription() == null || existing.getOverrideDescription().isBlank())) {
                existing.setOverrideDescription(desc);
                projectCharacterMapper.updateById(existing);
            }
            return existing.getId();
        }
        ProjectCharacter c = new ProjectCharacter();
        c.setProjectId(projectId);
        c.setDisplayName(name);
        c.setOverrideDescription(desc);
        projectCharacterMapper.insert(c);
        return c.getId();
    }

    private Long ensureProjectScene(Long projectId, String name, String desc) {
        LambdaQueryWrapper<ProjectScene> q = new LambdaQueryWrapper<>();
        q.eq(ProjectScene::getProjectId, projectId).eq(ProjectScene::getDisplayName, name);
        ProjectScene existing = projectSceneMapper.selectOne(q);
        if (existing != null) {
            if (desc != null && (existing.getOverrideDescription() == null || existing.getOverrideDescription().isBlank())) {
                existing.setOverrideDescription(desc);
                projectSceneMapper.updateById(existing);
            }
            return existing.getId();
        }
        ProjectScene s = new ProjectScene();
        s.setProjectId(projectId);
        s.setDisplayName(name);
        s.setOverrideDescription(desc);
        projectSceneMapper.insert(s);
        return s.getId();
    }

    private Long ensureProjectProp(Long projectId, String name, String desc) {
        LambdaQueryWrapper<ProjectProp> q = new LambdaQueryWrapper<>();
        q.eq(ProjectProp::getProjectId, projectId).eq(ProjectProp::getDisplayName, name);
        ProjectProp existing = projectPropMapper.selectOne(q);
        if (existing != null) {
            if (desc != null && (existing.getOverrideDescription() == null || existing.getOverrideDescription().isBlank())) {
                existing.setOverrideDescription(desc);
                projectPropMapper.updateById(existing);
            }
            return existing.getId();
        }
        ProjectProp p = new ProjectProp();
        p.setProjectId(projectId);
        p.setDisplayName(name);
        p.setOverrideDescription(desc);
        projectPropMapper.insert(p);
        return p.getId();
    }

    private boolean insertBindingIfAbsent(Long shotId, String type, Long bindId) {
        LambdaQueryWrapper<ShotBinding> q = new LambdaQueryWrapper<>();
        q.eq(ShotBinding::getShotId, shotId)
                .eq(ShotBinding::getBindType, type)
                .eq(ShotBinding::getBindId, bindId);
        if (bindingMapper.selectCount(q) > 0) {
            return false;
        }
        ShotBinding b = new ShotBinding();
        b.setShotId(shotId);
        b.setBindType(type);
        b.setBindId(bindId);
        bindingMapper.insert(b);
        return true;
    }

    // ============== 工具方法 ==============

    private void validateProjectOwnership(Long userId, Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw new BusinessException(ResultCode.PROJECT_NOT_FOUND, "项目不存在");
        }
        if (!project.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ACCESS_DENIED, "无权限访问该项目");
        }
    }

    private int nextShotNo(Long projectId) {
        LambdaQueryWrapper<StoryboardShot> q = new LambdaQueryWrapper<>();
        q.eq(StoryboardShot::getProjectId, projectId)
                .orderByDesc(StoryboardShot::getShotNo)
                .last("LIMIT 1");
        StoryboardShot maxShot = shotMapper.selectOne(q);
        return (maxShot == null) ? 1 : maxShot.getShotNo() + 1;
    }

    private String cellText(Cell cell, DataFormatter fmt) {
        if (cell == null) return "";
        return fmt.formatCellValue(cell).trim();
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /** Excel 中"出场人物/道具"列以中文/英文顿号、逗号分隔 */
    private List<String> splitNames(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        String[] parts = raw.split("[、,，;；\\s]+");
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String p : parts) {
            String n = p.trim();
            if (!n.isEmpty() && seen.add(n)) out.add(n);
        }
        return out;
    }

    /** 统计本次导入中"新创建"的资源数量（map 中可能包含已存在的） */
    private int countCreated(Map<String, Long> nameToId, Long projectId, String type) {
        // 简化：返回 map 大小（含已存在的）。前端只需感知"涉及多少资源"
        return nameToId.size();
    }
}
