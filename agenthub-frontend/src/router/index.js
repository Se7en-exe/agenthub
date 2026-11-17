import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    meta: {
      title: '首页 - AgentHub 智能体应用平台',
      description: 'AgentHub 智能体应用平台提供知识库助手和自主智能体服务，满足您的各种AI对话需求'
    }
  },
  {
    path: '/assistant',
    name: 'Assistant',
    component: () => import('../views/Assistant.vue'),
    meta: {
      title: '知识库助手 - AgentHub 智能体应用平台',
      description: '知识库助手把私有文档切成片段做向量检索，回答时标注原文出处，知识库外的问题会明确拒答'
    }
  },
  {
    path: '/agent',
    name: 'AutoAgent',
    component: () => import('../views/AutoAgent.vue'),
    meta: {
      title: '自主智能体 - AgentHub 智能体应用平台',
      description: '自主智能体是AgentHub 智能体应用平台的全能助手，能解答各类专业问题，提供精准建议和解决方案'
    }
  }
]

const router = createRouter({
  // 页面挂在后端的 /api 上下文下（http://127.0.0.1:8123/api/），
  // 路由 base 必须同步，否则 createWebHistory() 默认的 '/' 匹配不到任何 route，页面只会渲染空壳。
  history: createWebHistory('/api/'),
  routes
})

// 全局导航守卫，设置文档标题
router.beforeEach((to, from, next) => {
  // 设置页面标题
  if (to.meta.title) {
    document.title = to.meta.title
  }
  next()
})

export default router 