-- 为项目道具添加缩略图字段，支持 Excel 导入和补图文件夹里的自定义道具图片
ALTER TABLE project_props
ADD COLUMN thumbnail_url VARCHAR(1024) DEFAULT NULL COMMENT '项目道具缩略图URL' AFTER override_description;
