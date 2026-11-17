<template>
  <div class="assistant-container">
    <header class="header">
      <div class="header-inner">
        <button class="back-button" @click="goBack">返回</button>
        <div class="header-title">
          <span class="title">知识库助手</span>
          <span class="subtitle">检索增强 · 标注出处</span>
        </div>
        <button class="kb-toggle" :class="{ active: showPanel }" @click="showPanel = !showPanel">
          {{ showPanel ? '收起知识库' : '知识库' }}
        </button>
        <ModelSettings />
        <div class="chat-id">会话 {{ chatId }}</div>
      </div>
    </header>

    <main class="content-wrapper">
      <div class="body-row">
        <div class="chat-area">
          <ChatRoom
            :messages="messages"
            :connection-status="connectionStatus"
            ai-type="assistant"
            @send-message="sendMessage"
          />
        </div>
        <KnowledgePanel v-model="showPanel" />
      </div>
    </main>

    <div class="footer-container">
      <AppFooter />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import ChatRoom from '../components/ChatRoom.vue'
import KnowledgePanel from '../components/KnowledgePanel.vue'
import ModelSettings from '../components/ModelSettings.vue'
import AppFooter from '../components/AppFooter.vue'
import { chatWithKnowledgeAssistant } from '../api'

useHead({
  title: '知识库助手 - AgentHub 智能体应用平台',
  meta: [
    {
      name: 'description',
      content: '知识库助手把私有文档切片入库做向量检索，回答时标注出自哪一篇，知识库覆盖不到的问题直接拒答而不是编'
    },
    {
      name: 'keywords',
      content: '知识库问答,RAG,检索增强,文档问答,私有知识库,AgentHub,AI智能体'
    }
  ]
})

const router = useRouter()
const messages = ref([])
const chatId = ref('')
const connectionStatus = ref('disconnected')
// 桌面端默认展开知识库面板，让第一次进来的人先看到「这里可以喂文档」
const showPanel = ref(true)
let eventSource = null

const generateChatId = () => 'asst_' + Math.random().toString(36).substring(2, 10)

const addMessage = (content, isUser) => {
  messages.value.push({ content, isUser, time: new Date().getTime() })
}

const sendMessage = (message) => {
  addMessage(message, true)

  if (eventSource) eventSource.close()

  const aiMessageIndex = messages.value.length
  addMessage('', false)

  connectionStatus.value = 'connecting'
  eventSource = chatWithKnowledgeAssistant(message, chatId.value)

  let received = false

  eventSource.onmessage = (event) => {
    const data = event.data
    if (data && data !== '[DONE]') {
      received = true
      if (aiMessageIndex < messages.value.length) {
        messages.value[aiMessageIndex].content += data
      }
    }
    if (data === '[DONE]') {
      connectionStatus.value = 'disconnected'
      eventSource.close()
    }
  }

  // Spring AI 的 Flux 流正常结束时服务端不发 [DONE]，浏览器同样会触发 onerror。
  // 用「是否已收到过内容」区分正常收流和真的连不上，避免把成功请求显示成异常。
  eventSource.onerror = () => {
    connectionStatus.value = received ? 'disconnected' : 'error'
    eventSource.close()
  }
}

const goBack = () => router.push('/')

onMounted(() => {
  chatId.value = generateChatId()
  addMessage(
    '你好，我是知识库助手。右侧可以上传 md、txt、pdf 文档进知识库，传完立刻能问；也可以直接问我产品功能、订单计费、账户安全相关的问题，答案会标注来自哪一篇文档，知识库没覆盖的我直接说不知道。',
    false
  )
})

onBeforeUnmount(() => {
  if (eventSource) eventSource.close()
})
</script>

<style scoped>
.assistant-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: #f7f8fa;
  color: #101418;
}

.header {
  position: sticky;
  top: 0;
  z-index: 10;
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: saturate(180%) blur(12px);
  border-bottom: 1px solid #eaedf3;
}

.header-inner {
  max-width: 1080px;
  margin: 0 auto;
  padding: 14px 28px;
  display: flex;
  align-items: center;
  gap: 18px;
}

.back-button {
  flex: none;
  font-size: 13px;
  color: #5b6472;
  background: #fff;
  border: 1px solid #e4e7ee;
  border-radius: 8px;
  padding: 6px 13px;
  cursor: pointer;
  transition: color 0.22s, border-color 0.22s, background 0.22s;
}

.back-button::before {
  content: '←';
  margin-right: 6px;
}

.back-button:hover {
  color: #2f6bd8;
  border-color: #cdd9f3;
  background: #fafcff;
}

.header-title {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.title {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: -0.2px;
}

.subtitle {
  font-size: 12px;
  color: #9aa3b1;
}

.chat-id {
  flex: none;
  font-size: 11.5px;
  color: #a3abba;
  font-variant-numeric: tabular-nums;
}

.content-wrapper {
  flex: 1;
  min-height: 0;
  width: 100%;
  max-width: 1080px;
  margin: 0 auto;
  padding: 22px 28px 0;
  display: flex;
  flex-direction: column;
}

.body-row {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 16px;
  align-items: stretch;
}

.chat-area {
  flex: 1;
  min-width: 0;
  min-height: 460px;
  display: flex;
  flex-direction: column;
}

.kb-toggle {
  flex: none;
  font-size: 12.5px;
  color: #3c4553;
  background: #fff;
  border: 1px solid #e4e7ee;
  border-radius: 8px;
  padding: 5px 12px;
  cursor: pointer;
  transition: color 0.2s, border-color 0.2s, background 0.2s;
}

.kb-toggle:hover {
  color: #2f6bd8;
  border-color: #cdd9f3;
  background: #fafcff;
}

.kb-toggle.active {
  color: #0e7a5f;
  border-color: #b9e6d8;
  background: #f1fbf7;
}

.chat-area > * {
  flex: 1;
  min-height: 0;
}

.footer-container {
  margin-top: 18px;
}

@media (max-width: 860px) {
  .header-inner,
  .content-wrapper {
    padding-left: 16px;
    padding-right: 16px;
  }

  .subtitle {
    display: none;
  }

  .body-row {
    flex-direction: column;
  }
}

@media (max-width: 480px) {
  .chat-id {
    display: none;
  }

  .chat-area {
    min-height: 400px;
  }
}
</style>