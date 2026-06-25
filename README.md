<div align="center">

# 🎬 AI Story Studio

**面向个人创作者的 AI 短剧 / 动画分镜自动化工作台**

参考 [MochiAni](https://mochiani.com) 的工作流，把"剧本 → 角色 / 场景 / 道具 → 分镜 → 图像 / 视频"全流程搬进浏览器，
支持从 Excel 一键导入剧本，按行解析镜头并自动关联资源。

[![Java](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.4-4FC08D?logo=vue.js&logoColor=white)](https://vuejs.org/)
[![Vite](https://img.shields.io/badge/Vite-6.4-646CFF?logo=vite&logoColor=white)](https://vitejs.dev/)
[![Docker](https://img.shields.io/badge/Docker-ready-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)

</div>

---

## ✨ 核心特性

| 模块 | 能力 |
|---|---|
| 🎯 **项目管理** | 多项目 / 文件夹归类、画幅 / 风格 / 时代配置、模型组合 |
| 📜 **剧本导入** | **MochiAni 风格 .xlsx 一键导入**：自动解析「分镜 / 出场人物 / 场景 / 道具」四个 Sheet，按行追加镜头并建立绑定 |
| 🖼️ **图片导入** | Excel 内嵌图片自动抽取 + 独立 `assetFiles` 上传包，按文件名匹配角色 / 场景 / 道具缩略图 |
| 🎨 **分镜编辑器** | 富文本染色（角色 / 场景 / 道具 / 声纹特征 / 运镜标签按类型上色）、行高自适应、悬浮放大编辑 |
| 👤 **资源库** | 角色 / 场景 / 道具的项目库 ↔ 全局库引用，支持项目内覆盖、缩略图、悬停大图预览 |
| 🤖 **AI 生成** | 文本拆分、角色 / 场景 / 道具 / 分镜图 / 视频生成；批量任务通过 RabbitMQ 异步消费 |
| 💾 **存储** | 默认本地磁盘（`backend/uploads/`），可切换阿里云 OSS |
| 🔐 **认证** | JWT；自部署版本跳过短信验证码，任意手机号 + 任意 6 位数字即可登录 |

---

## 🛠️ 技术栈

**后端**
- Spring Boot 3.2.5 / Java 17
- MyBatis-Plus 3.5.12
- MySQL 8.0（Flyway 自动迁移）
- Redis · RabbitMQ
- Apache POI（Excel 解析）
- JWT
- 阿里云 OSS / 本地磁盘存储（可切换）

**前端**
- Vue 3 + TypeScript
- Vite 6
- Pinia · Vue Router
- TailwindCSS · Naive UI
- Axios

**基础设施**
- Docker Compose（一键拉起 MySQL / Redis / RabbitMQ / 后端 / 前端）
- Nginx（静态资源 + 反向代理）

---

## 🚀 快速开始

### 方式一：Docker 一键启动（推荐）

```bash
git clone https://github.com/xuyuanzhang1122/-AI.git
cd -AI
docker-compose up -d
```

启动后访问：
- 前端：http://localhost:8081
- 后端 API：http://localhost:8080
- RabbitMQ 管理台：http://localhost:15672（admin / admin123）

### 方式二：本地开发模式

#### 1. 启动依赖容器
```bash
docker-compose up -d mysql redis rabbitmq
```

#### 2. 启动后端
```powershell
# Windows PowerShell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;<your-maven-path>\bin;$env:PATH"
cd backend
mvn spring-boot:run "-Dspring-boot.run.jvmArguments=-Dspring.datasource.url=jdbc:mysql://localhost:3307/ai_story_studio?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true -Dspring.datasource.password=1234"
```

```bash
# macOS / Linux
cd backend
mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Dspring.datasource.url=jdbc:mysql://localhost:3307/ai_story_studio?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true -Dspring.datasource.password=1234"
```

#### 3. 启动前端
```bash
cd frontend/user-web
pnpm install
pnpm dev
```

> 默认端口被占用时 Vite 会自动尝试下一个（如 3001）。

### 登录

打开前端地址，手机号填任意 11 位数字（如 `13800000001`），验证码填任意 6 位数字（如 `888888`），点登录即可——首次登录会自动注册账号。

---

## 📜 Excel 剧本导入说明

支持 [MochiAni](https://mochiani.com) 导出的 `.xlsx` 文件，结构如下：

| Sheet 名 | 列 | 说明 |
|---|---|---|
| **分镜** | 序号 / 剧本 / 出场人物 / 场景 / 道具 / 分镜图 | 每行 = 一个镜头 |
| **出场人物** | 名称 / 描述词 / 图片 | 全量角色清单 + AI 生图 Prompt |
| **场景** | 名称 / 描述词 / 图片 | 全量场景清单 |
| **道具** | 名称 / 描述词 / 图片 | 全量道具清单 |

### 使用流程

1. 进入任一项目的分镜编辑器
2. 工具栏点 **「导入分镜表格」**，选 `.xlsx` 文件
3. 后端自动：
   - 按名称在项目内 upsert 角色 / 场景 / 道具（同名复用，已存在仅补全缺失描述）
   - 按行追加分镜，`shot_no` 从当前最大值递增
   - 解析"出场人物 / 场景 / 道具"列建立 `shot_bindings`
   - 抽取 Excel 内嵌图片 + 上传的 `assetFiles`，按文件名匹配资源缩略图
4. 完成后弹窗汇总：新增分镜数、涉及角色 / 场景 / 道具数、图片匹配数

---

## 📁 目录结构

```
.
├── backend/                    # Spring Boot 后端
│   ├── src/main/java/com/ym/ai_story_studio_server/
│   │   ├── controller/         # REST 接口
│   │   ├── service/            # 业务服务
│   │   │   └── impl/
│   │   │       ├── ImportServiceImpl.java       # ⭐ Excel 导入核心
│   │   │       ├── LocalStorageServiceImpl.java # ⭐ 本地磁盘存储
│   │   │       └── ...
│   │   ├── mapper/             # MyBatis-Plus 映射
│   │   ├── entity/             # 数据库实体
│   │   ├── dto/                # 请求 / 响应模型
│   │   ├── config/             # Web / JWT / Storage / RabbitMQ 配置
│   │   └── mq/                 # RabbitMQ 生产者 / 消费者
│   └── src/main/resources/
│       ├── application.yml
│       └── db/migration/       # Flyway 迁移脚本（V1 - V16）
│
├── frontend/user-web/          # Vue 3 前端
│   └── src/
│       ├── api/                # API 封装
│       │   ├── import.ts       # ⭐ 导入 API
│       │   └── ...
│       ├── components/editor/  # 编辑器组件
│       │   ├── StoryboardTable.vue
│       │   ├── StoryboardRow.vue
│       │   ├── ScriptExpandModal.vue   # ⭐ 放大编辑弹窗
│       │   ├── AssetHoverPreview.vue   # ⭐ 资源悬浮预览
│       │   └── panels/
│       ├── stores/             # Pinia 状态
│       ├── utils/
│       │   └── scriptHighlight.ts      # ⭐ 剧本富文本染色
│       └── views/              # 页面
│
├── docker-compose.yml          # MySQL / Redis / RabbitMQ / 后端 / 前端
├── Dockerfile.backend
├── Dockerfile.frontend
├── nginx.conf
└── README.md
```

---

## 🔌 关键接口

| Method | Path | 说明 |
|---|---|---|
| `POST` | `/api/auth/phone/login` | 手机号登录（自部署版跳过验证码） |
| `POST` | `/api/projects/{projectId}/import/excel` | 上传 .xlsx 导入分镜表 |
| `POST` | `/api/projects/{projectId}/import/asset-images` | 补传角色/场景/道具图片 |
| `GET` | `/api/projects/{projectId}/shots` | 分镜列表 |
| `POST` | `/api/projects/{projectId}/shots` | 新增分镜 |
| `POST` | `/api/projects/{projectId}/shots/parse-script` | AI 解析剧本批量建分镜 |
| `POST` | `/api/projects/{projectId}/export` | 项目素材打包导出 |

完整接口看 `backend/src/main/java/.../controller/`。

---

## 🧩 二次开发

### 切换存储后端

`application.yml` → `storage.type`：
- `local`：写到 `backend/uploads/`，通过 `WebConfig` 暴露静态资源
- `oss`：写到阿里云 OSS，需要在 `storage.oss.*` 填 AccessKey / Bucket

### 数据库迁移

放进 `backend/src/main/resources/db/migration/`，命名 `V{N}__description.sql`，重启后端 Flyway 会自动执行。

### 跳过 / 恢复短信验证码

`AuthServiceImpl.java` 的 `sendVerificationCode` / `phoneLogin`——当前是 dev 跳过模式，要恢复商业版逻辑请还原成调用 `smsService` + 比对 Redis cache。

---

## 🪪 License

本仓库 fork 自 [womendeshi/-AI](https://github.com/womendeshi/-AI)，仅用于个人学习 / 自部署，不用于商业。

