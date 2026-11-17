<template>
  <aside class="kb-panel" :class="{ open: modelValue }">
    <div class="kb-head">
      <div class="kb-title">
        <span class="kb-name">知识库</span>
        <span class="kb-count">{{ files.length }} 份 · {{ chunks }} 片</span>
      </div>
      <button class="kb-close" @click="$emit('update:modelValue', false)">收起</button>
    </div>

    <div
      class="drop-zone"
      :class="{ dragging, busy }"
      @click="pickFile"
      @dragover.prevent="dragging = true"
      @dragleave.prevent="dragging = false"
      @drop.prevent="onDrop"
    >
      <input ref="fileInput" type="file" accept=".md,.txt,.pdf,.csv,.log" hidden @change="onPick" />
      <template v-if="busy">
        <div class="spinner"></div>
        <p class="drop-main">正在切片入库…</p>
        <p class="drop-sub">首次上传要加载本地向量模型，约 20 秒</p>
      </template>
      <template v-else>
        <div class="drop-icon">+</div>
        <p class="drop-main">拖文件进来，或点击选择</p>
        <p class="drop-sub">支持 md / txt / pdf，单个不超过 20MB</p>
      </template>
    </div>

    <p v-if="message" class="kb-msg" :class="messageType">{{ message }}</p>

    <div class="file-list">
      <p v-if="loading" class="file-empty">读取中…</p>
      <p v-else-if="!files.length" class="file-empty">知识库还是空的</p>
      <div v-for="f in files" :key="f.name" class="file-item">
        <div class="file-main">
          <span class="file-name" :title="f.name">{{ f.name }}</span>
          <span class="file-meta">
            {{ f.source === 'builtin' ? '内置' : '上传' }} · {{ f.chunks }} 片
          </span>
        </div>
        <button class="file-del" title="从知识库移除" @click="remove(f.name)">删除</button>
      </div>
    </div>

    <p class="kb-tip">上传后立刻生效，同一个会话里追问就能答出新内容。</p>
  </aside>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { listKnowledge, uploadKnowledge, deleteKnowledge } from '../api'

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})
defineEmits(['update:modelValue'])

const files = ref([])
const chunks = ref(0)
const loading = ref(false)
const busy = ref(false)
const dragging = ref(false)
const message = ref('')
const messageType = ref('ok')
const fileInput = ref(null)

const flash = (text, type = 'ok') => {
  message.value = text
  messageType.value = type
  setTimeout(() => {
    if (message.value === text) message.value = ''
  }, 6000)
}

const refresh = async () => {
  loading.value = true
  try {
    const res = await listKnowledge()
    files.value = res.data.files || []
    chunks.value = res.data.chunks || 0
  } catch (e) {
    flash('读取知识库失败：' + describe(e), 'err')
  } finally {
    loading.value = false
  }
}

const describe = (e) =>
  (e && e.response && e.response.data && e.response.data.message) ||
  (e && e.message) ||
  '网络异常'

const upload = async (file) => {
  if (!file) return
  busy.value = true
  try {
    const res = await uploadKnowledge(file)
    const d = res.data.data || {}
    flash(`已入库「${d.name}」，切了 ${d.chunks} 片，共 ${d.chars} 字符`)
    await refresh()
  } catch (e) {
    flash('上传失败：' + describe(e), 'err')
  } finally {
    busy.value = false
    if (fileInput.value) fileInput.value.value = ''
  }
}

const pickFile = () => {
  if (!busy.value) fileInput.value && fileInput.value.click()
}

const onPick = (event) => upload(event.target.files && event.target.files[0])

const onDrop = (event) => {
  dragging.value = false
  if (busy.value) return
  const file = event.dataTransfer && event.dataTransfer.files && event.dataTransfer.files[0]
  upload(file)
}

const remove = async (name) => {
  try {
    const res = await deleteKnowledge(name)
    flash(`已移除「${name}」，删掉 ${res.data.removed} 片`)
    await refresh()
  } catch (e) {
    flash('删除失败：' + describe(e), 'err')
  }
}

watch(() => props.modelValue, (open) => {
  if (open) refresh()
})

onMounted(() => {
  if (props.modelValue) refresh()
})
</script>

<style scoped>
.kb-panel {
  flex: none;
  width: 296px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  background: #ffffff;
  border: 1px solid #e6e9f0;
  border-radius: 14px;
  align-self: stretch;
  overflow-y: auto;
}

.kb-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.kb-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.kb-name {
  font-size: 14px;
  font-weight: 700;
  color: #101418;
}

.kb-count {
  font-size: 11.5px;
  color: #9aa3b1;
  font-variant-numeric: tabular-nums;
}

.kb-close {
  font-size: 12px;
  color: #6b7484;
  background: #f4f6fa;
  border: 1px solid #e6e9f0;
  border-radius: 7px;
  padding: 4px 10px;
  cursor: pointer;
}

.kb-close:hover {
  color: #2f6bd8;
  border-color: #cdd9f3;
}

.drop-zone {
  border: 1.5px dashed #d3dae6;
  border-radius: 12px;
  padding: 20px 14px;
  text-align: center;
  cursor: pointer;
  background: #fbfcfe;
  transition: border-color 0.2s, background 0.2s;
}

.drop-zone:hover,
.drop-zone.dragging {
  border-color: #2f6bd8;
  background: #f4f8ff;
}

.drop-zone.busy {
  cursor: progress;
  border-color: #14b8a6;
  background: #f2fbf8;
}

.drop-icon {
  width: 30px;
  height: 30px;
  line-height: 28px;
  margin: 0 auto 8px;
  border-radius: 9px;
  background: #eaf0fd;
  color: #2f6bd8;
  font-size: 18px;
  font-weight: 500;
}

.drop-main {
  margin: 0;
  font-size: 13px;
  color: #3c4553;
}

.drop-sub {
  margin: 5px 0 0;
  font-size: 11.5px;
  color: #9aa3b1;
}

.spinner {
  width: 20px;
  height: 20px;
  margin: 4px auto 10px;
  border: 2px solid #cfe9e3;
  border-top-color: #14b8a6;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.kb-msg {
  margin: 0;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.6;
}

.kb-msg.ok {
  color: #0e7a5f;
  background: #eefaf5;
  border: 1px solid #cfeee3;
}

.kb-msg.err {
  color: #b42318;
  background: #fef3f2;
  border: 1px solid #fadbd9;
}

.file-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.file-empty {
  margin: 0;
  font-size: 12px;
  color: #9aa3b1;
  text-align: center;
  padding: 8px 0;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: #f8fafc;
  border: 1px solid #eef1f6;
  border-radius: 9px;
}

.file-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.file-name {
  font-size: 12.5px;
  color: #3c4553;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-meta {
  font-size: 11px;
  color: #9aa3b1;
}

.file-del {
  flex: none;
  font-size: 11.5px;
  color: #9aa3b1;
  background: none;
  border: none;
  cursor: pointer;
  padding: 2px 4px;
}

.file-del:hover {
  color: #b42318;
}

.kb-tip {
  margin: 0;
  font-size: 11.5px;
  color: #9aa3b1;
  line-height: 1.7;
}

@media (max-width: 860px) {
  .kb-panel {
    width: 100%;
  }
}
</style>
