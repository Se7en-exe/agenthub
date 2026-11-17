<template>
  <div class="page">
    <div class="page-bg" aria-hidden="true"></div>

    <main class="shell">
      <header class="hero">
        <div class="eyebrow">
          <span class="dot"></span>
          <span>Spring AI 1.0 · 本地向量检索 · ReAct 智能体</span>
        </div>
        <h1 class="hero-title">
          <span class="ink">AgentHub</span>
          <span class="muted">智能体平台</span>
        </h1>
        <p class="hero-sub">
          两个可以独立使用的 AI 应用。一个只回答你文档里写着的内容，答完告诉你出处；
          一个会自己拆步骤、挑工具，把活干完再收手。
        </p>
      </header>

      <section class="cards">
        <article
          class="card card-doc"
          tabindex="0"
          @click="go('/assistant')"
          @keyup.enter="go('/assistant')"
        >
          <div class="card-top">
            <div class="badge badge-doc">📚</div>
            <span class="tag">RAG</span>
          </div>
          <h2 class="card-title">知识库助手</h2>
          <p class="card-desc">把私有文档切片入库，提问时先检索再回答，答案标注依据来自哪一篇。</p>
          <ul class="points">
            <li>本地 ONNX 向量模型，不联网、不产生调用费</li>
            <li>检索不到就明确拒答，不编造</li>
            <li>多轮记忆 + SSE 逐字输出</li>
          </ul>
          <div class="card-foot">
            <span class="enter">进入应用</span>
            <span class="arrow">→</span>
          </div>
        </article>

        <article
          class="card card-agent"
          tabindex="0"
          @click="go('/agent')"
          @keyup.enter="go('/agent')"
        >
          <div class="card-top">
            <div class="badge badge-agent">🤖</div>
            <span class="tag">ReAct</span>
          </div>
          <h2 class="card-title">自主智能体</h2>
          <p class="card-desc">给一个目标，它自己决定下一步做什么，调用工具、看结果、再判断，直到收工。</p>
          <ul class="points">
            <li>7 个内置工具：搜索、抓页面、读写文件、执行命令、生成 PDF</li>
            <li>先想后做两段式，最大步数熔断防死循环</li>
            <li>逐步 SSE 推送，全过程可见</li>
          </ul>
          <div class="card-foot">
            <span class="enter">进入应用</span>
            <span class="arrow">→</span>
          </div>
        </article>
      </section>

      <section class="stack">
        <span class="stack-label">技术构成</span>
        <div class="chips">
          <span v-for="t in stack" :key="t" class="chip">{{ t }}</span>
        </div>
      </section>
    </main>

    <AppFooter />
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import AppFooter from '../components/AppFooter.vue'

useHead({
  title: 'AgentHub 智能体应用平台 - 首页',
  meta: [
    {
      name: 'description',
      content: 'AgentHub 智能体应用平台：基于私有文档的检索增强问答，以及会自己拆步骤调工具的 ReAct 智能体'
    },
    {
      name: 'keywords',
      content: 'AI智能体,RAG,检索增强,知识库问答,ReAct,AgentHub'
    }
  ]
})

const router = useRouter()
const go = (path) => router.push(path)

const stack = [
  'Spring Boot 3',
  'Java 21',
  'Spring AI 1.0',
  'DeepSeek',
  'ONNX Runtime',
  'bge-small-zh',
  'Vue 3',
  'SSE'
]
</script>

<style scoped>
.page {
  position: relative;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f7f8fa;
  color: #101418;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC",
    "Hiragino Sans GB", "Microsoft YaHei", "Helvetica Neue", Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  overflow-x: hidden;
}

.page-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(900px 420px at 12% -8%, rgba(20, 122, 107, 0.10), transparent 65%),
    radial-gradient(820px 400px at 92% 4%, rgba(47, 107, 216, 0.10), transparent 62%);
}

.shell {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 1080px;
  margin: 0 auto;
  padding: 0 28px;
  flex: 1;
}

.hero {
  padding: 96px 0 56px;
  max-width: 760px;
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  padding: 6px 14px 6px 11px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid #e4e7ee;
  box-shadow: 0 1px 2px rgba(16, 20, 24, 0.04);
  font-size: 12.5px;
  letter-spacing: 0.2px;
  color: #5b6472;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #17a673;
  box-shadow: 0 0 0 3px rgba(23, 166, 115, 0.16);
}

.hero-title {
  margin: 26px 0 0;
  font-size: 56px;
  line-height: 1.08;
  font-weight: 700;
  letter-spacing: -1.6px;
}

