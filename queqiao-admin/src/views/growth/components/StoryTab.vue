<script setup lang="ts">
/**
 * 5.3.2 成功案例营销
 * 由主管/管理员从「已成功」牵线生成脱敏草稿，再经管理员发布/下架。
 */
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchStories,
  fetchPublicStories,
  draftStory,
  publishStory,
  offlineStory,
  fetchStorySummary,
  type Story,
} from '@/api'
import { useGrowth } from '../context'

const ctx = useGrowth()
const { isManager, isAdmin, pairOpts, statusType } = ctx

const loading = ref(false)
const stories = ref<Story[]>([])
const publicCount = ref(0)
const storySummary = ref<any>(null)

const loadStories = async () => {
  loading.value = true
  try {
    stories.value = await fetchStories()
    try {
      publicCount.value = (await fetchPublicStories()).length
    } catch {
      publicCount.value = 0
    }
    try {
      storySummary.value = await fetchStorySummary()
    } catch {
      storySummary.value = null
    }
  } catch (e: any) {
    ElMessage.error('加载案例失败：' + (e?.message ?? '未知错误'))
  } finally {
    loading.value = false
  }
}

const storyVisible = ref(false)
const storyPairId = ref('')
const openStoryForm = () => {
  storyPairId.value = ''
  storyVisible.value = true
}
const submitStory = async () => {
  if (!storyPairId.value) {
    ElMessage.warning('请选择牵线（仅「已成功」牵线可生成案例）')
    return
  }
  try {
    await draftStory({ pairId: storyPairId.value })
    ElMessage.success('脱敏草稿已生成')
    storyVisible.value = false
    await loadStories()
  } catch (e: any) {
    ElMessage.error('生成草稿失败：' + (e?.message ?? '未知错误'))
  }
}
const doPublish = async (s: Story) => {
  try {
    await publishStory(s.id, { publicVisible: true })
    ElMessage.success('案例已发布（对外可见）')
    await loadStories()
  } catch (e: any) {
    ElMessage.error('发布失败：' + (e?.message ?? '未知错误'))
  }
}
const doOffline = async (s: Story) => {
  try {
    await offlineStory(s.id)
    ElMessage.success('案例已下架')
    await loadStories()
  } catch (e: any) {
    ElMessage.error('下架失败：' + (e?.message ?? '未知错误'))
  }
}

onMounted(loadStories)
</script>

<template>
  <div>
    <div class="summary-grid">
      <div class="sum-card">
        <span class="sum-label">案例总数</span>
        <span class="sum-value">{{ storySummary?.total ?? stories.length }}</span>
      </div>
      <div class="sum-card">
        <span class="sum-label">对外公开</span>
        <span class="sum-value ok">{{ publicCount }}</span>
      </div>
      <div class="sum-card">
        <span class="sum-label">草稿</span>
        <span class="sum-value warn">{{ storySummary?.draft ?? '-' }}</span>
      </div>
    </div>

    <div class="toolbar">
      <el-button type="primary" :disabled="!isManager" @click="openStoryForm">从牵线生成草稿</el-button>
      <span class="perm-tip" v-if="!isManager">（仅主管及以上可沉淀案例）</span>
    </div>

    <el-table :data="stories" v-loading="loading" stripe border>
      <el-table-column prop="maleAlias" label="男方别名" width="120" />
      <el-table-column prop="femaleAlias" label="女方别名" width="120" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="publicVisible" label="对外可见" width="100">
        <template #default="{ row }">
          <el-tag :type="row.publicVisible ? 'success' : 'info'" size="small">{{ row.publicVisible ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" min-width="160" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="success" :disabled="!isAdmin || row.status !== '草稿'" @click="doPublish(row)">发布</el-button>
          <el-button link type="info" :disabled="!isAdmin || row.status !== '已发布'" @click="doOffline(row)">下架</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="storyVisible" title="从牵线生成案例草稿" width="460px">
      <el-form label-width="80px">
        <el-form-item label="牵线">
          <el-select v-model="storyPairId" placeholder="仅「已成功」牵线可生成" filterable style="width: 100%">
            <el-option v-for="p in pairOpts" :key="p.value" :label="p.label" :value="p.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="storyVisible = false">取消</el-button>
        <el-button type="primary" @click="submitStory">生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 16px; margin: 16px 0; }
.sum-card { background: var(--dt-card, #fff); border: 1px solid var(--dt-border, #ececf2); border-radius: var(--dt-radius-lg, 16px); padding: 16px 18px; display: flex; flex-direction: column; gap: 4px; }
.sum-label { font-size: 0.7813rem; color: var(--dt-ink-3); }
.sum-value { font-size: 1.5rem; font-weight: 800; color: var(--dt-ink-1); }
.sum-value.warn { color: #d98800; } .sum-value.ok { color: #1aa86b; }
.toolbar { display: flex; align-items: center; gap: 12px; margin: 12px 0; flex-wrap: wrap; }
.perm-tip { font-size: 0.75rem; color: var(--dt-ink-4); }
</style>
