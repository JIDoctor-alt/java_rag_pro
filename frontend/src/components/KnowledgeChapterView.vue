<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import {
  askKnowledge,
  confirmMockPaid,
  createCheckoutSession,
  createDomesticPayment,
  deleteChapter,
  fetchChapter,
  fetchDomesticChannels,
  fetchKnowledgeTree,
  fetchPurchasableCourses,
  fetchWeChatOAuthUrl,
  knowledgeStats,
  updateChapter
} from '../api/loveMaster.js'
import {
  getStoredOpenId,
  invokeWeChatJsapiPay,
  isWeChatBrowser,
  submitAlipayForm
} from '../utils/pay.js'

const conversationId = ref('knowledge-' + Math.random().toString(36).slice(2, 8))
const tree = ref([])
const expanded = ref(new Set())
const activeId = ref('')
const activeChapter = ref(null)
const stats = ref(null)
const loadingChapter = ref(false)
const purchasableCourses = ref({})
const buyingCourseId = ref('')
const buyError = ref('')
const showPayModal = ref(false)
const payChannels = ref([])
const pendingCourseId = ref('')

const chapterQuestion = ref('')
const asking = ref(false)
const askError = ref('')
const askResult = ref(null)

const editing = ref(false)
const editContent = ref('')
const saving = ref(false)
const saveMessage = ref('')
const saveError = ref('')

const contextMenu = ref({ visible: false, x: 0, y: 0, node: null })
const deleting = ref(false)
const deleteMessage = ref('')

const activeMeta = computed(() => {
  if (!activeChapter.value) return null
  return {
    docId: activeChapter.value.docId,
    docType: activeChapter.value.docType,
    chapterPath: activeChapter.value.chapterPath
  }
})

const currentCourseProduct = computed(() => {
  if (!activeChapter.value || activeChapter.value.docType !== 'course') return null
  return purchasableCourses.value[activeChapter.value.docId] || null
})

onMounted(async () => {
  document.addEventListener('click', closeContextMenu)
  try {
    const [treeData, statsData, courses] = await Promise.all([
      fetchKnowledgeTree(),
      knowledgeStats(),
      fetchPurchasableCourses().catch(() => [])
    ])
    tree.value = treeData
    stats.value = statsData
    purchasableCourses.value = Object.fromEntries(
      (courses || []).map((course) => [course.id, course])
    )
    for (const group of treeData) {
      expanded.value.add(group.id)
    }
    const first = findFirstSelectable(treeData)
    if (first) {
      await selectNode(first)
    }
  } catch (e) {
    console.error(e)
  }
})

onUnmounted(() => {
  document.removeEventListener('click', closeContextMenu)
})

function closeContextMenu() {
  contextMenu.value.visible = false
  contextMenu.value.node = null
}

function openContextMenu(event, node) {
  if (!node?.selectable) return
  event.preventDefault()
  event.stopPropagation()
  contextMenu.value = {
    visible: true,
    x: event.clientX,
    y: event.clientY,
    node
  }
}

function deleteTargetLabel(node) {
  if (!node) return ''
  return node.sectionIndex != null ? `小节「${node.title}」` : `文档「${node.title}」`
}

async function refreshTree() {
  const [treeData, statsData] = await Promise.all([
    fetchKnowledgeTree(),
    knowledgeStats().catch(() => stats.value)
  ])
  tree.value = treeData
  stats.value = statsData
  for (const group of treeData) {
    expanded.value.add(group.id)
  }
}

async function afterDelete(deletedNode) {
  cancelEdit()
  closeContextMenu()
  await refreshTree()
  const deletedWholeDoc = deletedNode.sectionIndex == null
  const sameDoc = activeChapter.value?.docId === deletedNode.docId
  const sameItem = activeId.value === deletedNode.id
  if (sameItem || (deletedWholeDoc && sameDoc)) {
    const next = findFirstSelectable(tree.value)
    activeId.value = ''
    activeChapter.value = null
    if (next) {
      await selectNode(next)
    }
  }
  deleteMessage.value = `${deleteTargetLabel(deletedNode)} 已删除`
}

