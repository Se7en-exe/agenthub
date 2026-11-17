import axios from 'axios'

// 根据环境变量设置 API 基础 URL
const API_BASE_URL = process.env.NODE_ENV === 'production' 
 ? '/api' // 生产环境使用相对路径，适用于前后端部署在同一域名下
 : 'http://localhost:8123/api' // 开发环境指向本地后端服务

// 创建axios实例
const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000
})

// 封装SSE连接
export const connectSSE = (url, params, onMessage, onError) => {
  // 构建带参数的URL
  const queryString = Object.keys(params)
    .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
    .join('&')
  
  const fullUrl = `${API_BASE_URL}${url}?${queryString}`
  
  // 创建EventSource
  const eventSource = new EventSource(fullUrl)
  
  eventSource.onmessage = event => {
    let data = event.data
    
    // 检查是否是特殊标记
    if (data === '[DONE]') {
      if (onMessage) onMessage('[DONE]')
    } else {
      // 处理普通消息
      if (onMessage) onMessage(data)
    }
  }
  
  eventSource.onerror = error => {
    if (onError) onError(error)
    eventSource.close()
  }
  
  // 返回eventSource实例，以便后续可以关闭连接
  return eventSource
}

// 知识库助手聊天
export const chatWithKnowledgeAssistant = (message, chatId) => {
  return connectSSE('/ai/assistant/chat/rag/sse', { message, chatId })
}

// 自主智能体聊天
export const chatWithAgent = (message) => {
  return connectSSE('/ai/agent/chat', { message })
}

// 知识库文档列表
export const listKnowledge = () => request.get('/ai/knowledge/list')

// 上传一个文档进知识库（md / txt / pdf）
export const uploadKnowledge = (file) => {
  const form = new FormData()
  form.append('file', file)
  return request.post('/ai/knowledge/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    // 首次上传会触发本地向量模型加载，给足时间
    timeout: 180000
  })
}

// 从知识库删除一个文档
export const deleteKnowledge = (name) => request.delete('/ai/knowledge/delete', { params: { name } })

// 读取当前大模型配置（key 只回显打码后的样子）
export const getModelSettings = () => request.get('/ai/settings')

// 试连：不发保存，只验证这组参数能不能通
export const testModelSettings = (payload) => request.post('/ai/settings/test', payload || {}, { timeout: 30000 })

// 保存配置，后端会先试连再落盘
export const saveModelSettings = (payload) => request.post('/ai/settings', payload, { timeout: 30000 })

// 恢复为启动时的环境变量配置
export const resetModelSettings = () => request.post('/ai/settings/reset')

export default {
  chatWithKnowledgeAssistant,
  chatWithAgent,
  listKnowledge,
  uploadKnowledge,
  deleteKnowledge,
  getModelSettings,
  testModelSettings,
  saveModelSettings,
  resetModelSettings
}
