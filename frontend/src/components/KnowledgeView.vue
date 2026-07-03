<script setup>
import { onMounted, ref } from 'vue'
import { askKnowledge, knowledgeStats } from '../api/loveMaster.js'

const conversationId = ref('knowledge-' + Math.random().toString(36).slice(2, 8))
const question = ref('')
const loading = ref(false)
const error = ref('')
const answer = ref(null)
const stats = ref(null)

const suggestions = [
  '异地恋怎么维持感情？',
  '和对象经常吵架怎么办？',
  '第一次约会应该聊什么？',
  '暗恋的人怎么表白？',
  '有没有适合的恋爱课程推荐？'
]

onMounted(async () => {
  try {
    stats.value = await knowledgeStats()
  } catch {
    /* backend may be offline */
  }
})

async function ask(text) {
  const q = (text ?? question.value).trim()
  if (!q || loading.value) return

  question.value = ''
  loading.value = true
  error.value = ''
  answer.value = null

  try {
    answer.value = await askKnowledge({ question: q, conversationId: conversationId.value })
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="knowledge card">
    <div class="header">
      <h2>📚 定制化恋爱知识问答</h2>
      <p class="subtitle">私有知识库 RAG + 课程推荐 · 解决困惑 · 引导变现</p>
      <div v-if="stats" class="stats">
        <span>来源：{{ stats.source === 'local' ? '本地向量库' : stats.source }}</span>
        <span>分类：{{ stats.category }}</span>
        <span>RAG：{{ stats.enabled ? '已启用' : '未启用' }}</span>
      </div>
    </div>

    <div v-if="!answer && !loading" class="suggestions">
      <button v-for="(s, i) in suggestions" :key="i" class="chip" @click="ask(s)">
        {{ s }}
      </button>
    </div>

    <div v-if="loading" class="loading">
      <span class="dot"></span><span class="dot"></span><span class="dot"></span>
      正在检索知识库并生成回答…
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="answer" class="result">
      <div class="qa-block">
        <div class="label">❓ 你的问题</div>
        <p>{{ answer.question }}</p>
      </div>

      <div class="qa-block answer-block">
        <div class="label">💕 小爱的回答</div>
        <p>{{ answer.answer }}</p>
      </div>

      <div v-if="answer.sources?.length" class="sources">
        <div class="label">🔍 检索到的知识片段（{{ answer.sources.length }} 条）</div>
        <div v-for="(src, i) in answer.sources" :key="i" class="source-card">
          <div class="source-head">
            <span class="source-title">{{ src.title }}</span>
            <span class="source-score">相似度 {{ (src.score * 100).toFixed(0) }}%</span>
          </div>
          <p class="source-content">{{ src.content }}</p>
        </div>
      </div>

      <div v-if="answer.recommendedCourses?.length" class="courses">
        <div class="label">🎓 为你推荐的课程/服务</div>
        <div v-for="course in answer.recommendedCourses" :key="course.id" class="course-card">
          <div class="course-head">
            <span class="course-name">{{ course.name }}</span>
            <span class="course-price">{{ course.price }}</span>
          </div>
          <p class="course-desc">{{ course.description }}</p>
          <p class="course-reason">{{ course.reason }}</p>
          <a v-if="course.url" class="course-link" :href="course.url" target="_blank">了解详情 →</a>
        </div>
      </div>
    </div>

    <div class="composer">
      <input
        v-model="question"
        placeholder="输入恋爱相关问题，基于知识库为你解答…"
        :disabled="loading"
        @keydown.enter="ask()"
      />
      <button class="send-btn" :disabled="loading || !question.trim()" @click="ask()">
        {{ loading ? '检索中' : '提问' }}
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

.knowledge {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.header {
  padding: 20px 20px 12px;
  border-bottom: 1px solid #f0eaf2;
}

.header h2 {
  font-size: 18px;
  color: var(--pink-deep);
}

.subtitle {
  font-size: 13px;
  color: var(--text-soft);
  margin-top: 4px;
}

.stats {
  display: flex;
  gap: 12px;
  margin-top: 10px;
  flex-wrap: wrap;
}

.stats span {
  font-size: 12px;
  padding: 4px 10px;
  background: var(--pink-soft);
  border-radius: 999px;
  color: var(--pink-deep);
}

.suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 16px 20px;
}

.chip {
  padding: 8px 14px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid var(--pink-soft);
  color: var(--pink-deep);
  font-size: 13px;
}

.chip:hover {
  background: var(--pink-soft);
}

.loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 20px;
  color: var(--text-soft);
  font-size: 14px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--pink);
  animation: blink 1.2s infinite ease-in-out;
}

.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }

@keyframes blink {
  0%, 80%, 100% { opacity: 0.3; }
  40% { opacity: 1; }
}

.error {
  color: var(--pink-deep);
  padding: 0 20px 12px;
  font-size: 14px;
}

.result {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
}

.qa-block {
  margin-bottom: 16px;
}

.label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-soft);
  margin-bottom: 6px;
}

.qa-block p {
  font-size: 15px;
  line-height: 1.65;
}

.answer-block p {
  padding: 12px 14px;
  background: var(--pink-soft);
  border-radius: 12px;
}

.sources {
  margin-top: 8px;
}

.source-card {
  margin-top: 10px;
  padding: 12px 14px;
  background: #faf7fb;
  border: 1px solid #efe6f0;
  border-radius: 12px;
}

.source-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.source-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--purple);
}

.source-score {
  font-size: 12px;
  color: var(--text-soft);
}

.source-content {
  font-size: 13px;
  line-height: 1.55;
  color: #5a5562;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.courses {
  margin-top: 16px;
}

.course-card {
  margin-top: 10px;
  padding: 14px 16px;
  background: linear-gradient(135deg, #fff5f8, #f3ecff);
  border: 1.5px solid #f0d4e4;
  border-radius: 14px;
}

.course-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.course-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--pink-deep);
}

.course-price {
  font-size: 14px;
  font-weight: 700;
  color: var(--purple);
}

.course-desc {
  font-size: 13px;
  line-height: 1.55;
  color: #5a5562;
  margin-bottom: 6px;
}

.course-reason {
  font-size: 12px;
  color: var(--text-soft);
  margin-bottom: 8px;
}

.course-link {
  font-size: 13px;
  font-weight: 600;
  color: var(--pink-deep);
  text-decoration: none;
}

.course-link:hover {
  text-decoration: underline;
}

.composer {
  display: flex;
  gap: 10px;
  padding: 14px 16px;
  border-top: 1px solid #f0eaf2;
}

.composer input {
  flex: 1;
  padding: 11px 14px;
  border-radius: 14px;
  border: 1.5px solid #efe6f0;
  font-size: 15px;
}

.composer input:focus {
  border-color: var(--pink);
}

.send-btn {
  padding: 11px 22px;
  border-radius: 14px;
  background: linear-gradient(90deg, var(--pink), var(--purple));
  color: #fff;
  font-weight: 600;
  white-space: nowrap;
}
</style>