async function confirmDelete(node) {
  if (!node || deleting.value) return
  const label = deleteTargetLabel(node)
  if (!window.confirm(`确定删除 ${label}？\n删除后将从知识库与 RAG 索引中移除，且不可恢复。`)) {
    closeContextMenu()
    return
  }
  deleting.value = true
  deleteMessage.value = ''
  saveError.value = ''
  try {
    await deleteChapter({
      docId: node.docId,
      sectionIndex: node.sectionIndex ?? null
    })
    await afterDelete(node)
  } catch (e) {
    saveError.value = e.message
  } finally {
    deleting.value = false
  }
}

async function deleteActiveChapter() {
  if (!activeChapter.value) return
  const node = {
    id: activeChapter.value.id,
    docId: activeChapter.value.docId,
    sectionIndex: activeChapter.value.sectionIndex ?? null,
    title: activeChapter.value.title,
    selectable: true
  }
  await confirmDelete(node)
}

function findFirstSelectable(nodes) {
  for (const node of nodes) {
    if (node.selectable) return node
    if (node.children?.length) {
      const found = findFirstSelectable(node.children)
      if (found) return found
    }
  }
  return null
}

function toggleExpand(id) {
  if (expanded.value.has(id)) {
    expanded.value.delete(id)
  } else {
    expanded.value.add(id)
  }
}

function isExpanded(id) {
  return expanded.value.has(id)
}

async function selectNode(node) {
  if (!node.selectable) return
  cancelEdit()
  activeId.value = node.id
  loadingChapter.value = true
  askResult.value = null
  askError.value = ''
  try {
    activeChapter.value = await fetchChapter(node.docId, node.sectionIndex ?? null)
  } catch (e) {
    activeChapter.value = null
    askError.value = e.message
  } finally {
    loadingChapter.value = false
  }
}

async function askInChapter() {
  const q = chapterQuestion.value.trim()
  if (!q || asking.value || !activeMeta.value) return

  asking.value = true
  askError.value = ''
  askResult.value = null

  try {
    askResult.value = await askKnowledge({
      question: q,
      conversationId: conversationId.value,
      docId: activeMeta.value.docId,
      docType: activeMeta.value.docType
    })
    chapterQuestion.value = ''
  } catch (e) {
    askError.value = e.message
  } finally {
    asking.value = false
  }
}

function docTypeLabel(type) {
  return { article: '文章', course: '课程', case: '案例', group: '分类' }[type] || type
}

async function buyCourse(courseId) {
  if (!courseId || buyingCourseId.value) return
  buyError.value = ''
  pendingCourseId.value = courseId

  try {
    const channels = await fetchDomesticChannels().catch(() => [])
    if (channels.length) {
      payChannels.value = channels
      showPayModal.value = true
      return
    }
    await payWithStripe(courseId)
  } catch (e) {
    buyError.value = e.message
  }
}

async function payWithStripe(courseId) {
  buyingCourseId.value = courseId
  try {
    const product = purchasableCourses.value[courseId]
    if (product?.paymentLinkUrl) {
      window.location.href = product.paymentLinkUrl
      return
    }
    const result = await createCheckoutSession({ courseId })
    if (result.checkoutUrl) {
      window.location.href = result.checkoutUrl
    } else {
      buyError.value = '未获取到支付链接'
    }
  } finally {
    buyingCourseId.value = ''
  }
}

async function payWithChannel(channel) {
  const courseId = pendingCourseId.value
  if (!courseId) return
  buyingCourseId.value = courseId
  buyError.value = ''
  showPayModal.value = false

  try {
    if (channel === 'WECHAT_JSAPI') {
      let openId = getStoredOpenId()
      if (!openId && isWeChatBrowser()) {
        const oauth = await fetchWeChatOAuthUrl(courseId)
        window.location.href = oauth.url
        return
      }
      if (!openId) {
        openId = 'mock_openid_dev'
      }
      const result = await createDomesticPayment({ courseId, channel, openId })
      if (result.mock) {
        await confirmMockPaid(result.outTradeNo)
        buyError.value = ''
        alert(`模拟支付成功：《${result.courseName}》`)
        return
      }
      await invokeWeChatJsapiPay(result.wechatJsapi)
      alert('支付成功！')
      return
    }

    if (channel === 'ALIPAY_WAP') {
      const result = await createDomesticPayment({ courseId, channel })
      if (result.mock) {
        await confirmMockPaid(result.outTradeNo)
        alert(`模拟支付成功：《${result.courseName}》`)
        return
      }
      submitAlipayForm(result.alipayForm)
      return
    }

    const result = await createDomesticPayment({ courseId, channel })
    if (result.mock) {
      await confirmMockPaid(result.outTradeNo)
      alert(`模拟 App 模拟支付成功：《${result.courseName}》\n参数已返回，可在原生 App 中接入 SDK。`)
      console.log('App pay payload', result.wechatApp || result.alipayOrderString)
      return
    }
    alert('请在原生 App 中使用返回的支付参数调起 SDK')
    console.log('App pay payload', result.wechatApp || result.alipayOrderString)
  } catch (e) {
    buyError.value = e.message
  } finally {
    buyingCourseId.value = ''
  }
}

