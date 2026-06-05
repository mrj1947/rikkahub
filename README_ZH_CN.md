<div align="center">

<img src="docs/icon.png" alt="RikkaHub Icon" width="120" />

# 🌸 RikkaHub — 多模型AI聊天客户端（增强版）

**原生 Android LLM 聊天客户端 | 支持20+AI供应商 | 百度融合定位 | Material You 设计**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-8.0+-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](#-贡献指南)

> 🔀 本仓库基于 [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub) 原版增强，加入百度融合定位、本土化优化等功能。

[🌐 原版官网](https://rikka-ai.com) · [📱 Google Play](https://play.google.com/store/apps/details?id=me.rerere.rikkahub) · [💬 Discord](https://discord.gg/9weBqxe5c4) · [🐧 QQ群](https://qm.qq.com/q/I8MSU0FkOu)

</div>

---

## ✨ 核心功能

| 功能 | 说明 |
|------|------|
| 🤖 **多模型切换** | OpenAI / Claude / Gemini / DeepSeek / 通义千问 / 智谱 / 自定义OpenAI兼容接口 |
| 🎨 **Material You** | 动态取色、暗色模式、预测性返回手势 |
| 🖼️ **多模态输入** | 图片、PDF、Word文档、文本文件 |
| 📝 **富文本渲染** | Markdown、代码高亮、LaTeX公式、表格、Mermaid图表 |
| 🌐 **Web访问** | 内置Web服务器，多平台通过浏览器访问 |
| 🛠️ **MCP协议** | Model Context Protocol，AI工具生态扩展 |
| 🔍 **联网搜索** | Exa / Tavily / 智谱 / Brave / Perplexity / LinkUp |
| 🧠 **记忆功能** | 类ChatGPT长期记忆，跨对话上下文 |
| 🤖 **智能体** | 自定义Agent、SillyTavern角色卡导入 |
| 🌍 **AI翻译** | 多语言即时翻译 |
| 📤 **二维码配置** | 供应商配置导出/导入，扫码即用 |
| 🌿 **消息分支** | 对话任意节点分叉，多条思路并行探索 |
| 🖥️ **Web面板** | PC/手机浏览器直接访问，跨设备使用 |
| 🗣️ **TTS语音** | 文字转语音播放 |
| 🎨 **AI绘画** | 内置图片生成功能 |
| 📊 **使用统计** | 模型调用统计与费用追踪 |
| 🔄 **数据备份** | 本地备份与恢复 |

---

## 🇨🇳 本土化增强（本仓库特色）

在原版基础上，针对中国用户使用场景做了以下增强：

### 📍 百度融合定位引擎
- **双引擎定位**：百度SDK融合定位（GPS+WiFi+基站）优先 + 原生GPS兜底
- **自动逆地理编码**：直接获取省/市/区/街道地址文字
- **GCJ02坐标系**：符合国内地图标准
- **智能降级**：百度AK未配置时自动切换到原生GPS
- **精度对比**：原生GPS精度更优时自动覆盖百度结果
- **占位符集成**：`{location}` 变量自动注入当前位置+地址

### 🔧 构建优化
- 内置 local.properties 模板（自动适配Android SDK路径）
- Web UI 构建脚本适配国内pnpm路径
- Firebase google-services.json 占位配置（构建不报错）

---

## 🏗️ 项目架构

```
rikkahub/
├── app/                          # Android 主模块
│   └── src/main/java/.../rikkahub/
│       ├── RikkaHubApp.kt        # Application入口
│       ├── data/                 # 数据层（AI/API/数据库/仓库）
│       ├── di/                   # Koin依赖注入
│       ├── service/              # 前台服务、Web服务器
│       ├── ui/                   # Compose UI（页面/组件/主题）
│       └── utils/                # 工具类（含百度定位LocationUtils）
├── web/                          # Web服务模块（Ktor）
├── web-ui/                       # Web前端（React + pnpm）
├── ai/                           # AI核心逻辑模块
├── common/                       # 公共模块
├── highlight/                    # 代码高亮模块
├── search/                       # 搜索引擎模块
├── speech/                       # 语音模块
├── material3/                    # Material3动态取色
├── locale-tui/                   # 本地化TUI工具
└── docs/                         # 文档与截图
```

---

## 🛠️ 技术栈

| 层级 | 技术 |
|------|------|
| **语言** | Kotlin 2.0 |
| **UI框架** | Jetpack Compose (Material 3 / Material You) |
| **依赖注入** | Koin |
| **网络** | OkHttp + Kotlinx Serialization |
| **数据库** | Room (SQLite) |
| **偏好存储** | DataStore |
| **图片加载** | Coil |
| **导航** | Navigation Compose |
| **Web服务** | Ktor |
| **前端** | React + Vite + TypeScript |
| **定位（增强）** | 百度地图定位SDK 9.6.8 + Android原生LocationManager |
| **构建** | Gradle (Kotlin DSL) |

---

## 🚀 快速开始

### 环境要求
- **Android Studio** Hedgehog (2023.1.1) 或更高版本
- **JDK 17**
- **Android SDK** API 26+ (Android 8.0)
- **Node.js** + **pnpm**（用于构建Web前端）

### 构建步骤

1. **克隆仓库**
```bash
git clone https://github.com/mrj1947/rikkahub.git
cd rikkahub
git submodule update --init
```

2. **配置SDK路径**
```bash
# 在项目根目录创建 local.properties
echo "sdk.dir=C:/Users/你的用户名/AppData/Local/Android/Sdk" > local.properties
```

3. **配置Firebase（可选）**
放置 google-services.json 到 app/ 目录，或使用占位文件（可正常构建）

4. **配置百度定位AK（可选，增强定位）**
编辑 app/src/main/AndroidManifest.xml，将 YOUR_BAIDU_AK_HERE 替换为你的百度地图AK

5. **构建Web前端**
```bash
cd web-ui && pnpm install && pnpm run build
```

6. **构建APK**
```bash
./gradlew assembleDebug
```

---

## 📱 功能页面一览

| 页面 | 功能 |
|------|------|
| 💬 聊天 | 多轮对话、消息分支、Markdown渲染、代码高亮 |
| 🤖 助手 | AI助手管理、角色卡导入 |
| ⚙️ 设置 | 供应商配置、主题、搜索、MCP、语音、Web服务器 |
| 🔌 扩展 | Skills技能、快捷消息、Prompt管理 |
| 🔍 搜索 | 联网搜索配置 |
| 🖼️ AI绘画 | 图片生成 |
| 📝 翻译 | AI翻译 |
| 📊 统计 | 使用量统计 |
| 💾 备份 | 数据导入导出 |

---

## 📄 许可证

[MIT License](LICENSE) — 基于原版二次开发。

<div align="center">

**如果这个项目对你有帮助，给个 Star 吧！** ⭐

</div>
