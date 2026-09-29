# Agent Platform

本地智能体 Hub：Java 跑控制面，React 管配置，模型走云厂商。MCP 连接器用 Node 单独写。

## 本机要求

- JDK 17（仓库按 `D:\tools\jdk17` 来；不要用默认的 JDK 8）
- Node.js 18+
- Maven 不必装，Hub 自带 `mvnw`

## 启动

终端 1：

```powershell
$env:JAVA_HOME = "D:\tools\jdk17"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
cd hub
.\mvnw.cmd spring-boot:run
```

终端 2：

```powershell
cd web
npm install
npm run dev
```

打开 http://localhost:5173

1. 在「模型提供商」配置云厂商
2. 在「智能体」绑定模型和系统提示
3. 打开某个智能体，在右侧对话里发消息，需要时再看轨迹

业务数据在 MySQL 库 `agent_platform`，包括智能体、技能正文、参考文件、脚本和历史版本。连接地址和密码用环境变量 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 配置，不要写进仓库。`skills/` 目录只在 Hub 启动时导入库里还没有的技能，导入之后以数据库为准。API Key 的加密主密钥仍在 `hub/data/master.key`，不要提交这个目录。从 SQLite 切过来后，旧本地库不会自动迁移，需要重新录入提供商和智能体。
