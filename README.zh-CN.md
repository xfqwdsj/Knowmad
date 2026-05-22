<p align="center">
  <a href="README.md">English</a> |
  <a href="README.zh-CN.md">中文</a>
</p>

# Knowmad — 知行

**Knowmad**（知行）是一款面向 Android 平台的场景感知 AI 学伴应用。它将 Android
系统级能力——无障碍服务、GPS、日历同步、快捷设置——与大语言模型深度融合，打造出一款主动式、隐私优先的学习助手，能够感知用户的所处场景、当前行为与学习需求。

> ⚠️ **状态：** 活跃开发中。功能可运行，但尚未在任何应用商店发布，预期会有破坏性变更。

## 截图

<!-- TODO: 添加截图 -->

## 功能特性

### 🧠 AI Agent — 基于 Koog 框架的对话式智能体

- 基于有向图状态机的 Agent，支持工具调用（Tool Calling）
- 流式响应，实时展示推理过程、工具调用结果与文本内容
- 基于 Room 的持久化对话历史
- 多供应商 LLM 支持（远程 API + 端侧模型，热切换）
- 自动生成对话标题和消息摘要

### 👁️ 屏幕感知 — 基于无障碍服务的界面理解

- 通过 `AccessibilityService` 实时提取前台应用界面结构
- 语义化节点树解析，支持缓存、防抖与协程并发控制
- 服务挂起/恢复机制，非活跃时降低电量消耗
- 为 Agent 提供对用户当前数字上下文的感知能力

### 📅 智能日历 — 多源日程融合

- iCalendar 导入/导出，支持自定义属性扩展（学期、课程、授课教师等元数据）
- 通过 Android `CalendarContract` 和 `SyncAdapter` 实现系统日历双向同步
- 基于 Omnical 的重复规则引擎，适配复杂的学期制课程表
- 学期感知时区处理，基于 `kotlinx-datetime`

### 📱 系统集成 — 深度 Android 平台能力利用

- **快捷设置磁贴** — 一键启动画中画 AI 对话
- **Glance 桌面小部件** — 主页展示当日课程概览
- **富通知** — 课程进度追踪（HyperIsland 自定义 UI）、快速回复、建议卡片
- **日历意图过滤器** — 可直接从文件管理器或浏览器导入 `.ics` 文件

### 📦 端侧 AI — 隐私优先的边缘推理

- ExecuTorch 运行时，本地运行嵌入模型（Qwen3）
- 通过 ModelScope 下载模型，支持增量下载与断点续传
- 集成 HuggingFace Tokenizer，实现本地分词
- 在同一个 Prompt Executor 中无缝切换端侧模型与远程 API

### 🧪 其他亮点

- **MathJax 渲染** — 数学公式展示
- **自定义 Compose UI** — 毛玻璃背景、共享元素过渡动画、拖拽排序、自适应布局
- **Remend** — 基于 WebView 的 UI 渲染扩展支持
- **QuickJS** — 嵌入式 JavaScript 运行时，用于 Agent 工具中的代码执行

## 项目架构

```
app/
├── agent/                   # AI Agent 引擎（Koog GraphAIAgent）
│   ├── ChatAgent.kt         # 核心聊天 Agent 状态机
│   ├── ModelService.kt      # 后台 LLM 服务、任务队列、消息调度
│   ├── Tool.kt              # Agent 工具接口
│   ├── Schedule.kt          # 网页日程子 Agent
│   ├── Browser.kt           # 网页浏览能力
│   ├── Run.kt               # Agent 运行编排
│   └── client/              # LLM 客户端抽象（远程 + 本地）
├── accessibility/           # 系统级屏幕感知
│   └── semantic/
│       ├── SemanticAnalysisService.kt
│       ├── Node.kt
│       └── Rect.kt
├── data/                    # 数据层（Room + DataStore）
│   ├── chat/                # 对话与消息持久化
│   ├── schedule/            # 日历事件、iCalendar、重复规则
│   ├── geo/                 # 地理位置记录
│   ├── file/                # 文件附件
│   ├── llm/                 # LLM 供应商与模型配置
│   └── database/            # 数据库迁移与回调
├── sync/                    # 系统日历同步（SyncAdapter）
├── notification/            # 富通知、课程进度、建议
├── tile/                    # 画中画快捷磁贴
├── widget/                  # Glance 桌面小部件
├── ui/                      # Jetpack Compose UI 层
│   ├── component/           # 可复用组件
│   ├── page/                # 功能页面（聊天、日历、设置等）
│   ├── viewmodel/           # ViewModel
│   ├── theme/               # Material 3 主题
│   └── util/                # UI 工具类
├── util/                    # 通用工具（加密、序列化等）
└── modelscope/              # ModelScope API 集成
```

### 关键依赖

| 库                            | 用途                 |
|------------------------------|--------------------|
| Koog Agents                  | AI Agent 框架        |
| Jetpack Compose + Material 3 | 声明式 UI             |
| Room                         | 本地持久化              |
| biweekly                     | iCalendar 读写       |
| ExecuTorch                   | 端侧 ML 推理           |
| DJL + HuggingFace Tokenizers | 本地分词               |
| Ktor                         | HTTP 客户端           |
| MathJax                      | LaTeX 公式渲染         |
| HyperIsland Kit              | 富通知 UI             |
| Glance                       | 桌面小部件              |
| QuickJS                      | 嵌入式 JavaScript 运行时 |

## 构建

### 前置要求

- Android Studio（建议使用最新稳定版）
- JDK 17+
- Android SDK 35+

### 构建步骤

```bash
git clone https://github.com/ltfan/Knowmad.git
cd Knowmad
./gradlew :app:assembleDebug
```

构建系统会自动下载 MathJax 资源、Remend 运行时和 DJL 原生库。

### Release 构建

创建 `app/key.jks` 和 `app/key.properties` 签名配置文件：

```properties
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

然后运行：

```bash
./gradlew :app:assembleRelease
```

## 权限说明

Knowmad 使用以下 Android 权限：

| 权限                                           | 用途             |
|----------------------------------------------|----------------|
| `POST_NOTIFICATIONS`                         | 课程进度与建议通知      |
| `READ_CALENDAR` / `WRITE_CALENDAR`           | 学业日程双向同步       |
| `READ_SYNC_SETTINGS` / `WRITE_SYNC_SETTINGS` | 日历同步适配器管理      |
| `SCHEDULE_EXACT_ALARM`                       | 定时提醒与课程闹钟      |
| `RECEIVE_BOOT_COMPLETED`                     | 重启后恢复通知        |
| `FOREGROUND_SERVICE*`                        | 后台 AI Agent 运行 |
| 无障碍服务                                        | 屏幕内容感知（数据不离设备） |

## 许可证

Copyright (C) 2025-2026 LTFan（即 [xfqwdsj](https://github.com/xfqwdsj)）

本程序是自由软件：你可以再分发和/或修改它，前提是遵守由自由软件基金会发布的 GNU
通用公共许可证——许可证第三版，或（按你的选择）任何更新的版本。

详见 [LICENSE.md](LICENSE.md)。
