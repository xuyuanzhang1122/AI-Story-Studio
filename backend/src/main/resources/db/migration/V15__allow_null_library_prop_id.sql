-- V15: 允许 project_props 表的 library_prop_id 为空
-- 与 V12 对 project_characters / project_scenes 的处理保持一致
-- 场景：Excel 导入 / AI 解析时，先在项目内建立自定义道具，可无对应全局库引用

ALTER TABLE project_props MODIFY COLUMN library_prop_id BIGINT NULL COMMENT '引用的全局道具ID（可为空表示项目内自定义道具）';
