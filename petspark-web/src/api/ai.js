import http from './http'
import store from '@/store'

/**
 * AI 对话相关接口（API-AI-001~006）。
 * 非流式调用走 axios；流式 {@link streamAiMessage} 用 fetch + ReadableStream 解析 SSE，
 * 因为 EventSource 只支持 GET 且不能自定义 Authorization 头。
 */

export function getAiStatus() {
  return http.get('/api/v1/ai/status')
}

export function grantAiConsent(payload) {
  return http.put('/api/v1/ai/consent', payload)
}

export function withdrawAiConsent() {
  return http.delete('/api/v1/ai/consent')
}

export function createAiConversation(payload) {
  return http.post('/api/v1/ai/conversations', payload)
}

export function listAiConversations() {
  return http.get('/api/v1/ai/conversations')
}

export function sendAiMessage(conversationId, message) {
  return http.post(`/api/v1/ai/conversations/${conversationId}/messages`, { message })
}

/**
 * 护理问答场景可用性查询（PR-AI-04 / API-AI-004c）。
 * 返回 { enabled: boolean, scene: 'CARE_QA' }。
 */
export function getCareQaStatus() {
  return http.get('/api/v1/ai/care-qa/status')
}

/**
 * 护理问答非流式发送（PR-AI-04 / API-AI-004b）。返回结构化 CareQaReplyView：
 * { requestId, riskLevel, generalAdvice, warningSigns, seekHelp, disclaimer, usage }。
 */
export function sendCareQaMessage(conversationId, message) {
  return http.post(`/api/v1/ai/conversations/${conversationId}/messages:care-qa`, { message })
}

export function deleteAiConversation(conversationId) {
  return http.delete(`/api/v1/ai/conversations/${conversationId}`)
}

export function listAiMessages(conversationId) {
  return http.get(`/api/v1/ai/conversations/${conversationId}/messages`)
}

/**
 * 真实候选智能推荐（API-AI-007）。
 * 服务端检索真实可见候选喂给模型排序+理由，再对模型输出做服务端再校验。
 * AI 未启用或模型失败时走规则兜底排序。
 *
 * @param {object} payload { species, age, preference, candidateType, petId? }
 * @returns {Promise<{data: {requestId, items: [{id, type, reason}], usage, boundaryNotice}}>}
 */
export function recommendAi(payload) {
  return http.post('/api/v1/ai/recommend', payload, { timeout: 40000 })
}

/**
 * 流式发送消息。返回一个 controller 句柄，调用 .abort() 可取消流式（对应"流式取消"）。
 *
 * @param {string} conversationId 会话 ID
 * @param {string} message 用户消息文本
 * @param {object} handlers { onMeta, onDelta, onUsage, onDone, onError }
 * @returns {AbortController} 可调用 .abort() 取消
 */
export function streamAiMessage(conversationId, message, handlers = {}) {
  const controller = new AbortController()
  const nativeAbort = controller.abort.bind(controller)
  const responseTimeoutMs = 40000
  let settled = false
  let timeoutId
  const finish = (type, payload) => {
    if (settled) return
    settled = true
    if (timeoutId) clearTimeout(timeoutId)
    if (type === 'done' && handlers.onDone) handlers.onDone(payload)
    if (type === 'error' && handlers.onError) handlers.onError(payload)
  }
  timeoutId = setTimeout(() => {
    nativeAbort()
    finish('error', 'AI 响应超时，请稍后重试')
  }, responseTimeoutMs)
  controller.abort = () => {
    if (settled) return
    settled = true
    if (timeoutId) clearTimeout(timeoutId)
    nativeAbort()
  }
  const token = store.state.accessToken
  const baseUrl = process.env.VUE_APP_API_BASE_URL || ''
  fetch(`${baseUrl}/api/v1/ai/conversations/${conversationId}/messages:stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    credentials: 'include',
    signal: controller.signal,
    body: JSON.stringify({ message })
  })
    .then((response) => {
      if (!response.ok || !response.body) {
        // 非 2xx：仍尝试解析错误信封，回退到状态文本。
        return response.text().then((text) => {
          let message = `请求失败（${response.status}）`
          try {
            const body = JSON.parse(text)
            if (body && body.message) message = body.message
          } catch (_) { /* ignore */ }
          throw new Error(message)
        })
      }
      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''
      const dispatch = parseSseChunks()
      const eventHandlers = {
        ...handlers,
        onDone: (payload) => finish('done', payload),
        onError: (message) => finish('error', message)
      }
      function pump() {
        reader.read().then(({ done, value }) => {
          if (done) {
            buffer += decoder.decode()
            if (buffer) dispatch(buffer, eventHandlers)
            finish('done')
            return
          }
          buffer += decoder.decode(value, { stream: true })
          const { rest } = dispatch(buffer, eventHandlers)
          buffer = rest
          pump()
        }).catch((err) => {
          if (err && err.name === 'AbortError') return
          finish('error', err.message || '流式中断')
        })
      }
      pump()
    })
    .catch((err) => {
      if (err && err.name === 'AbortError') return
      finish('error', err.message || '流式连接失败')
    })
  return controller
}

/**
 * 极简 SSE 解析器：按 \n\n 分块，每块取 event: 与 data: 行。
 * 返回一个函数 (buffer, handlers) => { rest }；handlers 被调用对应事件。
 */
function parseSseChunks() {
  return (buffer, handlers) => {
    let rest = buffer.replace(/\r\n/g, '\n')
    const sep = '\n\n'
    let idx
    while ((idx = rest.indexOf(sep)) !== -1) {
      const chunk = rest.slice(0, idx)
      rest = rest.slice(idx + sep.length)
      let event = 'message'
      let dataLines = []
      chunk.split('\n').forEach((line) => {
        if (line.startsWith('event:')) event = line.slice(6).trim()
        else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
      })
      const data = dataLines.join('\n')
      let payload = data
      try { payload = data ? JSON.parse(data) : {} } catch (_) { /* keep raw */ }
      if (event === 'meta' && handlers.onMeta) handlers.onMeta(payload)
      else if (event === 'delta' && handlers.onDelta) handlers.onDelta(payload)
      else if (event === 'usage' && handlers.onUsage) handlers.onUsage(payload)
      else if (event === 'done' && handlers.onDone) handlers.onDone(payload)
      else if (event === 'error' && handlers.onError) {
        const msg = (payload && payload.message) || 'AI 流式错误'
        handlers.onError(msg)
      }
    }
    return { rest }
  }
}
