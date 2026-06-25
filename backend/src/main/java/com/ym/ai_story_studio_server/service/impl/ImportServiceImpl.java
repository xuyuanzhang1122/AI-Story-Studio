package com.ym.ai_story_studio_server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ym.ai_story_studio_server.common.ResultCode;
import com.ym.ai_story_studio_server.dto.importx.ImportSummary;
import com.ym.ai_story_studio_server.entity.*;
import com.ym.ai_story_studio_server.exception.BusinessException;
import com.ym.ai_story_studio_server.mapper.*;
import com.ym.ai_story_studio_server.service.ImportService;
import com.ym.ai_story_studio_server.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFPictureData;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Pattern NUMBERED_ASSET_FILE = Pattern.compile("^\\d{1,4}[_-]+(.+)$");

    private final ProjectMapper projectMapper;
    private final StoryboardShotMapper shotMapper;
    private final ShotBindingMapper bindingMapper;
    private final ProjectCharacterMapper projectCharacterMapper;
    private final ProjectSceneMapper projectSceneMapper;
    private final ProjectPropMapper projectPropMapper;
    private final StorageService storageService;

    private enum AssetKind {
        CHARACTER, SCENE, PROP, UNKNOWN
    }

    private record ImportImage(
            AssetKind kind,
            String name,
            String originalName,
            byte[] bytes,
            String contentType
    ) {
    }

    private record ImageImportResult(int matched, int uploaded) {
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportSummary importStoryboardExcel(Long userId, Long projectId, MultipartFile file, List<MultipartFile> assetFiles) {
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

            List<ImportImage> importImages = new ArrayList<>();
            importImages.addAll(extractWorkbookImages(wb));
            importImages.addAll(extractUploadedImages(assetFiles));

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

            ImageImportResult imageResult = applyImages(projectId, charNameToId, sceneNameToId, propNameToId, importImages);

            ImportSummary summary = new ImportSummary(
                    shotsCreated,
                    countCreated(charNameToId, projectId, BIND_PCHAR),
                    countCreated(sceneNameToId, projectId, BIND_PSCENE),
                    countCreated(propNameToId, projectId, BIND_PPROP),
                    bindingsCreated,
                    imageResult.matched(),
                    imageResult.uploaded()
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportSummary importAssetImages(Long userId, Long projectId, List<MultipartFile> assetFiles) {
        validateProjectOwnership(userId, projectId);
        List<ImportImage> importImages = extractUploadedImages(assetFiles);
        if (importImages.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请选择至少一张图片");
        }

        Map<String, Long> charNameToId = loadProjectCharacterMap(projectId);
        Map<String, Long> sceneNameToId = loadProjectSceneMap(projectId);
        Map<String, Long> propNameToId = loadProjectPropMap(projectId);
        ImageImportResult imageResult = applyImages(projectId, charNameToId, sceneNameToId, propNameToId, importImages);

        ImportSummary summary = new ImportSummary(
                0,
                0,
                0,
                0,
                0,
                imageResult.matched(),
                imageResult.uploaded()
        );
        log.info("资源图片补充导入完成: projectId={}, summary={}", projectId, summary);
        return summary;
    }

    // ============== 图片提取与匹配 ==============

    private List<ImportImage> extractWorkbookImages(XSSFWorkbook wb) {
        List<ImportImage> images = new ArrayList<>();
        images.addAll(extractSheetImages(wb.getSheet(SHEET_CHARACTERS), AssetKind.CHARACTER));
        images.addAll(extractSheetImages(wb.getSheet(SHEET_SCENES), AssetKind.SCENE));
        images.addAll(extractSheetImages(wb.getSheet(SHEET_PROPS), AssetKind.PROP));
        return images;
    }

    private List<ImportImage> extractSheetImages(Sheet sheet, AssetKind kind) {
        List<ImportImage> images = new ArrayList<>();
        if (!(sheet instanceof XSSFSheet xssfSheet)) {
            return images;
        }

        XSSFDrawing drawing = xssfSheet.getDrawingPatriarch();
        if (drawing == null) {
            return images;
        }

        DataFormatter fmt = new DataFormatter();
        for (XSSFShape shape : drawing.getShapes()) {
            if (!(shape instanceof XSSFPicture picture)) {
                continue;
            }
            XSSFClientAnchor anchor = picture.getClientAnchor();
            if (anchor == null) {
                continue;
            }
            Row row = sheet.getRow(anchor.getRow1());
            if (row == null) {
                continue;
            }
            String name = cellText(row.getCell(0), fmt);
            if (name.isBlank()) {
                continue;
            }
            XSSFPictureData pictureData = picture.getPictureData();
            String ext = pictureData.suggestFileExtension();
            String fileName = sanitizeUploadName(name) + "." + (StringUtils.hasText(ext) ? ext : "png");
            images.add(new ImportImage(kind, name, fileName, pictureData.getData(), detectImageContentType(fileName, pictureData.getMimeType())));
        }
        return images;
    }

    private List<ImportImage> extractUploadedImages(List<MultipartFile> assetFiles) {
        if (assetFiles == null || assetFiles.isEmpty()) {
            return Collections.emptyList();
        }

        List<ImportImage> images = new ArrayList<>();
        for (MultipartFile file : assetFiles) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("asset.png");
            if (!looksLikeImage(originalName, file.getContentType())) {
                continue;
            }
            String resourceName = extractResourceName(originalName);
            if (!StringUtils.hasText(resourceName)) {
                continue;
            }
            try {
                images.add(new ImportImage(
                        inferKindFromPath(originalName),
                        resourceName,
                        originalName,
                        file.getBytes(),
                        detectImageContentType(originalName, file.getContentType())
                ));
            } catch (Exception e) {
                throw new BusinessException(ResultCode.FILE_OPERATION_ERROR,
                        "读取图片失败：" + originalName + "，" + e.getMessage());
            }
        }
        return images;
    }

    private ImageImportResult applyImages(
            Long projectId,
            Map<String, Long> charNameToId,
            Map<String, Long> sceneNameToId,
            Map<String, Long> propNameToId,
            List<ImportImage> images) {
        if (images == null || images.isEmpty()) {
            return new ImageImportResult(0, 0);
        }

        Map<String, Long> normalizedChars = normalizeIdMap(charNameToId);
        Map<String, Long> normalizedScenes = normalizeIdMap(sceneNameToId);
        Map<String, Long> normalizedProps = normalizeIdMap(propNameToId);

        int matched = 0;
        int uploaded = 0;
        for (ImportImage image : images) {
            String key = normalizeName(image.name());
            if (!StringUtils.hasText(key)) {
                continue;
            }

            String uploadedUrl = null;
            boolean uploadedThisImage = false;

            if ((image.kind() == AssetKind.CHARACTER || image.kind() == AssetKind.UNKNOWN)
                    && normalizedChars.containsKey(key)
                    && needsCharacterThumbnail(normalizedChars.get(key))) {
                uploadedUrl = uploadImageIfNeeded(image, uploadedUrl);
                uploadedThisImage = true;
                if (updateCharacterThumbnailIfMissing(normalizedChars.get(key), uploadedUrl)) {
                    matched++;
                }
            }
            if ((image.kind() == AssetKind.SCENE || image.kind() == AssetKind.UNKNOWN)
                    && normalizedScenes.containsKey(key)
                    && needsSceneThumbnail(normalizedScenes.get(key))) {
                uploadedUrl = uploadImageIfNeeded(image, uploadedUrl);
                uploadedThisImage = true;
                if (updateSceneThumbnailIfMissing(normalizedScenes.get(key), uploadedUrl)) {
                    matched++;
                }
            }
            if ((image.kind() == AssetKind.PROP || image.kind() == AssetKind.UNKNOWN)
                    && normalizedProps.containsKey(key)
                    && needsPropThumbnail(normalizedProps.get(key))) {
                uploadedUrl = uploadImageIfNeeded(image, uploadedUrl);
                uploadedThisImage = true;
                if (updatePropThumbnailIfMissing(normalizedProps.get(key), uploadedUrl)) {
                    matched++;
                }
            }
            if (uploadedThisImage) {
                uploaded++;
            }
        }

        log.info("资源图片匹配完成: projectId={}, imageCandidates={}, matched={}, uploaded={}",
                projectId, images.size(), matched, uploaded);
        return new ImageImportResult(matched, uploaded);
    }

    private String uploadImageIfNeeded(ImportImage image, String uploadedUrl) {
        if (uploadedUrl != null) {
            return uploadedUrl;
        }
        try (ByteArrayInputStream in = new ByteArrayInputStream(image.bytes())) {
            return storageService.upload(in, image.originalName(), image.contentType());
        } catch (Exception e) {
            throw new BusinessException(ResultCode.FILE_OPERATION_ERROR,
                    "上传图片失败：" + image.originalName() + "，" + e.getMessage());
        }
    }

    private boolean needsCharacterThumbnail(Long id) {
        ProjectCharacter c = projectCharacterMapper.selectById(id);
        return c != null && !StringUtils.hasText(c.getThumbnailUrl());
    }

    private boolean needsSceneThumbnail(Long id) {
        ProjectScene s = projectSceneMapper.selectById(id);
        return s != null && !StringUtils.hasText(s.getThumbnailUrl());
    }

    private boolean needsPropThumbnail(Long id) {
        ProjectProp p = projectPropMapper.selectById(id);
        return p != null && !StringUtils.hasText(p.getThumbnailUrl());
    }

    private boolean updateCharacterThumbnailIfMissing(Long id, String thumbnailUrl) {
        ProjectCharacter c = projectCharacterMapper.selectById(id);
        if (c == null || StringUtils.hasText(c.getThumbnailUrl())) {
            return false;
        }
        c.setThumbnailUrl(thumbnailUrl);
        projectCharacterMapper.updateById(c);
        return true;
    }

    private boolean updateSceneThumbnailIfMissing(Long id, String thumbnailUrl) {
        ProjectScene s = projectSceneMapper.selectById(id);
        if (s == null || StringUtils.hasText(s.getThumbnailUrl())) {
            return false;
        }
        s.setThumbnailUrl(thumbnailUrl);
        projectSceneMapper.updateById(s);
        return true;
    }

    private boolean updatePropThumbnailIfMissing(Long id, String thumbnailUrl) {
        ProjectProp p = projectPropMapper.selectById(id);
        if (p == null || StringUtils.hasText(p.getThumbnailUrl())) {
            return false;
        }
        p.setThumbnailUrl(thumbnailUrl);
        projectPropMapper.updateById(p);
        return true;
    }

    private Map<String, Long> loadProjectCharacterMap(Long projectId) {
        LambdaQueryWrapper<ProjectCharacter> q = new LambdaQueryWrapper<>();
        q.eq(ProjectCharacter::getProjectId, projectId);
        Map<String, Long> result = new LinkedHashMap<>();
        for (ProjectCharacter c : projectCharacterMapper.selectList(q)) {
            if (StringUtils.hasText(c.getDisplayName())) {
                result.put(c.getDisplayName(), c.getId());
            }
        }
        return result;
    }

    private Map<String, Long> loadProjectSceneMap(Long projectId) {
        LambdaQueryWrapper<ProjectScene> q = new LambdaQueryWrapper<>();
        q.eq(ProjectScene::getProjectId, projectId);
        Map<String, Long> result = new LinkedHashMap<>();
        for (ProjectScene s : projectSceneMapper.selectList(q)) {
            if (StringUtils.hasText(s.getDisplayName())) {
                result.put(s.getDisplayName(), s.getId());
            }
        }
        return result;
    }

    private Map<String, Long> loadProjectPropMap(Long projectId) {
        LambdaQueryWrapper<ProjectProp> q = new LambdaQueryWrapper<>();
        q.eq(ProjectProp::getProjectId, projectId);
        Map<String, Long> result = new LinkedHashMap<>();
        for (ProjectProp p : projectPropMapper.selectList(q)) {
            if (StringUtils.hasText(p.getDisplayName())) {
                result.put(p.getDisplayName(), p.getId());
            }
        }
        return result;
    }

    private Map<String, Long> normalizeIdMap(Map<String, Long> source) {
        Map<String, Long> normalized = new LinkedHashMap<>();
        source.forEach((name, id) -> {
            String key = normalizeName(name);
            if (StringUtils.hasText(key)) {
                normalized.putIfAbsent(key, id);
            }
        });
        return normalized;
    }

    private String extractResourceName(String originalName) {
        String normalizedPath = originalName.replace('\\', '/');
        String baseName = normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1);
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) {
            baseName = baseName.substring(0, dot);
        }

        Matcher matcher = NUMBERED_ASSET_FILE.matcher(baseName);
        if (matcher.matches()) {
            baseName = matcher.group(1);
        }

        int gptIndex = baseName.toLowerCase(Locale.ROOT).indexOf("_gpt-image");
        if (gptIndex > 0) {
            baseName = baseName.substring(0, gptIndex);
        } else if (baseName.contains("_")) {
            baseName = baseName.split("_", 2)[0];
        }
        return baseName.trim();
    }

    private AssetKind inferKindFromPath(String originalName) {
        String normalized = originalName.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.contains("角色") || normalized.contains("人物")) {
            return AssetKind.CHARACTER;
        }
        if (normalized.contains("场景")) {
            return AssetKind.SCENE;
        }
        if (normalized.contains("道具")) {
            return AssetKind.PROP;
        }
        return AssetKind.UNKNOWN;
    }

    private boolean looksLikeImage(String fileName, String contentType) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return true;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".bmp");
    }

    private String detectImageContentType(String fileName, String providedContentType) {
        if (providedContentType != null
                && providedContentType.toLowerCase(Locale.ROOT).startsWith("image/")
                && !providedContentType.equalsIgnoreCase("image/x-png")) {
            return providedContentType;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        return "image/png";
    }

    private String sanitizeUploadName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }

    private String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            return "";
        }
        return name.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
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
