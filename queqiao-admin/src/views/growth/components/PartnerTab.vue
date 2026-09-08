<script setup lang="ts">
/**
 * 5.3.4 产业链导流
 * 合作商户管理 + 导流单（标记成交 → 结算计佣）+ 按牵线智能推荐商户。
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchMerchants,
  createMerchant,
  updateMerchant,
  deleteMerchant,
  fetchPartnerOrders,
  createPartnerOrder,
  dealPartnerOrder,
  settlePartnerOrder,
  cancelPartnerOrder,
  recommendMerchants,
  fetchPartnerSummary,
  type PartnerMerchant,
  type MerchantOrder,
} from '@/api'
import { useGrowth } from '../context'

const ctx = useGrowth()
const { isManager, isAdmin, isAgent, fmt, statusType } = ctx

const loading = ref(false)
const merchants = ref<PartnerMerchant[]>([])
const orders = ref<MerchantOrder[]>([])
const partnerSummary = ref<any>(null)

const loadPartner = async () => {
  loading.value = true
  try {
    ;[merchants.value, orders.value] = await Promise.all([fetchMerchants(), fetchPartnerOrders()])
    try {
      partnerSummary.value = await fetchPartnerSummary()
    } catch {
      partnerSummary.value = null
    }
  } catch (e: any) {
    ElMessage.error('加载导流数据失败：' + (e?.message ?? '未知错误'))
  } finally {
    loading.value = false
  }
}

/* —— 合作商户 —— */
const merchantVisible = ref(false)
const merchantForm = reactive<Partial<PartnerMerchant>>({ id: '', name: '', category: '', city: '', commissionRate: 10, status: '合作中', contact: '' })
const openMerchantForm = (m?: PartnerMerchant) => {
  Object.assign(merchantForm, m ? { ...m } : { id: '', name: '', category: '', city: '', commissionRate: 10, status: '合作中', contact: '' })
  merchantVisible.value = true
}
const submitMerchant = async () => {
  try {
    if (merchantForm.id) await updateMerchant(merchantForm.id, { ...merchantForm })
    else await createMerchant({ ...merchantForm })
    ElMessage.success('合作商户已保存')
    merchantVisible.value = false
    await loadPartner()
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.message ?? '未知错误'))
  }
}
const delMerchant = async (m: PartnerMerchant) => {
  try {
    await ElMessageBox.confirm('确认删除该合作商户？', '删除', { type: 'warning' })
    await deleteMerchant(m.id)
    ElMessage.success('已删除')
    await loadPartner()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('删除失败：' + (e?.message ?? '未知错误'))
  }
}

/* —— 导流单 —— */
const orderVisible = ref(false)
const orderForm = reactive({ merchantId: '', memberId: '', amount: 0, note: '' })
const openOrderForm = () => {
  Object.assign(orderForm, { merchantId: merchants.value[0]?.id || '', memberId: '', amount: 0, note: '' })
  orderVisible.value = true
}
const submitOrder = async () => {
  if (!orderForm.merchantId) {
    ElMessage.warning('请选择合作商户')
    return
  }
  try {
    await createPartnerOrder({ merchantId: orderForm.merchantId, memberId: orderForm.memberId || null, amount: Number(orderForm.amount) || 0, note: orderForm.note })
    ElMessage.success('导流单已创建')
    orderVisible.value = false
    await loadPartner()
  } catch (e: any) {
    ElMessage.error('创建失败：' + (e?.message ?? '未知错误'))
  }
}
const doDeal = async (o: MerchantOrder) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入成交金额（元）', '标记成交', { inputValue: String(o.amount || 0) })
    const amt = Number(value)
    if (!amt || amt <= 0) return
    await dealPartnerOrder(o.id, { amount: amt })
    ElMessage.success('已标记成交，佣金已计')
    await loadPartner()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('成交失败：' + (e?.message ?? '未知错误'))
  }
}
const doSettle = async (o: MerchantOrder) => {
  try {
    await settlePartnerOrder(o.id)
    ElMessage.success('已结算')
    await loadPartner()
  } catch (e: any) {
    ElMessage.error('结算失败：' + (e?.message ?? '未知错误'))
  }
}
const doCancel = async (o: MerchantOrder) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入取消原因', '取消导流单', { inputType: 'textarea' })
    await cancelPartnerOrder(o.id, { reason: value || '用户取消' })
    ElMessage.success('已取消')
    await loadPartner()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('取消失败：' + (e?.message ?? '未知错误'))
  }
}

/* —— 牵线推荐商户 —— */
const recPairId = ref('')
const recResult = ref<PartnerMerchant[]>([])
const doRecommend = async () => {
  if (!recPairId.value) {
    ElMessage.warning('请输入牵线ID')
    return
  }
  try {
    recResult.value = (await recommendMerchants(recPairId.value)).merchants || []
    if (!recResult.value.length) ElMessage.info('暂无匹配的合作商户')
  } catch (e: any) {
    ElMessage.error('推荐失败：' + (e?.message ?? '未知错误'))
  }
}

