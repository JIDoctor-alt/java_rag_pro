<script setup>
import { ref } from 'vue'
import ChatView from './components/ChatView.vue'
import ReportView from './components/ReportView.vue'

const activeTab = ref('chat')

const tabs = [
  { key: 'chat', label: '恋爱对话', icon: '💬' },
  { key: 'report', label: '恋爱报告', icon: '📊' }
]
</script>

<template>
  <div class="app">
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

    <main class="app-main">
      <ChatView v-show="activeTab === 'chat'" />
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
</style>
