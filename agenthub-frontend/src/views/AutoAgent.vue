<template>
  <div class="agent-container">
    <header class="header">
      <div class="header-inner">
        <button class="back-button" @click="goBack">返回</button>
        <div class="header-title">
          <span class="title">自主智能体</span>
          <span class="subtitle">ReAct · 自主拆步骤调工具</span>
        </div>
        <ModelSettings />
        <div class="status" :class="connectionStatus">
          {{ statusText }}
        </div>
      </div>
    </header>

    <main class="content-wrapper">
      <div class="chat-area">
        <ChatRoom
          :messages="messages"
          :connection-status="connectionStatus"
          ai-type="agent"
          @send-message="sendMessage"
        />
      </div>
    </main>

    <div class="footer-container">
      <AppFooter />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import ChatRoom from '../components/ChatRoom.vue'
import ModelSettings from '../components/ModelSettings.vue'
import AppFooter from '../components/AppFooter.vue'
import { chatWithAgent } from '../api'

useHead({
  title: '自主智能体 - AgentHub 智能体应用平台',
  meta: [
    {
      name: 'description',
      content: '自主智能体按 ReAct 模式自己拆解任务、挑选工具、执行并观察结果，支持搜索、抓页面、读写文件、执行命令、生成 PDF'
    },
    {
      name: 'keywords',
      content: '自主智能体,ReAct,Agent,工具调用,自动规划,AgentHub,AI智能体'
    }
  ]
})

const router = useRouter()
const messages = ref([])
const connectionStatus = ref('disconnected')
let eventSource = null

const statusText = computed(() => {
  if (connectionStatus.value === 'connecting') return '执行中'
  if (connectionStatus.value === 'error') return '连接异常'
  return '就绪'
})

const addMessage = (content, isUser, type = '') => {
  messages.value.push({ content, isUser, type, time: new Date().getTime() })
}

const sendMessage = (message) => {
  addMessage(message, true, 'user-question')

  if (eventSource) eventSource.close()

  connectionStatus.value = 'connecting'

  let messageBuffer = []
  let lastBubbleTime = Date.now()
  let isFirstResponse = true
  let finished = false

  const chineseEndPunctuation = ['。', '！', '？', '…']
  const minBubbleInterval = 800

  const createBubble = (content, type = 'ai-answer') => {
    if (!content.trim()) return
    const now = Date.now()
    const timeSinceLastBubble = now - lastBubbleTime

    if (isFirstResponse) {
      addMessage(content, false, type)
      isFirstResponse = false
    } else if (timeSinceLastBubble < minBubbleInterval) {
      setTimeout(() => addMessage(content, false, type), minBubbleInterval - timeSinceLastBubble)
    } else {
      addMessage(content, false, type)
    }

    lastBubbleTime = now
    messageBuffer = []
  }

  eventSource = chatWithAgent(message)

  eventSource.onmessage = (event) => {
    const data = event.data

    if (data && data !== '[DONE]') {
      messageBuffer.push(data)
      const combinedText = messageBuffer.join('')
      const lastChar = data.charAt(data.length - 1)
      const hasCompleteSentence = chineseEndPunctuation.includes(lastChar) || data.includes('\n\n')
      const isLongEnough = combinedText.length > 40

      if (hasCompleteSentence || isLongEnough) {
        createBubble(combinedText)
      }
    }

    if (data === '[DONE]') {
      finished = true
      if (messageBuffer.length > 0) {
        createBubble(messageBuffer.join(''), 'ai-final')
      }
      connectionStatus.value = 'disconnected'
      eventSource.close()
    }
  }

  eventSource.onerror = () => {
    // 服务端正常 complete 时浏览器也会触发 onerror，这里按「是否已收到结束标记」区分
    if (finished) {
      connectionStatus.value = 'disconnected'
    } else {
      connectionStatus.value = 'error'
      if (messageBuffer.length > 0) {
        createBubble(messageBuffer.join(''), 'ai-error')
      }
    }
    eventSource.close()
  }
}

const goBack = () => router.push('/')

onMounted(() => {
  addMessage(
    '说出你的目标，我会自己拆步骤去做。比如「列一下当前目录的文件，然后把结果写进 note.txt」——我会调用终端执行、再调用文件工具写入，每一步都会显示在这里。',
    false
  )
})

onBeforeUnmount(() => {
  if (eventSource) eventSource.close()
})
</script>

<style scoped>
.agent-container {
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

.status {
  flex: none;
  font-size: 11.5px;
  padding: 4px 11px;
  border-radius: 999px;
  border: 1px solid #e6e9f0;
  color: #7b8595;
  background: #fbfcfe;
}

.status.connecting {
  color: #2f6bd8;
  border-color: #d7e2f9;
  background: #f3f7ff;
}

.status.error {
  color: #c2410c;
  border-color: #f6ddd2;
  background: #fdf5f1;
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

.chat-area {
  flex: 1;
  min-height: 460px;
  display: flex;
  flex-direction: column;
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
}

@media (max-width: 480px) {
  .status {
    display: none;
  }

  .chat-area {
    min-height: 400px;
  }
}
</style>
