<script setup>
import { reactive, ref } from 'vue'
import { generateReport } from '../api/loveMaster.js'

const form = reactive({
  name: '',
  gender: '女',
  situation: '',
  partnerInfo: '',
  conversationId: 'report-' + Math.random().toString(36).slice(2, 8)
})

const loading = ref(false)
const error = ref('')
const report = ref(null)

async function submit() {
  error.value = ''
  if (!form.name.trim() || !form.situation.trim()) {
    error.value = '请填写昵称和当前感情状况'
    return
  }
  loading.value = true
  report.value = null
  try {
    report.value = await generateReport({ ...form })
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function reset() {
  report.value = null
  error.value = ''
}

function scoreStyle(score) {
  const s = Number.isFinite(score) ? score : 0
  const angle = (s / 100) * 360
  return {
    background: `conic-gradient(var(--pink) ${angle}deg, #f0e8f2 0deg)`
  }
}
</script>

<template>
  <section class="report-wrap">
    <form v-if="!report" class="card form" @submit.prevent="submit">
      <h2 class="form-title">💌 生成专属恋爱报告</h2>

      <div class="field">
        <label>昵称</label>
        <input v-model="form.name" placeholder="怎么称呼你？" maxlength="20" />
      </div>

      <div class="field">
        <label>性别</label>
        <div class="radio-row">
          <label v-for="g in ['女', '男', '其他']" :key="g" class="radio">
            <input v-model="form.gender" type="radio" :value="g" />
            <span>{{ g }}</span>
          </label>
        </div>
      </div>

      <div class="field">
        <label>当前感情状况</label>
        <textarea
          v-model="form.situation"
          rows="3"
          placeholder="例如：和对象异地一年，最近联系变少，担心感情变淡…"
        ></textarea>
      </div>

      <div class="field">
        <label>对方情况（选填）</label>
        <textarea
          v-model="form.partnerInfo"
          rows="2"
          placeholder="例如：性格内向，工作较忙，不太主动表达…"
        ></textarea>
      </div>

      <p v-if="error" class="error">{{ error }}</p>

      <button class="submit-btn" :disabled="loading">
        {{ loading ? '小爱正在分析中…' : '生成恋爱报告' }}
      </button>
    </form>

    <div v-else class="card result">
      <div class="result-head">
        <div>
          <h2>{{ form.name }} 的恋爱报告</h2>
          <p class="summary">{{ report.summary }}</p>
        </div>
        <div class="score-ring" :style="scoreStyle(report.compatibilityScore)">
          <span class="score-num">{{ report.compatibilityScore ?? '—' }}</span>
          <small>匹配度</small>
        </div>
      </div>

      <div class="section">
        <h3>🧠 性格与相处分析</h3>
        <p>{{ report.personalityAnalysis }}</p>
      </div>

      <div class="section">
        <h3>💬 沟通技巧</h3>
        <p>{{ report.communicationTips }}</p>
      </div>

      <div class="section">
        <h3>❤️ 关系发展建议</h3>
        <p>{{ report.relationshipAdvice }}</p>
      </div>

      <div class="section">
        <h3>✅ 行动计划</h3>
        <ul class="plan">
          <li v-for="(item, i) in report.actionPlan" :key="i">{{ item }}</li>
        </ul>
      </div>

      <blockquote class="warm">{{ report.warmMessage }}</blockquote>

      <button class="submit-btn ghost" @click="reset">重新生成</button>
    </div>
  </section>
</template>

<style scoped>
.report-wrap {
  height: 100%;
  overflow-y: auto;
  padding-bottom: 8px;
}

.card {
  background: var(--card);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 24px;
}

.form-title {
  font-size: 19px;
  margin-bottom: 18px;
  color: var(--pink-deep);
}

.field {
  margin-bottom: 16px;
}

.field > label {
  display: block;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 7px;
  color: var(--text);
}

.field input[type='text'],
.field input:not([type]),
.field textarea {
  width: 100%;
  padding: 11px 14px;
  border-radius: 12px;
  border: 1.5px solid #efe6f0;
  font-size: 15px;
  transition: border 0.2s;
}

.field input:focus,
.field textarea:focus {
  border-color: var(--pink);
}

.field textarea {
  resize: vertical;
  line-height: 1.55;
}

.radio-row {
  display: flex;
  gap: 18px;
}

.radio {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  cursor: pointer;
}

.radio input {
  accent-color: var(--pink);
}

.error {
  color: var(--pink-deep);
  font-size: 14px;
  margin-bottom: 12px;
}

.submit-btn {
  width: 100%;
  padding: 13px;
  border-radius: 14px;
  background: linear-gradient(90deg, var(--pink), var(--purple));
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  margin-top: 6px;
}

.submit-btn.ghost {
  background: #fff;
  color: var(--pink-deep);
  border: 1.5px solid var(--pink-soft);
  margin-top: 20px;
}

.result-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding-bottom: 18px;
  border-bottom: 1px dashed #eee0ea;
  margin-bottom: 18px;
}

.result-head h2 {
  font-size: 20px;
  color: var(--pink-deep);
}

.summary {
  margin-top: 6px;
  color: var(--text-soft);
  font-size: 14px;
  line-height: 1.5;
}

.score-ring {
  width: 88px;
  height: 88px;
  flex-shrink: 0;
  border-radius: 50%;
  display: grid;
  place-items: center;
  position: relative;
}

.score-ring::before {
  content: '';
  position: absolute;
  inset: 8px;
  background: #fff;
  border-radius: 50%;
}

.score-num {
  position: relative;
  font-size: 26px;
  font-weight: 700;
  color: var(--pink-deep);
  line-height: 1;
}

.score-ring small {
  position: relative;
  font-size: 11px;
  color: var(--text-soft);
}

.section {
  margin-bottom: 18px;
}

.section h3 {
  font-size: 15px;
  margin-bottom: 8px;
}

.section p {
  font-size: 14.5px;
  line-height: 1.7;
  color: #4a4552;
}

.plan {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.plan li {
  position: relative;
  padding: 10px 14px 10px 36px;
  background: var(--pink-soft);
  border-radius: 12px;
  font-size: 14.5px;
  line-height: 1.5;
}

.plan li::before {
  content: '💗';
  position: absolute;
  left: 12px;
  top: 10px;
}

.warm {
  margin: 6px 0;
  padding: 14px 18px;
  background: linear-gradient(90deg, #fff0f6, #f3ecff);
  border-left: 4px solid var(--pink);
  border-radius: 10px;
  font-size: 15px;
  color: var(--pink-deep);
  line-height: 1.6;
}
</style>