function closePayModal() {
  showPayModal.value = false
}

function startEdit() {
  if (!activeChapter.value) return
  editing.value = true
  editContent.value = activeChapter.value.content
  saveMessage.value = ''
  saveError.value = ''
}

function cancelEdit() {
  editing.value = false
  editContent.value = ''
  saveMessage.value = ''
  saveError.value = ''
}

async function saveEdit() {
  if (!activeChapter.value || saving.value) return
  saving.value = true
  saveMessage.value = ''
  saveError.value = ''
  try {
    const updated = await updateChapter({
      docId: activeChapter.value.docId,
      sectionIndex: activeChapter.value.sectionIndex ?? null,
      content: editContent.value
    })
    activeChapter.value = updated
    if (activeChapter.value.sectionIndex != null) {
      updateTreeSectionTitle(activeChapter.value.docId, activeChapter.value.sectionIndex, updated.title)
    }
    editing.value = false
    saveMessage.value = '保存成功，RAG 索引已更新'
  } catch (e) {
    saveError.value = e.message
  } finally {
    saving.value = false
  }
}

function updateTreeSectionTitle(docId, sectionIndex, title) {
  for (const group of tree.value) {
    for (const doc of group.children || []) {
      if (doc.docId !== docId) continue
      for (const sec of doc.children || []) {
        if (sec.sectionIndex === sectionIndex) {
          sec.title = title
          return
        }
      }
    }
  }
}
</script>