.hero-title .ink {
  background: linear-gradient(96deg, #101418 20%, #2f6bd8 78%, #147a6b 108%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.hero-title .muted {
  color: #9aa3b1;
  font-weight: 600;
  margin-left: 10px;
}

.hero-sub {
  margin: 22px 0 0;
  max-width: 620px;
  font-size: 15.5px;
  line-height: 1.85;
  color: #5b6472;
}

.cards {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 22px;
  padding-bottom: 8px;
}

.card {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 28px 28px 24px;
  background: #fff;
  border: 1px solid #e6e9f0;
  border-radius: 18px;
  cursor: pointer;
  outline: none;
  overflow: hidden;
  transition: transform 0.32s cubic-bezier(0.22, 0.61, 0.36, 1),
    box-shadow 0.32s cubic-bezier(0.22, 0.61, 0.36, 1), border-color 0.32s;
}

.card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  transform: scaleX(0.28);
  transform-origin: left;
  transition: transform 0.42s cubic-bezier(0.22, 0.61, 0.36, 1);
}

.card-doc::before {
  background: linear-gradient(90deg, #14b8a6, #0e9f6e);
}

.card-agent::before {
  background: linear-gradient(90deg, #2f6bd8, #6d5efc);
}

.card:hover {
  transform: translateY(-4px);
  border-color: #d7dce6;
  box-shadow: 0 18px 40px -18px rgba(16, 20, 24, 0.22), 0 2px 6px rgba(16, 20, 24, 0.04);
}

.card:hover::before {
  transform: scaleX(1);
}

.card:focus-visible {
  border-color: #2f6bd8;
  box-shadow: 0 0 0 3px rgba(47, 107, 216, 0.18);
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.badge {
  width: 46px;
  height: 46px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 13px;
  font-size: 22px;
}

.badge-doc {
  background: #e7f7f2;
  box-shadow: inset 0 0 0 1px #cdeee3;
}

.badge-agent {
  background: #eaf0fd;
  box-shadow: inset 0 0 0 1px #d5e1fa;
}

.tag {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 1.1px;
  padding: 4px 9px;
  border-radius: 6px;
  color: #7b8595;
  background: #f4f6f9;
  border: 1px solid #eaedf3;
}

.card-title {
  margin: 0 0 10px;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.3px;
  color: #101418;
}

.card-desc {
  margin: 0 0 20px;
  font-size: 14px;
  line-height: 1.75;
  color: #5b6472;
}

.points {
  list-style: none;
  margin: 0 0 26px;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 11px;
}

.points li {
  position: relative;
  padding-left: 20px;
  font-size: 13.5px;
  line-height: 1.6;
  color: #3c4553;
}

.points li::before {
  content: '';
  position: absolute;
  left: 3px;
  top: 7px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.card-doc .points li::before {
  background: #14b8a6;
}

.card-agent .points li::before {
  background: #2f6bd8;
}

.card-foot {
  margin-top: auto;
  padding-top: 18px;
  border-top: 1px solid #eef1f6;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.enter {
  font-size: 13.5px;
  font-weight: 600;
  color: #2f6bd8;
}

.card-doc .enter {
  color: #0e9f6e;
}

.arrow {
  font-size: 16px;
  color: #b6bec9;
  transition: transform 0.3s ease, color 0.3s ease;
}

.card:hover .arrow {
  transform: translateX(5px);
  color: #5b6472;
}

.stack {
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
  margin: 44px 0 72px;
  padding-top: 26px;
  border-top: 1px solid #e9ecf2;
}

.stack-label {
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 1.4px;
  color: #a3abba;
  flex: none;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chip {
  font-size: 12px;
  color: #6b7484;
  padding: 4px 10px;
  border-radius: 6px;
  background: #fff;
  border: 1px solid #e8ebf1;
  transition: color 0.25s, border-color 0.25s;
}

.chip:hover {
  color: #2f6bd8;
  border-color: #cdd9f3;
}

@media (max-width: 860px) {
  .cards {
    grid-template-columns: 1fr;
  }

  .hero {
    padding: 64px 0 40px;
  }

  .hero-title {
    font-size: 40px;
  }

  .stack {
    margin-bottom: 56px;
  }
}

@media (max-width: 480px) {
  .shell {
    padding: 0 18px;
  }

  .hero-title {
    font-size: 32px;
  }

  .hero-title .muted {
    display: block;
    margin: 6px 0 0;
  }

  .card {
    padding: 22px 20px 20px;
  }
}
</style>
