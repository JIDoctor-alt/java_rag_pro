<script setup>
import { onMounted, ref } from 'vue'
import ChatView from './components/ChatView.vue'
import ReportView from './components/ReportView.vue'
import KnowledgeChapterView from './components/KnowledgeChapterView.vue'
import { fetchPaymentOrder } from './api/loveMaster.js'

const activeTab = ref('chat')
const paymentNotice = ref(null)

const tabs = [
  { key: 'chat', label: '恋爱对话', icon: '💬' },
  { key: 'knowledge', label: '知识库', icon: '📚' },
  { key: 'report', label: '恋爱报告', icon: '📊' }
]

onMounted(async () => {
  const params = new URLSearchParams(window.location.search)
  const payment = params.get('payment')
  const sessionId = params.get('session_id')
  if (payment === 'success' && sessionId) {
    try {
      const order = await fetchPaymentOrder(sessionId)
      paymentNotice.value = {
        type: 'success',
        text: `支付成功！已购买《${order.courseName}》`
      }
    } catch {
      paymentNotice.value = { type: 'success', text: '支付成功，感谢你的购买！' }
    }
    activeTab.value = 'knowledge'
  } else if (payment === 'cancel') {
    paymentNotice.value = { type: 'cancel', text: '已取消支付，可随时回来继续选购' }
    activeTab.value = 'knowledge'
  }
  if (payment) {
    window.history.replaceState({}, '', window.location.pathname)
  }
})

function dismissNotice() {
  paymentNotice.value = null
}
</script>

<template>
  <div class="app" :class="{ wide: activeTab === 'knowledge' }">
    <header class="app-header">
      <div class="brand">
        <span class="brand-avatar">💕</span>
        <div class="brand-text">
          <h1>AI 恋爱大师</h1>
          <p>你的专属情感顾问「小爱」</p>
        </div>
      </div>
      <nav class="tabs">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="tab"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          <span>{{ tab.icon }}</span>
          {{ tab.label }}
        </button>
      </nav>
    </header>

    <div v-if="paymentNotice" class="payment-notice" :class="paymentNotice.type">
      <span>{{ paymentNotice.text }}</span>
      <button @click="dismissNotice">知道了</button>
    </div>

    <main class="app-main">
      <ChatView v-show="activeTab === 'chat'" />
      <KnowledgeChapterView v-show="activeTab === 'knowledge'" />
      <ReportView v-show="activeTab === 'report'" />
    </main>
  </div>
</template>

<style scoped>
.app {
  max-width: 860px;
  margin: 0 auto;
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 20px 16px;
}

.app.wide {
  max-width: 1100px;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 14px;
  margin-bottom: 16px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-avatar {
  font-size: 38px;
  width: 58px;
  height: 58px;
  display: grid;
  place-items: center;
  background: var(--card);
  border-radius: 50%;
  box-shadow: var(--shadow);
}

.brand-text h1 {
  font-size: 22px;
  background: linear-gradient(90deg, var(--pink-deep), var(--purple));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.brand-text p {
  font-size: 13px;
  color: var(--text-soft);
  margin-top: 2px;
}

.tabs {
  display: flex;
  gap: 8px;
  background: var(--card);
  padding: 6px;
  border-radius: 999px;
  box-shadow: var(--shadow);
}

.tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 18px;
  border-radius: 999px;
  background: transparent;
  color: var(--text-soft);
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s;
}

.tab.active {
  background: linear-gradient(90deg, var(--pink), var(--purple));
  color: #fff;
  box-shadow: 0 4px 14px rgba(255, 107, 157, 0.4);
}

.app-main {
  flex: 1;
  min-height: 0;
}

.payment-notice {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
}

.payment-notice.success {
  background: #eefbf3;
  color: #1f7a45;
  border: 1px solid #bfe8cf;
}

.payment-notice.cancel {
  background: #fff8ee;
  color: #9a6b1f;
  border: 1px solid #f3ddb0;
}

.payment-notice button {
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.8);
  font-size: 13px;
}
</style>
