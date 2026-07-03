# AI 超级智能体

基于 **Spring AI** 框架构建的 AI 超级智能体，对应课程架构图实现 RAG 知识库、Tool Calling、MCP、ReAct Agent 等核心能力。

## 技术栈

| 模块 | 技术 |
|------|------|
| 基础框架 | Java 21 + Spring Boot 3 |
| AI 框架 | Spring AI + Spring AI Alibaba（百炼/通义） |
| 向量数据库 | PGvector |
| 工具调用 | Spring AI Tool Calling |
| 协议 | MCP（Model Context Protocol） |
| 文档 | Knife4j |
| 工具库 | Jsoup、iText |

## 架构

```
客户端 → REST API → SuperAgentService
                        ├── Advisors（安全守卫 / RAG / 记忆 / 日志）
                        ├── ChatModel（通义 / DeepSeek / Ollama）
                        ├── Tools（搜索 / 抓取 / PDF / 文件）
                        └── MCP Client（可选）
                              ↓
                    PGvector + PostgreSQL
```

## AI 恋爱大师

对应课程大纲实现的独立应用模块（`com.ragpro.lovemaster`）：

| 课程要点 | 实现 |
|----------|------|
| ChatClient / Advisor / ChatMemory | 独立 `loveMasterChatClient` + JDBC 记忆持久化 |
| 自定义 Advisor | `ReReadingAdvisor`（重读增强）、`LoveSensitiveAdvisor`（安全守卫） |
| 多轮对话 | `POST /api/love-master/chat` |
| 结构化输出 - 恋爱报告 | `POST /api/love-master/report` → `LoveReport` |
| Prompt 模板 | `resources/prompts/lovemaster/*.st` |
| 多模态 | 对话请求支持 `imageUrl` 字段 |
| **RAG 知识问答** | `QuestionAnswerAdvisor` + PGvector 本地向量库 |

### Vue3 前端界面

前端工程位于 `frontend/`，使用 **Vue3（组合式 API）+ Vite** 构建，包含「恋爱对话」（流式 SSE）与「恋爱报告」（结构化输出可视化）两个页面。

```bash
cd frontend
npm install
npm run dev        # 开发服务器 http://localhost:5173（已配置 /api 代理到 8080）
npm run build      # 生产构建，产物在 frontend/dist
```

> 开发模式下 Vite 通过代理转发 `/api` 请求到后端 `localhost:8080`；后端已开启 CORS，也可独立部署。

```bash
# 多轮对话
curl -X POST http://localhost:8080/api/love-master/chat \
  -H "Content-Type: application/json" \
  -d "{\"message\":\"暗恋同事很久了，该怎么开口？\",\"conversationId\":\"user-001\"}"

# 恋爱报告（结构化输出）
curl -X POST http://localhost:8080/api/love-master/report \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"小明\",\"gender\":\"男\",\"situation\":\"和女友异地一年，最近联系变少\",\"partnerInfo\":\"性格内向，工作较忙\"}"
```

### RAG 知识问答（方式一：本地 PGvector）

```
离线 ETL：Read → Split → Write（启动时自动导入 knowledge/love/）
运行时 RAG：Retrieve → Augment → Generate
```

| 接口 | 说明 |
|------|------|
| `POST /api/love-master/knowledge/ask` | RAG 知识问答（返回答案 + 检索片段） |
| `GET /api/love-master/knowledge/search?query=` | 仅检索（演示 Retrieve 步骤） |
| `POST /api/love-master/knowledge/text` | 导入恋爱知识文本 |
| `GET /api/love-master/knowledge/stats` | 知识库状态 |

前端「知识问答」Tab 可可视化检索片段与回答。

## 大模型接入

