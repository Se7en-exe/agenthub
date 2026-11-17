<template>
  <div class="chat-container">
    <div class="chat-messages" ref="messagesContainer">
      <div v-for="(msg, index) in messages" :key="index" class="message-wrapper">
        <div v-if="!msg.isUser" class="message ai-message" :class="[msg.type]">
          <div class="avatar ai-avatar">
            <AiAvatarFallback :type="aiType" />
          </div>

          <!-- 工具执行结果：折叠块，默认只露一行摘要 -->
          <div v-if="isToolStep(msg.content)" class="message-body">
            <div class="tool-step">
              <div class="tool-head" @click="toggle(index)">
                <span class="tool-badge">{{ toolName(msg.content) }}</span>
                <span class="tool-summary">{{ summarize(msg.content) }}</span>
                <span class="tool-toggle">{{ expanded[index] ? '收起' : '展开' }}</span>
              </div>
              <pre v-if="expanded[index]" class="tool-body">{{ normalize(fullText(msg.content)) }}</pre>
            </div>
            <div class="message-time">{{ formatTime(msg.time) }}</div>
          </div>

          <!-- 普通回答 -->
          <div v-else class="message-body">
            <div class="message-bubble">
              <span class="message-content">{{ normalize(msg.content) }}</span>
              <span
                v-if="connectionStatus === 'connecting' && index === messages.length - 1"
                class="typing-indicator"
              ></span>
            </div>
            <div class="message-time">{{ formatTime(msg.time) }}</div>
          </div>
        </div>

        <div v-else class="message user-message" :class="[msg.type]">
          <div class="message-body">
            <div class="message-bubble">
              <span class="message-content">{{ msg.content }}</span>
            </div>
            <div class="message-time">{{ formatTime(msg.time) }}</div>
          </div>
          <div class="avatar user-avatar">我</div>
        </div>
      </div>
    </div>

    <div class="chat-input-container">
      <div class="chat-input">
        <textarea
          v-model="inputMessage"
          @keydown.enter.prevent="sendMessage"
          placeholder="输入问题，回车发送"
          class="input-box"
          rows="1"
          :disabled="connectionStatus === 'connecting'"
        ></textarea>
        <button
          @click="sendMessage"
          class="send-button"
          :disabled="connectionStatus === 'connecting' || !inputMessage.trim()"
        >发送</button>
      </div>
      <div class="input-hint">Enter 发送 · 回答由模型生成，重要信息请自行核对</div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick, watch } from 'vue'
import AiAvatarFallback from './AiAvatarFallback.vue'

const props = defineProps({
  messages: { type: Array, default: () => [] },
  connectionStatus: { type: String, default: 'disconnected' },
  aiType: { type: String, default: 'default' }
})

const emit = defineEmits(['send-message'])

const inputMessage = ref('')
const messagesContainer = ref(null)
const expanded = reactive({})

const toggle = (i) => {
  expanded[i] = !expanded[i]
}

// 工具执行结果形如：Step 1: 工具 executeTerminalCommand 返回的结果："..."
const TOOL_RE = /^Step\s+\d+:\s*工具\s+(\S+)\s+返回的结果：([\s\S]*)$/

const isToolStep = (text) => TOOL_RE.test(String(text || ''))

const toolName = (text) => {
  const m = String(text || '').match(TOOL_RE)
  return m ? m[1] : ''
}

const fullText = (text) => {
  const m = String(text || '').match(TOOL_RE)
  return m ? m[2] : String(text || '')
}

