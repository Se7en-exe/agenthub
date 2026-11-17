<template>
  <div class="settings-root">
    <button class="settings-trigger" type="button" title="大模型设置" @click="open">
      模型设置
    </button>

    <Teleport to="body">
    <div v-if="visible" class="mask" @click.self="close">
      <div class="dialog" role="dialog" aria-modal="true">
        <header class="dialog-head">
          <div>
            <h3>大模型设置</h3>
            <p class="hint">密钥保存在本机 tmp/chat-settings.json，重启后仍然生效，不会写进前端代码。</p>
          </div>
          <button class="close" type="button" aria-label="关闭" @click="close">×</button>
        </header>

        <div class="dialog-body">
          <label class="field">
            <span class="label">API Key</span>
            <input
              v-model="form.apiKey"
              type="password"
              autocomplete="off"
              :placeholder="current.apiKeyMasked || '未配置，粘贴一个 sk- 开头的密钥'"
            />
            <span class="tip">
              当前：{{ current.apiKeyConfigured ? current.apiKeyMasked : '未配置' }}
              <button v-if="form.apiKey" class="link" type="button" @click="form.apiKey = ''">清空输入</button>
            </span>
          </label>

          <label class="field">
            <span class="label">Base URL</span>
            <input v-model="form.baseUrl" type="text" placeholder="https://api.deepseek.com" />
            <span class="tip">只要是 OpenAI 兼容的服务都能填，比如通义、Kimi、智谱的兼容地址。</span>
          </label>

          <div class="field-row">
            <label class="field">
              <span class="label">模型名</span>
              <input v-model="form.model" type="text" placeholder="deepseek-flash" />
            </label>
            <label class="field narrow">
              <span class="label">温度</span>
              <input v-model.number="form.temperature" type="number" min="0" max="2" step="0.1" />
            </label>
          </div>

          <p v-if="feedback" class="feedback" :class="feedbackType">{{ feedback }}</p>

          <div class="presets">
            <span class="presets-label">快速填入：</span>
            <button
              v-for="p in presets"
              :key="p.name"
              class="preset"
              type="button"
              @click="usePreset(p)"
            >{{ p.name }}</button>
          </div>
        </div>

        <footer class="dialog-foot">
          <button class="ghost" type="button" :disabled="busy" @click="reset">恢复默认</button>
          <div class="right">
            <button class="ghost" type="button" :disabled="busy" @click="test">测试连接</button>
            <button class="primary" type="button" :disabled="busy" @click="save">
              {{ busy ? '处理中…' : '保存并启用' }}
            </button>
          </div>
        </footer>
      </div>
    </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { getModelSettings, testModelSettings, saveModelSettings, resetModelSettings } from '../api'

const visible = ref(false)
const busy = ref(false)
const feedback = ref('')
const feedbackType = ref('info')

const current = reactive({
  apiKeyMasked: '',
  apiKeyConfigured: false,
  baseUrl: '',
  model: '',
  temperature: 0.7
})

const form = reactive({
  apiKey: '',
  baseUrl: '',
  model: '',
  temperature: 0.7
})

const presets = [
  { name: 'DeepSeek', baseUrl: 'https://api.deepseek.com', model: 'deepseek-flash' },
  { name: '智谱', baseUrl: 'https://open.bigmodel.cn/api/paas/v4', model: 'glm-4-flash' },
  { name: 'Kimi', baseUrl: 'https://api.moonshot.cn/v1', model: 'moonshot-v1-8k' }
]

const say = (text, type = 'info') => {
  feedback.value = text
  feedbackType.value = type
}

const fill = (data) => {
  current.apiKeyMasked = data.apiKeyMasked || ''
  current.apiKeyConfigured = !!data.apiKeyConfigured
  current.baseUrl = data.baseUrl || ''
  current.model = data.model || ''
  current.temperature = data.temperature ?? 0.7
  form.baseUrl = current.baseUrl
  form.model = current.model
  form.temperature = current.temperature
  form.apiKey = ''
}

const open = async () => {
  visible.value = true
  say('')
  try {
    const { data } = await getModelSettings()
    fill(data)
  } catch (e) {
    say('读取当前配置失败：' + (e.message || e), 'error')
  }
}

const close = () => {
  if (busy.value) return
  visible.value = false
}

const payload = () => ({
  apiKey: form.apiKey || undefined,
  baseUrl: form.baseUrl || undefined,
  model: form.model || undefined,
  temperature: form.temperature
})

const test = async () => {
  busy.value = true
  say('正在连接…')
  try {
    const { data } = await testModelSettings(payload())
    say(data.message || (data.ok ? '连接正常' : '连接失败'), data.ok ? 'ok' : 'error')
  } catch (e) {
    const detail = e.response?.data?.message || e.message || String(e)
    say('连接失败：' + detail, 'error')
  } finally {
    busy.value = false
  }
}

