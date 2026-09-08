<script setup lang="ts">
/**
 * 5.3.5 渠道归因 ROI
 * 渠道管理与 ROI 分析（ROI = 成交佣金 / 渠道成本；CAC = 成本 / 线索数；转化率 = 成交 / 线索）。
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchChannels,
  createChannel,
  updateChannel,
  deleteChannel,
  fetchChannelRoi,
  type MarketChannel,
  type ChannelRoi,
} from '@/api'
import { useGrowth } from '../context'

const ctx = useGrowth()
const { isAdmin, fmt, statusType } = ctx

const loading = ref(false)
const channels = ref<MarketChannel[]>([])
const roi = ref<ChannelRoi | null>(null)

const loadChannel = async () => {
  loading.value = true
  try {
    ;[channels.value, roi.value] = await Promise.all([fetchChannels(), fetchChannelRoi()])
  } catch (e: any) {
    ElMessage.error('加载渠道数据失败：' + (e?.message ?? '未知错误'))
  } finally {
    loading.value = false
  }
}

const channelVisible = ref(false)
const channelForm = reactive<Partial<MarketChannel>>({ id: '', name: '', category: '线上投放', code: '', cost: 0, owner: '', status: '启用' })
const openChannelForm = (c?: MarketChannel) => {
  Object.assign(channelForm, c ? { ...c } : { id: '', name: '', category: '线上投放', code: '', cost: 0, owner: '', status: '启用' })
  channelVisible.value = true
}
const submitChannel = async () => {
  try {
    if (channelForm.id) await updateChannel(channelForm.id, { ...channelForm })
    else await createChannel({ ...channelForm })
    ElMessage.success('渠道已保存')
    channelVisible.value = false
    await loadChannel()
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.message ?? '未知错误'))
  }
}
const delChannel = async (c: MarketChannel) => {
  try {
    await ElMessageBox.confirm('确认删除该渠道？', '删除', { type: 'warning' })
    await deleteChannel(c.id)
    ElMessage.success('已删除')
    await loadChannel()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('删除失败：' + (e?.message ?? '未知错误'))
  }
}

onMounted(loadChannel)
</script>

<template>
  <div>
    <div class="toolbar">
      <el-button type="success" plain :disabled="!isAdmin" @click="openChannelForm()">新增渠道</el-button>
      <span class="perm-tip">ROI = 成交佣金 / 渠道成本；CAC = 成本 / 线索数；转化率 = 成交 / 线索</span>
    </div>

    <el-divider content-position="left">渠道 ROI 明细</el-divider>
    <el-table :data="roi?.rows || []" v-loading="loading" stripe border>
      <el-table-column prop="name" label="渠道" min-width="140" />
      <el-table-column prop="leadCount" label="线索数" width="100" />
      <el-table-column prop="cost" label="成本" width="110">
        <template #default="{ row }">¥{{ fmt(row.cost) }}</template>
      </el-table-column>
      <el-table-column prop="dealCount" label="成交数" width="90" />
      <el-table-column prop="cac" label="CAC" width="100">
        <template #default="{ row }">¥{{ fmt(row.cac) }}</template>
      </el-table-column>
      <el-table-column prop="convRate" label="转化率" width="100">
        <template #default="{ row }">{{ (row.convRate * 100).toFixed(1) }}%</template>
      </el-table-column>
      <el-table-column prop="roi" label="ROI" width="100">
        <template #default="{ row }">
          <el-tag :type="row.roi >= 1 ? 'success' : 'danger'" size="small">{{ row.roi.toFixed(2) }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
    <div class="roi-total" v-if="roi">总线索数：<b>{{ roi.leadTotal }}</b></div>

    <el-divider content-position="left">渠道管理</el-divider>
    <el-table :data="channels" v-loading="loading" stripe border>
      <el-table-column prop="name" label="渠道" min-width="140" />
      <el-table-column prop="category" label="类目" width="110" />
      <el-table-column prop="code" label="编码" width="110" />
      <el-table-column prop="cost" label="成本" width="100">
        <template #default="{ row }">¥{{ fmt(row.cost) }}</template>
      </el-table-column>
      <el-table-column prop="owner" label="负责人" width="100" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :disabled="!isAdmin" @click="openChannelForm(row)">编辑</el-button>
          <el-button link type="danger" :disabled="!isAdmin" @click="delChannel(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="channelVisible" title="渠道" width="460px">
      <el-form label-width="90px">
        <el-form-item label="渠道名"><el-input v-model="channelForm.name" /></el-form-item>
        <el-form-item label="类目"><el-input v-model="channelForm.category" /></el-form-item>
        <el-form-item label="编码"><el-input v-model="channelForm.code" /></el-form-item>
        <el-form-item label="成本"><el-input-number v-model="channelForm.cost" :min="0" :step="50" style="width: 100%" /></el-form-item>
        <el-form-item label="负责人"><el-input v-model="channelForm.owner" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="channelForm.status" style="width: 100%">
            <el-option label="启用" value="启用" />
            <el-option label="停用" value="停用" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="channelVisible = false">取消</el-button>
        <el-button type="primary" @click="submitChannel">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 12px; margin: 12px 0; flex-wrap: wrap; }
.perm-tip { font-size: 0.75rem; color: var(--dt-ink-4); }
.roi-total { margin-top: 10px; font-size: 0.8125rem; color: var(--dt-ink-2); }
</style>