<template>
  <section class="kb card">
    <aside class="sidebar">
      <div class="sidebar-head">
        <h2>📚 恋爱知识库</h2>
        <p v-if="stats" class="meta">{{ stats.documentCount }} 篇 · RAG {{ stats.enabled ? '已启用' : '未启用' }}</p>
      </div>
      <nav class="tree">
        <template v-for="group in tree" :key="group.id">
          <div class="tree-group">
            <button class="tree-row group-row" @click="toggleExpand(group.id)">
              <span class="arrow" :class="{ open: isExpanded(group.id) }">▶</span>
              <span>{{ group.title }}</span>
            </button>
            <div v-show="isExpanded(group.id)" class="tree-children">
              <template v-for="doc in group.children" :key="doc.id">
                <div
                  class="tree-item"
                  @contextmenu.prevent="openContextMenu($event, doc)"
                >
                  <button
                    class="tree-row doc-row"
                    :class="{ active: activeId === doc.id }"
                    @click="selectNode(doc)"
                  >
                    <span class="dot">·</span>
                    <span class="tree-title">{{ doc.title }}</span>
                  </button>
                  <button
                    class="tree-more"
                    title="更多操作"
                    @click.stop="openContextMenu($event, doc)"
                  >
                    ⋯
                  </button>
                </div>
                <div
                  v-for="sec in doc.children"
                  :key="sec.id"
                  class="tree-item"
                  @contextmenu.prevent="openContextMenu($event, sec)"
                >
                  <button
                    class="tree-row section-row"
                    :class="{ active: activeId === sec.id }"
                    @click="selectNode(sec)"
                  >
                    <span class="tree-title">{{ sec.title }}</span>
                  </button>
                  <button
                    class="tree-more section-more"
                    title="更多操作"
                    @click.stop="openContextMenu($event, sec)"
                  >
                    ⋯
                  </button>
                </div>
              </template>
            </div>
          </div>
        </template>
      </nav>
    </aside>

    <Teleport to="body">
      <div
        v-if="contextMenu.visible"
        class="context-menu"
        :style="{ top: `${contextMenu.y}px`, left: `${contextMenu.x}px` }"
        @click.stop
      >
        <button class="context-item danger" :disabled="deleting" @click="confirmDelete(contextMenu.node)">
          🗑️ 删除
        </button>
      </div>
    </Teleport>

    <div class="main">
      <div v-if="loadingChapter" class="loading">加载章节中…</div>

      <template v-else-if="activeChapter">
        <div class="chapter-head">
          <nav class="breadcrumb">
            <span v-for="(crumb, i) in activeChapter.breadcrumb" :key="crumb.id">
              <span v-if="i > 0" class="sep">›</span>
              {{ crumb.title }}
            </span>
          </nav>
          <div class="chapter-tags">
            <span class="tag">{{ docTypeLabel(activeChapter.docType) }}</span>
            <span class="tag path">{{ activeChapter.chapterPath }}</span>
          </div>
          <h3>{{ activeChapter.title }}</h3>
          <div class="chapter-actions">
            <button v-if="!editing" class="edit-btn" @click="startEdit">✏️ 编辑</button>
            <button
              v-if="!editing"
              class="delete-btn"
              :disabled="deleting"
              @click="deleteActiveChapter"
            >
              🗑️ 删除
            </button>
            <template v-else>
              <button class="save-btn" :disabled="saving" @click="saveEdit">
                {{ saving ? '保存中…' : '保存' }}
              </button>
              <button class="cancel-btn" :disabled="saving" @click="cancelEdit">取消</button>
            </template>
          </div>
          <p v-if="saveMessage" class="save-msg">{{ saveMessage }}</p>
          <p v-if="saveError" class="save-err">{{ saveError }}</p>
          <p v-if="deleteMessage" class="save-msg">{{ deleteMessage }}</p>
          <div v-if="currentCourseProduct?.purchasable" class="course-buy">
            <span class="course-price">{{ currentCourseProduct.price }}</span>
            <button
              class="buy-btn"
              :disabled="!!buyingCourseId"
              @click="buyCourse(activeChapter.docId)"
            >
              {{ buyingCourseId === activeChapter.docId ? '跳转支付中…' : '立即购买' }}
            </button>
          </div>
          <p v-if="buyError" class="error">{{ buyError }}</p>
        </div>

        <article v-if="!editing" class="chapter-body">{{ activeChapter.content }}</article>
        <div v-else class="chapter-editor">
          <textarea
            v-model="editContent"
            class="edit-textarea"
            placeholder="支持 Markdown，小节编辑请保留 ## 标题"
          ></textarea>
          <p class="edit-hint">Markdown 格式 · 保存后写入 data/knowledge/love 并同步 RAG 向量库</p>
        </div>

        <div class="ask-panel">
          <div class="ask-head">
            <span>💡 向本章提问</span>
            <span class="ask-scope">检索范围：{{ activeChapter.chapterPath }}</span>
          </div>
          <div class="ask-row">
            <input
              v-model="chapterQuestion"
              placeholder="基于当前章节内容提问…"
              :disabled="asking"
              @keydown.enter="askInChapter()"
            />
            <button class="send-btn" :disabled="asking || !chapterQuestion.trim()" @click="askInChapter()">
              {{ asking ? '检索中' : '提问' }}
            </button>
          </div>
          <p v-if="askError" class="error">{{ askError }}</p>

          <div v-if="askResult" class="ask-result">
            <div class="answer-block">
              <div class="label">💕 小爱的回答</div>
              <p>{{ askResult.answer }}</p>
            </div>
            <div v-if="askResult.sources?.length" class="sources">
              <div class="label">🔍 引用来源</div>
              <div v-for="(src, i) in askResult.sources" :key="i" class="source-card">
                <div class="source-head">
                  <span class="source-path">{{ src.chapterPath || src.title }}</span>
                  <span class="source-score">{{ (src.score * 100).toFixed(0) }}%</span>
                </div>
                <p class="source-content">{{ src.content }}</p>
              </div>
            </div>
            <div v-if="askResult.recommendedCourses?.length" class="courses">
              <div class="label">🎓 相关课程</div>
              <div v-for="course in askResult.recommendedCourses" :key="course.id" class="course-card">
                <div class="course-head">
                  <span>{{ course.name }}</span>
                  <span class="course-price">{{ course.price }}</span>
                </div>
                <p>{{ course.reason }}</p>
                <button
                  v-if="course.purchasable"
                  class="buy-btn buy-btn-sm"
                  :disabled="!!buyingCourseId"
                  @click="buyCourse(course.id)"
                >
                  {{ buyingCourseId === course.id ? '跳转中…' : '立即购买' }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </template>

      <div v-else class="empty">请从左侧选择章节开始阅读</div>
    </div>

    <div v-if="showPayModal" class="pay-modal-mask" @click.self="closePayModal">
      <div class="pay-modal">
        <h4>选择支付方式</h4>
        <p class="pay-tip">国内用户推荐微信 / 支付宝；海外用户可关闭后使用 Stripe</p>
        <button
          v-for="ch in payChannels"
          :key="ch.channel"
          class="pay-channel-btn"
          :disabled="!!buyingCourseId"
          @click="payWithChannel(ch.channel)"
        >
          <span>{{ ch.label }}</span>
          <small>{{ ch.description }}{{ ch.mock === 'true' ? '（模拟）' : '' }}</small>
        </button>
        <button class="pay-channel-btn stripe-fallback" @click="payWithStripe(pendingCourseId); closePayModal()">
          Stripe 国际支付
        </button>
        <button class="pay-cancel" @click="closePayModal">取消</button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.card {
  background: var(--card);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}

.kb {
  height: 100%;
  display: flex;
  overflow: hidden;
}

.sidebar {
  width: 260px;
  flex-shrink: 0;
  border-right: 1px solid #f0eaf2;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sidebar-head {
  padding: 16px 14px 10px;
  border-bottom: 1px solid #f0eaf2;
}

.sidebar-head h2 {
  font-size: 16px;
  color: var(--pink-deep);
}

.meta {
  font-size: 12px;
  color: var(--text-soft);
  margin-top: 4px;
}

.tree {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0 12px;
}

.tree-row {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  width: 100%;
  text-align: left;
  padding: 7px 12px;
  font-size: 13px;
  color: var(--text);
  background: transparent;
  line-height: 1.4;
}

.tree-row:hover {
  background: #faf7fb;
}

.tree-row.active {
  background: var(--pink-soft);
  color: var(--pink-deep);
  font-weight: 600;
}

.group-row {
  font-weight: 600;
  color: var(--text-soft);
}

.doc-row {
  padding-left: 20px;
}

.section-row {
  padding-left: 34px;
  font-size: 12px;
  color: var(--text-soft);
}

.section-row.active {
  color: var(--pink-deep);
}

.arrow {
  font-size: 10px;
  transition: transform 0.2s;
  color: var(--text-soft);
}

.arrow.open {
  transform: rotate(90deg);
}

.dot {
  color: var(--pink);
}

.tree-title {
  flex: 1;
}

.tree-item {
  position: relative;
  display: flex;
  align-items: center;
}

.tree-item .tree-row {
  flex: 1;
  min-width: 0;
}

.tree-more {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  margin-right: 6px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-soft);
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s, background 0.15s;
}