const save = async () => {
  if (!form.apiKey && !current.apiKeyConfigured) {
    say('请先填入 API Key', 'error')
    return
  }
  busy.value = true
  say('正在验证并保存…')
  try {
    const { data } = await saveModelSettings(payload())
    if (data.ok) {
      fill({ ...current, ...data, apiKeyConfigured: true })
      say(data.message || '已保存', 'ok')
    } else {
      say(data.message || '保存失败', 'error')
    }
  } catch (e) {
    const detail = e.response?.data?.message || e.message || String(e)
    say('保存失败：' + detail, 'error')
  } finally {
    busy.value = false
  }
}

const reset = async () => {
  busy.value = true
  say('正在恢复…')
  try {
    const { data } = await resetModelSettings()
    const { data: fresh } = await getModelSettings()
    fill(fresh)
    say(data.message || '已恢复默认', 'ok')
  } catch (e) {
    const detail = e.response?.data?.message || e.message || String(e)
    say('恢复失败：' + detail, 'error')
  } finally {
    busy.value = false
  }
}

const usePreset = (p) => {
  form.baseUrl = p.baseUrl
  form.model = p.model
  say('已填入 ' + p.name + ' 的地址和模型名，再填上这个服务商的 Key 就能测', 'info')
}
</script>

<style scoped>
.settings-trigger {
  border: 1px solid #e2e6ee;
  background: #fff;
  color: #4a5261;
  font-size: 13px;
  padding: 6px 13px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.18s;
}

.settings-trigger:hover {
  border-color: #c9d3e6;
  color: #1b2330;
  background: #f7f9fc;
}

.mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  background: rgba(16, 20, 24, 0.42);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.dialog {
  width: 100%;
  max-width: 560px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 24px 60px rgba(16, 20, 24, 0.22);
  display: flex;
  flex-direction: column;
  max-height: 90vh;
  overflow: hidden;
}

.dialog-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 22px 24px 14px;
  border-bottom: 1px solid #eef1f6;
}

.dialog-head h3 {
  margin: 0 0 6px;
  font-size: 17px;
  color: #101418;
}

.hint {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.65;
  color: #8b93a3;
}

.close {
  border: none;
  background: transparent;
  font-size: 22px;
  line-height: 1;
  color: #9aa3b1;
  cursor: pointer;
  padding: 0 2px;
}

.close:hover {
  color: #101418;
}

.dialog-body {
  padding: 18px 24px 8px;
  overflow-y: auto;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 16px;
  flex: 1;
}

.field.narrow {
  flex: 0 0 96px;
}

.field-row {
  display: flex;
  gap: 14px;
}

.label {
  font-size: 12.5px;
  color: #5b6472;
  font-weight: 500;
}

.field input {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #e2e6ee;
  border-radius: 9px;
  padding: 9px 12px;
  font-size: 13.5px;
  color: #101418;
  outline: none;
  transition: border-color 0.16s, box-shadow 0.16s;
  font-family: inherit;
}

.field input:focus {
  border-color: #2f6bd8;
  box-shadow: 0 0 0 3px rgba(47, 107, 216, 0.12);
}

.tip {
  font-size: 11.5px;
  color: #9aa3b1;
  display: flex;
  align-items: center;
  gap: 8px;
}

.link {
  border: none;
  background: none;
  padding: 0;
  font-size: 11.5px;
  color: #2f6bd8;
  cursor: pointer;
}

.feedback {
  margin: 0 0 12px;
  font-size: 12.5px;
  line-height: 1.6;
  padding: 9px 12px;
  border-radius: 9px;
  border: 1px solid transparent;
}

.feedback.info {
  background: #f5f7fb;
  border-color: #e6ebf4;
  color: #5b6472;
}

.feedback.ok {
  background: #f1faf5;
  border-color: #cfecdc;
  color: #147a4b;
}

.feedback.error {
  background: #fdf5f1;
  border-color: #f6ddd2;
  color: #c2410c;
}

.presets {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding-bottom: 8px;
}

.presets-label {
  font-size: 11.5px;
  color: #9aa3b1;
}

.preset {
  border: 1px solid #e2e6ee;
  background: #fff;
  color: #4a5261;
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 999px;
  cursor: pointer;
}

.preset:hover {
  border-color: #c9d3e6;
  color: #1b2330;
}

.dialog-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 24px 18px;
  border-top: 1px solid #eef1f6;
}

.right {
  display: flex;
  gap: 10px;
}

.ghost,
.primary {
  border-radius: 9px;
  font-size: 13px;
  padding: 8px 16px;
  cursor: pointer;
  transition: all 0.16s;
}

.ghost {
  border: 1px solid #e2e6ee;
  background: #fff;
  color: #4a5261;
}

.ghost:hover:not(:disabled) {
  border-color: #c9d3e6;
  color: #1b2330;
}

.primary {
  border: 1px solid #2f6bd8;
  background: #2f6bd8;
  color: #fff;
}

.primary:hover:not(:disabled) {
  background: #2559bd;
}

.ghost:disabled,
.primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
</style>
