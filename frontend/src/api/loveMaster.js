const BASE = '/api/love-master'

async function handleJson(res) {
  if (!res.ok) {
    throw new Error(`请求失败：HTTP ${res.status}`)
  }
  const result = await res.json()
  if (result.code !== 0) {
    throw new Error(result.message || '服务返回错误')
  }
  return result.data
}

export function chat({ message, conversationId, imageUrl }) {
  return fetch(`${BASE}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, conversationId, imageUrl })
  }).then(handleJson)
}

/**
 * 流式对话：读取后端 text/plain 分块响应
 */
export async function chatStream({ message, conversationId }, onChunk, signal) {
  const res = await fetch(`${BASE}/chat/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, conversationId }),
    signal
  })
  if (!res.ok || !res.body) {
    throw new Error(`流式请求失败：HTTP ${res.status}`)
  }

  const contentType = res.headers.get('content-type') || ''
  const reader = res.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let full = ''
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    if (contentType.includes('text/event-stream')) {
      const parsed = drainSseBuffer(buffer)
      buffer = parsed.rest
      if (parsed.text) {
        full += parsed.text
        onChunk?.(parsed.text)
      }
    } else {
      full += buffer
      onChunk?.(buffer)
      buffer = ''
    }
  }

  if (buffer) {
    if (contentType.includes('text/event-stream')) {
      const text = parseSseBlock(buffer)
      if (text) {
        full += text
        onChunk?.(text)
      }
    } else {
      full += buffer
      onChunk?.(buffer)
    }
  }
  return full
}

function drainSseBuffer(buffer) {
  let text = ''
  let rest = buffer
  let idx
  while ((idx = rest.indexOf('\n\n')) !== -1) {
    const block = rest.slice(0, idx)
    rest = rest.slice(idx + 2)
    const part = parseSseBlock(block)
    if (part) text += part
  }
  return { text, rest }
}

function parseSseBlock(block) {
  const lines = block.split('\n').filter((line) => line.startsWith('data:'))
  if (!lines.length) return ''
  return lines.map((line) => line.slice(5).replace(/^ /, '')).join('\n')
}

export function generateReport(payload) {
  return fetch(`${BASE}/report`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  }).then(handleJson)
}

export function askKnowledge({ question, conversationId, docId, docType }) {
  return fetch(`${BASE}/knowledge/ask`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question, conversationId, docId, docType })
  }).then(handleJson)
}

export function fetchKnowledgeTree() {
  return fetch(`${BASE}/knowledge/tree`).then(handleJson)
}

export function fetchChapter(docId, sectionIndex) {
  const params = new URLSearchParams({ docId })
  if (sectionIndex != null) {
    params.set('sectionIndex', String(sectionIndex))
  }
  return fetch(`${BASE}/knowledge/chapter?${params}`).then(handleJson)
}

export function updateChapter({ docId, sectionIndex, content }) {
  return fetch(`${BASE}/knowledge/chapter`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ docId, sectionIndex, content })
  }).then(handleJson)
}

export function deleteChapter({ docId, sectionIndex }) {
  const params = new URLSearchParams({ docId })
  if (sectionIndex != null) {
    params.set('sectionIndex', String(sectionIndex))
  }
  return fetch(`${BASE}/knowledge/chapter?${params}`, { method: 'DELETE' }).then(handleJson)
}

export function searchKnowledge(query, { docId, docType } = {}) {
  const params = new URLSearchParams({ query })
  if (docId) params.set('docId', docId)
  if (docType) params.set('docType', docType)
  return fetch(`${BASE}/knowledge/search?${params}`).then(handleJson)
}

export function knowledgeStats() {
  return fetch(`${BASE}/knowledge/stats`).then(handleJson)
}

export function fetchPurchasableCourses() {
  return fetch(`${BASE}/payment/courses`).then(handleJson)
}

export function createCheckoutSession({ courseId, customerEmail }) {
  return fetch(`${BASE}/payment/checkout`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ courseId, customerEmail })
  }).then(handleJson)
}

export function fetchPaymentOrder(sessionId) {
  return fetch(`${BASE}/payment/orders/${encodeURIComponent(sessionId)}`).then(handleJson)
}

export function fetchDomesticChannels() {
  return fetch(`${BASE}/payment/domestic/channels`).then(handleJson)
}

export function createDomesticPayment({ courseId, channel, openId }) {
  return fetch(`${BASE}/payment/domestic/create`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ courseId, channel, openId })
  }).then(handleJson)
}

export function confirmMockPaid(outTradeNo) {
  return fetch(`${BASE}/payment/domestic/mock-paid/${encodeURIComponent(outTradeNo)}`, {
    method: 'POST'
  }).then(handleJson)
}

export function fetchWeChatOAuthUrl(state = 'love') {
  return fetch(`${BASE}/payment/wechat/oauth-url?state=${encodeURIComponent(state)}`).then(handleJson)
}

export function fetchOrderByTradeNo(outTradeNo) {
  return fetch(`${BASE}/payment/orders/trade/${encodeURIComponent(outTradeNo)}`).then(handleJson)
}