.tree-item:hover .tree-more,
.tree-more:focus {
  opacity: 1;
}

.tree-more:hover {
  background: #f5eef7;
  color: var(--pink-deep);
}

.section-more {
  margin-right: 4px;
}

.context-menu {
  position: fixed;
  z-index: 2000;
  min-width: 140px;
  padding: 6px;
  background: #fff;
  border: 1px solid #ece4ef;
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.context-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 12px;
  border: none;
  border-radius: 8px;
  background: transparent;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.context-item:hover {
  background: #faf7fb;
}

.context-item.danger {
  color: #c0392b;
}

.context-item:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.loading,
.empty {
  flex: 1;
  display: grid;
  place-items: center;
  color: var(--text-soft);
  font-size: 14px;
}

.chapter-head {
  padding: 18px 22px 12px;
  border-bottom: 1px solid #f0eaf2;
}

.breadcrumb {
  font-size: 12px;
  color: var(--text-soft);
}

.sep {
  margin: 0 6px;
}

.chapter-tags {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  flex-wrap: wrap;
}

.tag {
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--pink-soft);
  color: var(--pink-deep);
}

.tag.path {
  background: #f3ecff;
  color: var(--purple);
}

.chapter-head h3 {
  margin-top: 10px;
  font-size: 20px;
  color: var(--pink-deep);
}

.chapter-actions {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}

.edit-btn,
.save-btn,
.cancel-btn,
.delete-btn {
  padding: 8px 16px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
}

.edit-btn,
.save-btn {
  background: linear-gradient(90deg, var(--pink), var(--purple));
  color: #fff;
}

.delete-btn {
  background: #fff;
  color: #c0392b;
  border: 1.5px solid #f0d4d4;
}

.delete-btn:hover:not(:disabled) {
  background: #fff5f5;
}

