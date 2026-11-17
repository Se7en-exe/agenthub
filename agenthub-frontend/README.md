# AgentHub 前端

Vue 3 + Vite 写的两个页面：知识库助手、自主智能体。

## 结构

```
src/
├── api/index.js                 axios 实例 + SSE 连接封装
├── router/index.js              三页路由（/ /assistant /agent）
├── views/
│   ├── Home.vue                 首页，两个应用入口
│   ├── Assistant.vue            知识库助手对话页
│   └── AutoAgent.vue            自主智能体对话页
└── components/
    ├── ChatRoom.vue             通用对话区（消息列表 + 输入框）
    ├── AiAvatarFallback.vue     头像占位
    └── AppFooter.vue            页脚
```

## 开发

```bash
npm install
npm run dev      # 开发服务器，默认 5173，代理到 http://localhost:8123
npm run build    # 产物输出 dist/
```

## 与后端对接的两个坑

**1. base 两处都要改。** 前端打包后放在后端的 `/api/` 上下文下，`vite.config.js` 要设 `base: '/api/'`，`router/index.js` 要设 `createWebHistory('/api/')`。只改 vite 不改 router，页面会渲染成空壳 —— 所有请求都 200，但 `#app` 里只有一个注释节点，看网络面板查不出问题。

**2. 刷新页面要后端转发。** 前端路由是 history 模式，直接访问 `/api/assistant` 会打到后端而不是静态文件。`SpaForwardController` 把 `/assistant`、`/agent` 转发回 `index.html`，否则刷新就是 404。

## 接口

| 路径 | 说明 |
|---|---|
| `/api/ai/assistant/chat/sse` | 知识库助手，SSE |
| `/api/ai/agent/chat` | 自主智能体，SSE 逐步推送 |

两个接口都用 `EventSource` 收，收到 `[DONE]` 表示流结束。