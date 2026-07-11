/**
 * @jest-environment node
 */
jest.mock('@/api/http', () => ({
  get: jest.fn(),
  post: jest.fn(),
  put: jest.fn(),
  delete: jest.fn()
}))
jest.mock('@/store', () => ({ state: { accessToken: 'test-token' } }))

import { ReadableStream } from 'node:stream/web'
import { TextEncoder, TextDecoder } from 'util'
globalThis.ReadableStream = ReadableStream
globalThis.TextEncoder = TextEncoder
globalThis.TextDecoder = TextDecoder
import http from '@/api/http'
import {
  getAiStatus,
  grantAiConsent,
  withdrawAiConsent,
  createAiConversation,
  sendAiMessage,
  sendCareQaMessage,
  getCareQaStatus,
  deleteAiConversation,
  listAiMessages,
  streamAiMessage,
  recommendAi
} from '@/api/ai'

function makeStreamResponse(chunks) {
  const encoder = new TextEncoder()
  const stream = new ReadableStream({
    start(controller) {
      chunks.forEach((c) => controller.enqueue(encoder.encode(c)))
      controller.close()
    }
  })
  // 仿造 WHATWG fetch 的 Response：.ok=true、.body=ReadableStream（含 getReader）、.text()、.status。
  return {
    ok: true,
    status: 200,
    body: stream,
    text: async () => chunks.join('')
  }
}

describe('ai API', () => {
  beforeEach(() => {
    http.get.mockReset()
    http.post.mockReset()
    http.put.mockReset()
    http.delete.mockReset()
    global.fetch = jest.fn()
  })

  it('getAiStatus hits /api/v1/ai/status', () => {
    getAiStatus()
    expect(http.get).toHaveBeenCalledWith('/api/v1/ai/status')
  })

  it('grantAiConsent PUTs /api/v1/ai/consent', () => {
    grantAiConsent({ policyVersion: 'v1', scopes: 'PET_CHAT' })
    expect(http.put).toHaveBeenCalledWith('/api/v1/ai/consent', { policyVersion: 'v1', scopes: 'PET_CHAT' })
  })

  it('withdrawAiConsent DELETEs /api/v1/ai/consent', () => {
    withdrawAiConsent()
    expect(http.delete).toHaveBeenCalledWith('/api/v1/ai/consent')
  })

  it('createAiConversation POSTs /api/v1/ai/conversations', () => {
    createAiConversation({ scene: 'PET_CHAT', title: 't' })
    expect(http.post).toHaveBeenCalledWith('/api/v1/ai/conversations', { scene: 'PET_CHAT', title: 't' })
  })

  it('sendAiMessage POSTs messages', () => {
    sendAiMessage('c-1', 'hi')
    expect(http.post).toHaveBeenCalledWith('/api/v1/ai/conversations/c-1/messages', { message: 'hi' })
  })

  it('deleteAiConversation DELETEs conversation', () => {
    deleteAiConversation('c-1')
    expect(http.delete).toHaveBeenCalledWith('/api/v1/ai/conversations/c-1')
  })

  it('listAiMessages GETs messages', () => {
    listAiMessages('c-1')
    expect(http.get).toHaveBeenCalledWith('/api/v1/ai/conversations/c-1/messages')
  })

  it('recommendAi POSTs /api/v1/ai/recommend', () => {
    const payload = { species: '狗', age: 36, preference: '活泼', candidateType: 'GOODS' }
    recommendAi(payload)
    expect(http.post).toHaveBeenCalledWith('/api/v1/ai/recommend', payload)
  })

  it('streamAiMessage parses SSE events and dispatches handlers', async () => {
    const chunks = [
      'event:meta\ndata:{"requestId":"r-1","model":"spark-x","scene":"PET_CHAT"}\n\n',
      'event:delta\ndata:{"content":"你好"}\n\n',
      'event:usage\ndata:{"promptTokens":10,"completionTokens":2,"totalTokens":12}\n\n',
      'event:done\ndata:{}\n\n'
    ]
    global.fetch.mockResolvedValue(makeStreamResponse(chunks))

    const handlers = {
      onMeta: jest.fn(),
      onDelta: jest.fn(),
      onUsage: jest.fn(),
      onDone: jest.fn(),
      onError: jest.fn()
    }
    const controller = streamAiMessage('c-1', '你好', handlers)
    expect(controller).toBeInstanceOf(AbortController)

    await flushPromises()
    expect(handlers.onMeta).toHaveBeenCalled()
    expect(handlers.onDelta).toHaveBeenCalledWith({ content: '你好' })
    expect(handlers.onUsage).toHaveBeenCalledWith({ promptTokens: 10, completionTokens: 2, totalTokens: 12 })
    expect(handlers.onDone).toHaveBeenCalled()
    expect(handlers.onError).not.toHaveBeenCalled()
  })

  it('streamAiMessage parses CRLF SSE frames and completes only once', async () => {
    const chunks = [
      'event:delta\r\ndata:{"content":"及时回复"}\r\n\r\n',
      'event:done\r\ndata:{}\r\n\r\n'
    ]
    global.fetch.mockResolvedValue(makeStreamResponse(chunks))
    const handlers = { onDelta: jest.fn(), onDone: jest.fn(), onError: jest.fn() }

    streamAiMessage('c-1', 'hello', handlers)
    await flushPromises()

    expect(handlers.onDelta).toHaveBeenCalledWith({ content: '及时回复' })
    expect(handlers.onDone).toHaveBeenCalledTimes(1)
    expect(handlers.onError).not.toHaveBeenCalled()
  })

  it('streamAiMessage dispatches error event', async () => {
    const chunks = ['event:error\ndata:{"code":"AI_DISABLED_001","message":"AI 服务未启用"}\n\n']
    global.fetch.mockResolvedValue(makeStreamResponse(chunks))
    const handlers = { onError: jest.fn(), onDone: jest.fn() }
    streamAiMessage('c-1', 'hello', handlers)
    await flushPromises()
    expect(handlers.onError).toHaveBeenCalledWith('AI 服务未启用')
  })

  it('streamAiMessage aborts and reports a friendly timeout instead of hanging forever', () => {
    jest.useFakeTimers()
    global.fetch.mockReturnValue(new Promise(() => {}))
    const handlers = { onError: jest.fn(), onDone: jest.fn() }
    const controller = streamAiMessage('c-timeout', 'hello', handlers)

    jest.advanceTimersByTime(40000)

    expect(controller.signal.aborted).toBe(true)
    expect(handlers.onError).toHaveBeenCalledWith('AI 响应超时，请稍后重试')
    expect(handlers.onDone).not.toHaveBeenCalled()
    jest.useRealTimers()
  })

  it('streamAiMessage includes Authorization header from store', async () => {
    global.fetch.mockResolvedValue(makeStreamResponse(['event:done\ndata:{}\n\n']))
    streamAiMessage('c-1', 'hello', {})
    await flushPromises()
    const [, init] = global.fetch.mock.calls[0]
    expect(init.headers.Authorization).toBe('Bearer test-token')
  })

  it('getCareQaStatus hits /api/v1/ai/care-qa/status', () => {
    getCareQaStatus()
    expect(http.get).toHaveBeenCalledWith('/api/v1/ai/care-qa/status')
  })

  it('sendCareQaMessage POSTs to care-qa endpoint', () => {
    sendCareQaMessage('c-9', '猫咪呕吐')
    expect(http.post).toHaveBeenCalledWith('/api/v1/ai/conversations/c-9/messages:care-qa', { message: '猫咪呕吐' })
  })
})

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}