.cancel-btn {
  background: #f5f0f6;
  color: var(--text-soft);
}

.save-msg {
  margin-top: 8px;
  font-size: 13px;
  color: #1f7a45;
}

.save-err {
  margin-top: 8px;
  font-size: 13px;
  color: #c0392b;
}

.chapter-editor {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 12px 22px 18px;
}

.edit-textarea {
  flex: 1;
  min-height: 280px;
  padding: 14px 16px;
  border: 1.5px solid #efe6f0;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.7;
  font-family: Consolas, 'Courier New', monospace;
  resize: vertical;
}

.edit-textarea:focus {
  border-color: var(--pink);
  outline: none;
}

.edit-hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-soft);
}

.chapter-body {
  flex: 1;
  overflow-y: auto;
  padding: 18px 22px;
  font-size: 15px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

.ask-panel {
  border-top: 1px solid #f0eaf2;
  padding: 14px 22px 18px;
  background: #fdfbfe;
}

.ask-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-soft);
  margin-bottom: 10px;
}

.ask-scope {
  font-weight: 400;
  font-size: 12px;
  color: var(--purple);
}

.ask-row {
  display: flex;
  gap: 10px;
}

.ask-row input {
  flex: 1;
  padding: 10px 14px;
  border-radius: 12px;
  border: 1.5px solid #efe6f0;
  font-size: 14px;
}

.ask-row input:focus {
  border-color: var(--pink);
}

.send-btn {
  padding: 10px 20px;
  border-radius: 12px;
  background: linear-gradient(90deg, var(--pink), var(--purple));
  color: #fff;
  font-weight: 600;
  white-space: nowrap;
}

.error {
  color: var(--pink-deep);
  font-size: 13px;
  margin-top: 8px;
}

.ask-result {
  margin-top: 14px;
  max-height: 240px;
  overflow-y: auto;
}

.label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-soft);
  margin-bottom: 6px;
}

.answer-block p {
  padding: 10px 12px;
  background: var(--pink-soft);
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.6;
}

.sources {
  margin-top: 12px;
}

.source-card {
  margin-top: 8px;
  padding: 10px 12px;
  background: #fff;
  border: 1px solid #efe6f0;
  border-radius: 10px;
}

.source-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 4px;
}

.source-path {
  font-size: 12px;
  font-weight: 600;
  color: var(--purple);
}

.source-score {
  font-size: 11px;
  color: var(--text-soft);
}

.source-content {
  font-size: 12px;
  line-height: 1.5;
  color: #5a5562;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.courses {
  margin-top: 12px;
}

.course-card {
  margin-top: 8px;
  padding: 10px 12px;
  background: linear-gradient(135deg, #fff5f8, #f3ecff);
  border-radius: 10px;
  font-size: 13px;
}

.course-head {
  display: flex;
  justify-content: space-between;
  font-weight: 600;
  margin-bottom: 4px;
}

.course-price {
  color: var(--purple);
}

.course-buy {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}

.buy-btn {
  padding: 9px 18px;
  border-radius: 12px;
  background: linear-gradient(90deg, var(--pink), var(--purple));
  color: #fff;
  font-weight: 600;
  font-size: 14px;
}

.buy-btn:disabled {
  opacity: 0.7;
}

.buy-btn-sm {
  margin-top: 8px;
  padding: 7px 14px;
  font-size: 13px;
}

.pay-modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  display: grid;
  place-items: center;
  z-index: 1000;
}

.pay-modal {
  width: min(420px, 92vw);
  background: #fff;
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.15);
}

.pay-modal h4 {
  font-size: 18px;
  color: var(--pink-deep);
  margin-bottom: 8px;
}

.pay-tip {
  font-size: 12px;
  color: var(--text-soft);
  margin-bottom: 14px;
}

.pay-channel-btn {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  width: 100%;
  padding: 12px 14px;
  margin-bottom: 8px;
  border-radius: 12px;
  border: 1.5px solid #efe6f0;
  background: #fff;
  text-align: left;
}

.pay-channel-btn small {
  font-size: 12px;
  color: var(--text-soft);
  margin-top: 4px;
}

.pay-channel-btn:hover {
  border-color: var(--pink);
  background: var(--pink-soft);
}

.stripe-fallback {
  border-style: dashed;
}

.pay-cancel {
  width: 100%;
  margin-top: 6px;
  padding: 10px;
  color: var(--text-soft);
}
</style>