onMounted(loadPartner)
</script>

<template>
  <div>
    <div class="summary-grid" v-if="partnerSummary">
      <div class="sum-card">
        <span class="sum-label">导流单数</span>
        <span class="sum-value">{{ partnerSummary.orderCount ?? orders.length }}</span>
      </div>
      <div class="sum-card">
        <span class="sum-label">已成交</span>
        <span class="sum-value warn">{{ partnerSummary.dealCount ?? '-' }}</span>
      </div>
      <div class="sum-card">
        <span class="sum-label">已结算金额</span>
        <span class="sum-value ok">¥{{ fmt(partnerSummary.settledAmount) }}</span>
      </div>
    </div>

    <div class="toolbar">
      <el-button type="primary" @click="openOrderForm">新建导流单</el-button>
      <el-button type="success" plain :disabled="!isAdmin" @click="openMerchantForm()">新增合作商户</el-button>
      <span class="perm-tip" v-if="isAgent">（红娘可创建导流单并查看推荐商户）</span>
    </div>

    <el-divider content-position="left">合作商户</el-divider>
    <el-table :data="merchants" v-loading="loading" stripe border>
      <el-table-column prop="name" label="商户" min-width="140" />
      <el-table-column prop="category" label="类目" width="110" />
      <el-table-column prop="city" label="城市" width="100" />
      <el-table-column prop="commissionRate" label="佣金率" width="90">
        <template #default="{ row }">{{ row.commissionRate }}%</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :disabled="!isAdmin" @click="openMerchantForm(row)">编辑</el-button>
          <el-button link type="danger" :disabled="!isAdmin" @click="delMerchant(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-divider content-position="left">导流单</el-divider>
    <el-table :data="orders" v-loading="loading" stripe border>
      <el-table-column prop="merchantName" label="商户" width="140" />
      <el-table-column prop="memberName" label="会员" width="110" />
      <el-table-column prop="amount" label="金额" width="100">
        <template #default="{ row }">¥{{ fmt(row.amount) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="commissionAmount" label="佣金" width="100">
        <template #default="{ row }"><b v-if="row.commissionAmount">¥{{ fmt(row.commissionAmount) }}</b><span v-else>—</span></template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button link type="warning" :disabled="!isManager || row.status !== '已导流'" @click="doDeal(row)">成交</el-button>
          <el-button link type="success" :disabled="!isAdmin || row.status !== '已成交'" @click="doSettle(row)">结算</el-button>
          <el-button link type="danger" :disabled="!isManager || row.status === '已结算'" @click="doCancel(row)">取消</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-divider content-position="left">成功牵线推荐商户</el-divider>
    <div class="toolbar">
      <el-input v-model="recPairId" placeholder="输入牵线ID" style="width: 260px" />
      <el-button type="primary" plain @click="doRecommend">获取推荐</el-button>
    </div>
    <el-table :data="recResult" stripe border v-if="recResult.length">
      <el-table-column prop="name" label="商户" min-width="140" />
      <el-table-column prop="category" label="类目" width="110" />
      <el-table-column prop="commissionRate" label="佣金率" width="90">
        <template #default="{ row }">{{ row.commissionRate }}%</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag></template>
      </el-table-column>
    </el-table>

    <!-- 合作商户 -->
    <el-dialog v-model="merchantVisible" title="合作商户" width="460px">
      <el-form label-width="90px">
        <el-form-item label="商户名"><el-input v-model="merchantForm.name" /></el-form-item>
        <el-form-item label="类目"><el-input v-model="merchantForm.category" placeholder="如 婚纱摄影 / 婚礼策划" /></el-form-item>
        <el-form-item label="城市"><el-input v-model="merchantForm.city" /></el-form-item>
        <el-form-item label="佣金率"><el-input-number v-model="merchantForm.commissionRate" :min="0" :max="100" :step="1" style="width: 100%" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="merchantForm.status" style="width: 100%">
            <el-option label="合作中" value="合作中" />
            <el-option label="暂停" value="暂停" />
            <el-option label="终止" value="终止" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="merchantVisible = false">取消</el-button>
        <el-button type="primary" @click="submitMerchant">保存</el-button>
      </template>
    </el-dialog>

    <!-- 导流单 -->
    <el-dialog v-model="orderVisible" title="新建导流单" width="460px">
      <el-form label-width="90px">
        <el-form-item label="合作商户">
          <el-select v-model="orderForm.merchantId" filterable style="width: 100%">
            <el-option v-for="m in merchants" :key="m.id" :label="`${m.name}（${m.commissionRate}%）`" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="会员ID"><el-input v-model="orderForm.memberId" placeholder="可空" /></el-form-item>
        <el-form-item label="金额"><el-input-number v-model="orderForm.amount" :min="0" :step="100" style="width: 100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="orderForm.note" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="orderVisible = false">取消</el-button>
        <el-button type="primary" @click="submitOrder">创建</el-button>
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
