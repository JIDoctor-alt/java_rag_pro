<script setup>
import { nextTick, ref } from 'vue'
import { chatStream } from '../api/loveMaster.js'

const conversationId = ref('user-' + Math.random().toString(36).slice(2, 8))
const input = ref('')
const sending = ref(false)
const messages = ref([
  {
    role: 'assistant',
    content: '嗨～我是小爱 💕 无论是心动、暧昧、争吵还是复合，都可以和我聊聊。说说你的困扰吧？'
  }
])
const listRef = ref(null)

const suggestions = [
  '暗恋同事很久了，该怎么开口？',
  '和对象异地，最近联系变少了',
  '第一次约会该聊些什么？',
  '吵架后应该谁先低头？'
]

async function scrollToBottom() {
  await nextTick()
  const el = listRef.value
  if (el) el.scrollTop = el.scrollHeight
}

async function send(text) {
  const content = (text ?? input.value).trim()
  if (!content || sending.value) return

  messages.value.push({ role: 'user', content })
  input.value = ''
  sending.value = true

  const reply = { role: 'assistant', content: '' }
  messages.value.push(reply)
  await scrollToBottom()

  try {
    await chatStream(
      { message: content, conversationId: conversationId.value },
      (chunk) => {
        reply.content += chunk
        scrollToBottom()
      }
    )
    if (!reply.content) {
      reply.content = '（小爱暂时没有回应，请稍后再试）'
    }
  } catch (e) {
    reply.content = `😢 出错了：${e.message}`
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

function handleKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}
</script>

<template>
  <section class="chat card">
    <div ref="listRef" class="messages">
      <div
        v-for="(msg, idx) in messages"
        :key="idx"
        class="msg"
        :class="msg.role"
      >
        <div class="avatar">{{ msg.role === 'user' ? '🧑' : '💕' }}</div>
        <div class="bubble">
          <span v-if="msg.content">{{ msg.content }}</span>
          <span v-else class="typing"><i></i><i></i><i></i></span>
        </div>
      </div>
    </div>

    <div v-if="messages.length <= 1" class="suggestions">
      <button
        v-for="(s, i) in suggestions"
        :key="i"
        class="chip"
        @click="send(s)"
      >
        {{ s }}
      </button>
    </div>

    <div class="composer">
      <textarea
        v-model="input"
        rows="1"
        placeholder="说说你的心事…（Enter 发送，Shift+Enter 换行）"
        :disabled="sending"
        @keydown="handleKeydown"
      ></textarea>
      <button class="send-btn" :disabled="sending || !input.trim()" @click="send()">
        {{ sending ? '思考中' : '发送' }}
      </button>
    </div>
  </section>
</template>

<style scoped>
.card {
  background: var(--card);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}

.chat {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.msg {
  display: flex;
  gap: 10px;
  max-width: 82%;
}

.msg.user {
  flex-direction: row-reverse;
  align-self: flex-end;
}

.avatar {
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--pink-soft);
  font-size: 20px;
}

.bubble {
  padding: 11px 15px;
  border-radius: 16px;
  line-height: 1.65;
  font-size: 15px;
  white-space: pre-wrap;
  word-break: break-word;
}

.msg.assistant .bubble {
  background: var(--pink-soft);
  border-top-left-radius: 4px;
  color: var(--text);
}

.msg.user .bubble {
  background: linear-gradient(90deg, var(--pink), var(--purple));
  border-top-right-radius: 4px;
  color: #fff;
}

.typing {
  display: inline-flex;
  gap: 4px;
  align-items: center;
}

.typing i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--pink);
  animation: blink 1.2s infinite ease-in-out;
}

.typing i:nth-child(2) {
  animation-delay: 0.2s;
}

.typing i:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes blink {
  0%, 80%, 100% { opacity: 0.3; transform: translateY(0); }
  40% { opacity: 1; transform: translateY(-4px); }
}

.suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 0 20px 12px;
}

.chip {
  padding: 8px 14px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid var(--pink-soft);
  color: var(--pink-deep);
  font-size: 13px;
  transition: all 0.2s;
}

.chip:hover {
  background: var(--pink-soft);
}

.composer {
  display: flex;
  gap: 10px;
  padding: 14px 16px;
  border-top: 1px solid #f0eaf2;
  align-items: flex-end;
}

.composer textarea {
  flex: 1;
  resize: none;
  max-height: 120px;
  padding: 11px 14px;
  border-radius: 14px;
  border: 1.5px solid #efe6f0;
  font-size: 15px;
  line-height: 1.5;
  transition: border 0.2s;
}

.composer textarea:focus {
  border-color: var(--pink);
}

.send-btn {
  padding: 11px 22px;
  border-radius: 14px;
  background: linear-gradient(90deg, var(--pink), var(--purple));
  color: #fff;
  font-weight: 600;
  font-size: 15px;
  white-space: nowrap;
}
</style>