| 模型 | Provider ID | 接入方式 | 环境变量 |
|------|-------------|----------|----------|
| 通义千问（百炼） | `dashscope` | Spring AI Alibaba | `DASHSCOPE_API_KEY` |
| DeepSeek | `deepseek` | 官方 Starter | `DEEPSEEK_API_KEY` + `DEEPSEEK_ENABLED=true` |
| 豆包 | `doubao` | OpenAI 兼容接口 | `DOUBAO_API_KEY` + `DOUBAO_ENABLED=true` |
| Ollama 本地 | `ollama` | 本地部署 | `OLLAMA_ENABLED=true` |

### 配置示例

```bash
# 百炼（默认）
set DASHSCOPE_API_KEY=sk-xxx

# 启用 DeepSeek
set DEEPSEEK_ENABLED=true
set DEEPSEEK_API_KEY=sk-xxx

# 启用豆包（火山引擎 Ark）
set DOUBAO_ENABLED=true
set DOUBAO_API_KEY=xxx
set DOUBAO_MODEL=doubao-pro-32k
```

### 模型 API

| 接口 | 说明 |
|------|------|
| `GET /api/agent/models` | 查看所有模型及可用状态 |
| `GET /api/agent/models/default` | 当前默认模型 |
| `POST /api/models/{modelId}/test` | 测试模型连通性 |

对话时通过 `model` 字段指定模型：

```json
{
  "message": "你好",
  "conversationId": "user-001",
  "model": "deepseek"
}
```

## 环境要求

| 工具 | 路径（本机示例） |
|------|------------------|
| JDK 21 | `D:\work\jdk21` |
| Maven 3.9+ | `D:\work\apache-maven-3.9.9-bin\apache-maven-3.9.9` |

Windows 下可先加载环境变量：

```powershell
. .\scripts\env.ps1
```

或 CMD：

```cmd
call scripts\env.bat
```

## 快速开始

### 1. 启动 PGvector

```bash
docker compose up -d
```

### 2. 配置环境变量

```bash
# 百炼 API Key（必填，至少配置一个模型）
export DASHSCOPE_API_KEY=your-dashscope-api-key

# 可选
export DEEPSEEK_API_KEY=your-deepseek-api-key
export SEARCH_API_KEY=your-searchapi-key
export MCP_ENABLED=false
```

### 3. 启动应用

```bash
mvn spring-boot:run
```

### 4. 访问 API 文档

http://localhost:8080/doc.html

## 核心 API

| 接口 | 说明 |
|------|------|
| `POST /api/agent/chat` | 同步对话（RAG + Tools + Memory） |
| `POST /api/agent/chat/stream` | 流式对话（SSE） |
| `GET /api/agent/models` | 可用模型列表 |
| `POST /api/rag/documents` | 上传文档到知识库 |
| `POST /api/rag/text` | 导入文本到知识库 |

### 对话示例

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"你好，介绍一下你自己","conversationId":"user-001"}'
```

### 导入知识库

```bash
curl -X POST http://localhost:8080/api/rag/text \
  -H "Content-Type: application/json" \
  -d '{"content":"Spring AI 是 Spring 官方的 AI 应用开发框架","category":"tech"}'
```

## 项目结构

```
src/main/java/com/ragpro/superagent/
├── SuperAgentApplication.java      # 启动类
├── config/                         # AI / Memory / OpenAPI 配置
├── advisor/                        # 前置/后置拦截器
├── agent/                          # 智能体核心服务
├── controller/                     # REST API
├── rag/                            # RAG ETL（Read → Split → Write）
├── tool/                           # Tool Calling 工具
└── model/                          # 请求/响应 DTO
```

## MCP 集成

在 `application.yml` 中设置 `spring.ai.mcp.client.enabled=true` 并配置 MCP Server 连接，智能体即可调用外部 MCP 工具。

## 后续扩展

- [ ] ReAct Agent 多步推理循环
- [ ] LangChain4j 混合集成
- [ ] Kryo 高性能序列化
- [ ] Serverless 部署配置
- [ ] 前端 Vue3 对话界面