// 摘要只留一行，够判断是什么结果就行
const summarize = (text) => {
  const raw = fullText(text).replace(/^["'`]+|["'`]+$/g, '')
  const oneLine = raw.replace(/\\r\\n|\\n|\r?\n/g, ' ').replace(/\s+/g, ' ').trim()
  return oneLine.length > 110 ? oneLine.slice(0, 110) + '…' : oneLine
}

// 工具结果里带的是字面量反斜杠n（JSON 转义后没还原），显示前换回真正的换行
const normalize = (text) =>
  String(text || '')
    .replace(/\\r\\n|\\n/g, String.fromCharCode(10))
    .replace(/\*\*(.+?)\*\*/g, '$1')
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/^```[a-zA-Z]*$/gm, '')

const sendMessage = () => {
  if (!inputMessage.value.trim()) return
  emit('send-message', inputMessage.value)
  inputMessage.value = ''
}

const formatTime = (timestamp) => {
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

watch(() => props.messages.length, scrollToBottom)
watch(() => props.messages.map((m) => m.content).join(''), scrollToBottom)

onMounted(scrollToBottom)
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: #ffffff;
  border: 1px solid #e6e9f0;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(16, 20, 24, 0.04);
}

.chat-messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 26px 26px 12px;
  display: flex;
  flex-direction: column;
  scroll-behavior: smooth;
}

.message-wrapper {
  margin-bottom: 20px;
  width: 100%;
}

.message {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  max-width: 88%;
}

.user-message {
  margin-left: auto;
  justify-content: flex-end;
}

.ai-message {
  margin-right: auto;
}

.avatar {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 2px;
}

.user-avatar {
  background: #101418;
  color: #fff;
  font-size: 12.5px;
  font-weight: 600;
}

.message-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.message-bubble {
  padding: 11px 15px;
  border-radius: 14px;
  word-wrap: break-word;
  overflow-wrap: anywhere;
}

.user-message .message-bubble {
  background: #101418;
  color: #fff;
  border-bottom-right-radius: 5px;
}

.ai-message .message-bubble {
  background: #f4f6f9;
  color: #1c2430;
  border-bottom-left-radius: 5px;
  border: 1px solid #eaedf3;
}

.message-content {
  font-size: 14.5px;
  line-height: 1.72;
  white-space: pre-wrap;
}

.message-time {
  font-size: 11.5px;
  color: #a3abba;
  margin-top: 6px;
  padding: 0 3px;
}

.user-message .message-time {
  text-align: right;
}

.chat-input-container {
  flex: none;
  border-top: 1px solid #eef1f6;
  background: #fff;
  padding: 14px 20px 12px;
}

.chat-input {
  display: flex;
  align-items: flex-end;
  gap: 10px;
}

.input-box {
  flex: 1;
  border: 1px solid #e2e6ee;
  border-radius: 12px;
  padding: 11px 14px;
  font-size: 14.5px;
  font-family: inherit;
  line-height: 1.6;
  resize: none;
  min-height: 44px;
  max-height: 108px;
  outline: none;
  color: #101418;
  background: #fbfcfe;
  transition: border-color 0.22s, background 0.22s;
  overflow-y: auto;
}

.input-box::placeholder {
  color: #a8b0bd;
}

.input-box:focus {
  border-color: #b9cdf4;
  background: #fff;
}

.send-button {
  flex: none;
  height: 44px;
  padding: 0 22px;
  border: none;
  border-radius: 12px;
  background: #2f6bd8;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.22s, opacity 0.22s;
}

.send-button:hover:not(:disabled) {
  background: #2456b3;
}

.send-button:disabled {
  opacity: 0.42;
  cursor: not-allowed;
}

.input-hint {
  margin-top: 8px;
  font-size: 11.5px;
  color: #b0b7c4;
  padding-left: 2px;
}

.typing-indicator {
  display: inline-block;
  width: 2px;
  height: 14px;
  margin-left: 2px;
  vertical-align: -2px;
  background: #2f6bd8;
  animation: blink 0.9s steps(2, start) infinite;
}

@keyframes blink {
  0% { opacity: 1; }
  50% { opacity: 0; }
  100% { opacity: 1; }
}

.ai-answer {
  animation: fadeIn 0.3s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}

.ai-error {
  opacity: 0.65;
}

.ai-message + .ai-message {
  margin-top: 6px;
}

.ai-message + .ai-message .avatar {
  visibility: hidden;
}

.chat-messages::-webkit-scrollbar {
  width: 6px;
}

.chat-messages::-webkit-scrollbar-thumb {
  background: #dde2ea;
  border-radius: 3px;
}

.chat-messages::-webkit-scrollbar-track {
  background: transparent;
}

@media (max-width: 768px) {
  .message {
    max-width: 94%;
  }

  .chat-messages {
    padding: 18px 16px 8px;
  }

  .message-content {
    font-size: 14px;
  }

  .chat-input-container {
    padding: 12px 14px 10px;
  }
}
.tool-step {
  max-width: 100%;
  border: 1px solid #e6e9f0;
  border-radius: 12px;
  background: #fbfcfe;
  overflow: hidden;
}

.tool-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 14px;
  cursor: pointer;
  user-select: none;
  transition: background 0.2s;
}

.tool-head:hover {
  background: #f4f7fc;
}

.tool-badge {
  flex: none;
  font-size: 11.5px;
  font-weight: 600;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  color: #2f6bd8;
  background: #eaf0fd;
  border: 1px solid #d5e1fa;
  border-radius: 6px;
  padding: 2px 8px;
}

.tool-summary {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: #6b7484;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tool-toggle {
  flex: none;
  font-size: 12px;
  color: #9aa3b1;
}

.tool-head:hover .tool-toggle {
  color: #2f6bd8;
}

.tool-body {
  margin: 0;
  padding: 12px 14px 14px;
  border-top: 1px solid #eef1f6;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12.5px;
  line-height: 1.65;
  color: #3c4553;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 320px;
  overflow-y: auto;
  background: #fff;
}

</style>
