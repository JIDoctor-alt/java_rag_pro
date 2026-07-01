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

/**
 * 多轮对话（同步）
 */
export function chat({ message, conversationId, imageUrl }) {
  return fetch(`${BASE}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, conversationId, imageUrl })
  }).then(handleJson)
}

/**
 * 流式对话（SSE / text-event-stream）
 * @param {Function} onChunk 每接收到一段文本时回调
 * @returns {Promise<string>} 完整回复
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

  const reader = res.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let full = ''
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    const events = buffer.split('\n\n')
    buffer = events.pop() ?? ''
    for (const evt of events) {
      const text = parseSseData(evt)
      if (text) {
        full += text
        onChunk?.(text)
      }
    }
  }

  const tail = parseSseData(buffer)
  if (tail) {
    full += tail
    onChunk?.(tail)
  }
  return full
}

function parseSseData(chunk) {
  return chunk
    .split('\n')
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).replace(/^ /, ''))
    .join('')
}

/**
 * 生成恋爱报告（结构化输出）
 */
export function generateReport(payload) {
  return fetch(`${BASE}/report`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  }).then(handleJson)
}
