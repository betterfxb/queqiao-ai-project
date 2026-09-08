<script setup lang="ts">
/**
 * 5.3.1 转介绍激励
 * 红娘（AGENT）仅见本人登记的转介绍；转化/发奖由主管/管理员操作。
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchReferrals,
  fetchMyReferrals,
  registerReferral,
  convertReferral,
  rewardReferral,
  invalidReferral,
  fetchReferralSummary,
  type Referral,
} from '@/api'
import { useGrowth } from '../context'

const ctx = useGrowth()
const { isManager, isAgent, isAdmin, memberOpts, fmt, statusType } = ctx

const loading = ref(false)
const referrals = ref<Referral[]>([])
const referralSummary = ref<any>(null)

const loadReferrals = async () => {
  loading.value = true
  try {
    referrals.value = isAgent.value ? await fetchMyReferrals() : await fetchReferrals()
    try {
      referralSummary.value = await fetchReferralSummary()
    } catch {
      referralSummary.value = null
    }
  } catch (e: any) {
    ElMessage.error('加载转介绍数据失败：' + (e?.message ?? '未知错误'))
  } finally {
    loading.value = false
  }
}

const refVisible = ref(false)
const refForm = reactive({ referrerId: '', referrerType: '会员', refereeName: '', refereePhone: '', remark: '' })
const openRefForm = () => {
  Object.assign(refForm, { referrerId: '', referrerType: '会员', refereeName: '', refereePhone: '', remark: '' })
  refVisible.value = true
}
const submitRef = async () => {
  if (!refForm.refereeName || !refForm.refereePhone) {
    ElMessage.warning('请填写被推荐人姓名与手机号')
    return
  }
  try {
    await registerReferral({
      referrerId: refForm.referrerId || null,
      referrerType: refForm.referrerType,
      refereeName: refForm.refereeName,
      refereePhone: refForm.refereePhone,
      remark: refForm.remark,
    })
    ElMessage.success('转介绍关系已登记')
    refVisible.value = false
    await loadReferrals()
  } catch (e: any) {
    ElMessage.error('登记失败：' + (e?.message ?? '未知错误'))
  }
}
const doConvert = async (r: Referral) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入成交订单金额（元）', '标记转化', { inputValue: '3000' })
    const amt = Number(value)
    if (!amt || amt <= 0) return
    await convertReferral(r.id, { orderAmount: amt })
    ElMessage.success('已标记转化')
    await loadReferrals()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('转化失败：' + (e?.message ?? '未知错误'))
  }
}
const doReward = async (r: Referral) => {
  try {
    await rewardReferral(r.id)
    ElMessage.success('奖励已发放')
    await loadReferrals()
  } catch (e: any) {
    ElMessage.error('发奖失败：' + (e?.message ?? '未知错误'))
  }
}
const doInvalid = async (r: Referral) => {
  try {
    await ElMessageBox.confirm('确认作废该转介绍记录？', '作废', { type: 'warning' })
    await invalidReferral(r.id)
    ElMessage.success('已作废')
    await loadReferrals()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('作废失败：' + (e?.message ?? '未知错误'))
  }
}

onMounted(loadReferrals)
</script>

<template>
  <div>
    <div class="summary-grid" v-loading="loading">
      <div class="sum-card" v-if="referralSummary">
        <span class="sum-label">累计转介绍</span>
        <span class="sum-value">{{ referralSummary.total }}</span>
      </div>
      <div class="sum-card" v-if="referralSummary">
        <span class="sum-label">已转化</span>
        <span class="sum-value warn">{{ referralSummary.converted ?? referralSummary.registered ?? '-' }}</span>
      </div>
      <div class="sum-card" v-if="referralSummary">
        <span class="sum-label">已发奖</span>
        <span class="sum-value ok">{{ referralSummary.rewarded ?? '-' }}</span>
      </div>
      <div class="sum-card" v-if="referralSummary">
        <span class="sum-label">已发奖励总额</span>
        <span class="sum-value">¥{{ fmt(referralSummary.paidAmount) }}</span>
      </div>
    </div>

    <div class="toolbar">
      <el-button type="primary" @click="openRefForm">登记转介绍</el-button>
      <span class="perm-tip" v-if="isAgent">（红娘仅可登记本人跟进的转介绍）</span>
    </div>

    <el-table :data="referrals" v-loading="loading" stripe border>
      <el-table-column prop="referrerName" label="推荐人" width="120" />
      <el-table-column prop="referrerType" label="类型" width="90" />
      <el-table-column prop="refereeName" label="被推荐人" width="110" />
      <el-table-column prop="refereePhone" label="手机号" width="130" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="rewardAmount" label="奖励金额" width="110">
        <template #default="{ row }"><b v-if="row.rewardAmount">¥{{ fmt(row.rewardAmount) }}</b><span v-else>—</span></template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button link type="warning" :disabled="!isManager || row.status !== '已登记'" @click="doConvert(row)">转化</el-button>
          <el-button link type="success" :disabled="!isAdmin || row.status !== '已转化'" @click="doReward(row)">发奖</el-button>
          <el-button link type="danger" :disabled="!isManager || row.status === '已发奖'" @click="doInvalid(row)">作废</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="refVisible" title="登记转介绍" width="460px">
      <el-form label-width="90px">
        <el-form-item label="推荐人">
          <el-select v-model="refForm.referrerId" placeholder="留空=不绑定会员" clearable filterable style="width: 100%">
            <el-option v-for="m in memberOpts" :key="m.value" :label="m.label" :value="m.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="推荐人类型">
          <el-select v-model="refForm.referrerType" style="width: 100%">
            <el-option label="会员" value="会员" />
            <el-option label="红娘" value="红娘" />
            <el-option label="外部" value="外部" />
          </el-select>
        </el-form-item>
        <el-form-item label="被推荐人"><el-input v-model="refForm.refereeName" placeholder="姓名/昵称" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="refForm.refereePhone" placeholder="11 位手机号" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="refForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="refVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRef">登记</el-button>
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
.sum-sub { font-size: 0.75rem; color: var(--dt-ink-3); }
.toolbar { display: flex; align-items: center; gap: 12px; margin: 12px 0; flex-wrap: wrap; }
.perm-tip { font-size: 0.75rem; color: var(--dt-ink-4); }
</style>
