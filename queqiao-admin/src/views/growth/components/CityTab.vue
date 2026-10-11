<script setup lang="ts">
/**
 * 5.3.3 同城运营
 *  - 城市合伙人（招商加盟）+ 社群档位（轻量会员订阅）
 *  - 社群档位订阅：下单 → 模拟收款(simulatePay) → 自动开通会员资格
 * 角色：合伙人/档位的新增编辑仅管理员；订阅动作红娘即可为会员开通。
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchCityPartners,
  fetchCityTiers,
  createCityTier,
  updateCityTier,
  deleteCityTier,
  subscribeCityTier,
  fetchTierMemberships,
  simulatePay,
  fetchCityOverview,
  fetchCityList,
  createCityPartner,
  updateCityPartner,
  deleteCityPartner,
  type CityPartner,
  type CommunityTier,
  type CommunityMembership,
} from '@/api'
import { useGrowth } from '../context'

const ctx = useGrowth()
const { isAdmin, memberOpts, fmt } = ctx

const loading = ref(false)
const cityPartners = ref<CityPartner[]>([])
const cityTiers = ref<CommunityTier[]>([])
const cityList = ref<any[]>([])
const overview = ref<any>(null)
const overviewCity = ref('')

const loadCity = async () => {
  loading.value = true
  try {
    ;[cityPartners.value, cityTiers.value, cityList.value] = await Promise.all([
      fetchCityPartners(),
      fetchCityTiers(),
      fetchCityList(),
    ])
    if (cityList.value.length) {
      const stillExists = cityList.value.some((c: any) => c.city === overviewCity.value)
      if (!overviewCity.value || !stillExists) overviewCity.value = cityList.value[0].city
      await loadOverview()
    } else {
      overviewCity.value = ''
      overview.value = null
    }
  } catch (e: any) {
    ElMessage.error('加载同城数据失败：' + (e?.message ?? '未知错误'))
  } finally {
    loading.value = false
  }
}
const loadOverview = async () => {
  if (!overviewCity.value) return
  try {
    overview.value = await fetchCityOverview(overviewCity.value)
  } catch (e: any) {
    overview.value = null
    ElMessage.error('加载单城总览失败：' + (e?.message ?? '未知错误'))
  }
}

/* —— 社群档位 —— */
const tierVisible = ref(false)
const tierForm = reactive<Partial<CommunityTier>>({ id: '', city: '', level: '轻会员', price: 0, capacity: 0, memberCount: 0, benefits: '', status: '在售' })
const openTierForm = (t?: CommunityTier) => {
  Object.assign(
    tierForm,
    t
      ? { ...t, status: t.status || '在售' }
      : { id: '', city: overviewCity.value, level: '轻会员', price: 0, capacity: 0, memberCount: 0, benefits: '', status: '在售' },
  )
  tierVisible.value = true
}
const submitTier = async () => {
  try {
    if (tierForm.id) await updateCityTier(tierForm.id, { ...tierForm })
    else await createCityTier({ ...tierForm })
    ElMessage.success('社群档位已保存')
    tierVisible.value = false
    await loadCity()
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.message ?? '未知错误'))
  }
}
const delTier = async (t: CommunityTier) => {
  try {
    await ElMessageBox.confirm('确认删除该社群档位？', '删除', { type: 'warning' })
    await deleteCityTier(t.id)
    ElMessage.success('已删除')
    await loadCity()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('删除失败：' + (e?.message ?? '未知错误'))
  }
}

/* —— 社群档位订阅（下单 → 支付 → 自动开通会员资格） —— */
const subVisible = ref(false)
const subSubmitting = ref(false)
const subTier = ref<CommunityTier | null>(null)
const subForm = reactive({ memberId: '', memberName: '' })

const tierFull = (t: CommunityTier) => !!t.capacity && t.capacity > 0 && (t.memberCount ?? 0) >= t.capacity
const tierSubscribable = (t: CommunityTier) => t.status !== '已下架' && !tierFull(t)

const openSubscribe = (t: CommunityTier) => {
  if (!tierSubscribable(t)) {
    ElMessage.warning(tierFull(t) ? `该档位已满（${t.memberCount}/${t.capacity}）` : '该档位已下架，无法订阅')
    return
  }
  subTier.value = t
  subForm.memberId = ''
  subForm.memberName = ''
  subVisible.value = true
}
const onSubMemberChange = () => {
  const m = ctx.members.value.find((x: any) => x.id === subForm.memberId)
  subForm.memberName = m?.name ?? ''
}
const submitSubscribe = async () => {
  const t = subTier.value
  if (!t) return
  if (!subForm.memberId) {
    ElMessage.warning('请选择订阅会员')
    return
  }
  subSubmitting.value = true
  try {
    const res = await subscribeCityTier(t.id, { memberId: subForm.memberId, memberName: subForm.memberName })
    const order = res?.order
    subVisible.value = false
    try {
      await ElMessageBox.confirm(
        `已生成社群订阅待支付订单 ${order?.orderNo}（${subForm.memberName} ¥${order?.amount ?? t.price}）。\n线下收款后点击「确认收款」，系统将自动开通社群会员资格。`,
        '订阅下单成功',
        { confirmButtonText: '确认收款', cancelButtonText: '稍后支付', type: 'success' },
      )
      await simulatePay(order!.id)
      ElMessage.success('收款成功，社群会员资格已开通')
    } catch {
      ElMessage.info('订单已生成，可稍后在支付中心确认收款')
    }
    await loadCity()
  } catch (e: any) {
    ElMessage.error('订阅失败：' + (e?.message ?? '未知错误'))
  } finally {
    subSubmitting.value = false
  }
}

/* —— 档位会员名单 —— */
const memListVisible = ref(false)
const memListLoading = ref(false)
const memList = ref<CommunityMembership[]>([])
const memListTier = ref<CommunityTier | null>(null)
const openMemberships = async (t: CommunityTier) => {
  memListTier.value = t
  memList.value = []
  memListVisible.value = true
  memListLoading.value = true
  try {
    memList.value = (await fetchTierMemberships(t.id)) ?? []
  } catch (e: any) {
    ElMessage.error('加载会员名单失败：' + (e?.message ?? '未知错误'))
  } finally {
    memListLoading.value = false
  }
}

/* —— 城市合伙人 —— */
const partnerVisible = ref(false)
const partnerForm = reactive<Partial<CityPartner>>({ id: '', city: '', ownerAgentName: '', tier: '城市代理', status: '筹备中', shareRate: 20, startDate: '' })
const openPartnerForm = (p?: CityPartner) => {
  Object.assign(partnerForm, p ? { ...p } : { id: '', city: overviewCity.value, ownerAgentName: '', tier: '城市代理', status: '筹备中', shareRate: 20, startDate: '' })
  partnerVisible.value = true
}
const submitPartner = async () => {
  try {
    if (partnerForm.id) await updateCityPartner(partnerForm.id, { ...partnerForm })
    else await createCityPartner({ ...partnerForm })
    ElMessage.success('城市合伙人已保存')
    partnerVisible.value = false
    await loadCity()
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.message ?? '未知错误'))
  }
}
const delPartner = async (p: CityPartner) => {
  try {
    await ElMessageBox.confirm('确认删除该城市合伙人？', '删除', { type: 'warning' })
    await deleteCityPartner(p.id)
    ElMessage.success('已删除')
    await loadCity()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('删除失败：' + (e?.message ?? '未知错误'))
  }
}

onMounted(loadCity)
</script>

<template>
  <div>
    <div class="toolbar">
      <el-select v-model="overviewCity" placeholder="选择城市" style="width: 200px" @change="loadOverview">
        <el-option v-for="c in cityList" :key="c.city" :label="c.city" :value="c.city" />
      </el-select>
      <el-button type="primary" plain @click="loadOverview">查看单城总览</el-button>
      <el-button type="success" plain :disabled="!isAdmin" @click="openPartnerForm()">新增合伙人</el-button>
      <el-button type="success" plain :disabled="!isAdmin" @click="openTierForm()">新增社群档位</el-button>
    </div>

    <div class="summary-grid" v-if="overview">
      <div class="sum-card">
        <span class="sum-label">{{ overview.city }} 社群营收</span>
        <span class="sum-value">¥{{ fmt(overview.communityRevenue) }}</span>
        <span class="sum-sub">{{ overview.tiers?.length || 0 }} 个档位</span>
      </div>
      <div class="sum-card">
        <span class="sum-label">合伙人营收</span>
        <span class="sum-value ok">¥{{ fmt(overview.partnerRevenue) }}</span>
        <span class="sum-sub">{{ overview.partners?.length || 0 }} 位合伙人</span>
      </div>
      <div class="sum-card">
        <span class="sum-label">社群档位</span>
        <span class="sum-value">{{ overview.tiers?.length || 0 }}</span>
      </div>
      <div class="sum-card">
        <span class="sum-label">城市合伙人</span>
        <span class="sum-value">{{ overview.partners?.length || 0 }}</span>
      </div>
    </div>

    <el-divider content-position="left">城市合伙人</el-divider>
    <el-table :data="cityPartners" v-loading="loading" stripe border>
      <el-table-column prop="city" label="城市" width="110" />
      <el-table-column prop="ownerAgentName" label="负责人" width="120" />
      <el-table-column prop="tier" label="层级" width="110" />
      <el-table-column prop="shareRate" label="分成比例" width="100">
        <template #default="{ row }">{{ row.shareRate }}%</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }"><el-tag :type="row.status === '已暂停' ? 'info' : 'success'" size="small">{{ row.status }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :disabled="!isAdmin" @click="openPartnerForm(row)">编辑</el-button>
          <el-button link type="danger" :disabled="!isAdmin" @click="delPartner(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-divider content-position="left">社群档位</el-divider>
    <el-table :data="cityTiers" v-loading="loading" stripe border>
      <el-table-column prop="city" label="城市" width="110" />
      <el-table-column prop="level" label="档位" width="100" />
      <el-table-column prop="price" label="价格" width="100">
        <template #default="{ row }">¥{{ fmt(row.price) }}</template>
      </el-table-column>
      <el-table-column label="会员数 / 容量" width="130">
        <template #default="{ row }">
          <span :class="{ full: tierFull(row) }">{{ row.memberCount ?? 0 }}<span class="muted"> / {{ row.capacity ? row.capacity : '不限' }}</span></span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === '已下架' ? 'info' : 'success'">{{ row.status || '在售' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="benefits" label="权益" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button link type="success" :disabled="!tierSubscribable(row)" @click="openSubscribe(row)">订阅</el-button>
          <el-button link type="primary" @click="openMemberships(row)">名单</el-button>
          <el-button link type="primary" :disabled="!isAdmin" @click="openTierForm(row)">编辑</el-button>
          <el-button link type="danger" :disabled="!isAdmin" @click="delTier(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 社群档位 -->
    <el-dialog v-model="tierVisible" title="社群档位" width="460px">
      <el-form label-width="90px">
        <el-form-item label="城市"><el-input v-model="tierForm.city" /></el-form-item>
        <el-form-item label="档位"><el-input v-model="tierForm.level" placeholder="如 轻会员 / VIP" /></el-form-item>
        <el-form-item label="价格"><el-input-number v-model="tierForm.price" :min="0" :step="10" style="width: 100%" /></el-form-item>
        <el-form-item label="容量">
          <el-input-number v-model="tierForm.capacity" :min="0" style="width: 100%" />
          <div class="form-tip">0 表示不限容量；达到容量后订阅将被拒绝</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="tierForm.status" style="width: 100%">
            <el-option label="在售" value="在售" />
            <el-option label="已下架" value="已下架" />
          </el-select>
        </el-form-item>
        <el-form-item label="会员数">
          <el-input-number v-model="tierForm.memberCount" :min="0" disabled style="width: 100%" />
          <div class="form-tip">由订阅支付成功自动累加，不支持手工修改</div>
        </el-form-item>
        <el-form-item label="权益"><el-input v-model="tierForm.benefits" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tierVisible = false">取消</el-button>
        <el-button type="primary" @click="submitTier">保存</el-button>
      </template>
    </el-dialog>

    <!-- 社群档位订阅下单 -->
    <el-dialog v-model="subVisible" title="社群档位订阅" width="460px" :close-on-click-modal="false">
      <div class="sub-head" v-if="subTier">
        <span class="sub-title">{{ subTier.city }} · {{ subTier.level }}</span>
        <span class="sub-price">¥{{ fmt(subTier.price) }}</span>
        <span class="muted">已有 {{ subTier.memberCount ?? 0 }} 人{{ subTier.capacity ? ` / 容量 ${subTier.capacity}` : '' }}</span>
      </div>
      <el-form label-width="90px">
        <el-form-item label="订阅会员" required>
          <el-select v-model="subForm.memberId" filterable placeholder="选择会员" style="width: 100%" @change="onSubMemberChange">
            <el-option v-for="o in memberOpts" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="form-tip">下单后生成「社群订阅」待支付订单；确认收款后系统自动开通会员资格并累加档位人数。</div>
      <template #footer>
        <el-button @click="subVisible = false">取消</el-button>
        <el-button type="primary" :loading="subSubmitting" @click="submitSubscribe">订阅下单</el-button>
      </template>
    </el-dialog>

    <!-- 档位会员名单 -->
    <el-dialog v-model="memListVisible" width="720px" :title="`社群会员名单${memListTier ? ' · ' + memListTier.city + ' ' + memListTier.level : ''}`">
      <el-table :data="memList" v-loading="memListLoading" stripe border size="small">
        <el-table-column prop="memberName" label="会员" width="120" />
        <el-table-column prop="tierLevel" label="档位" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === '有效' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生效期" min-width="220">
          <template #default="{ row }">{{ String(row.activeFrom || '').slice(0, 10) }} ~ {{ String(row.activeTo || '').slice(0, 10) }}</template>
        </el-table-column>
        <el-table-column prop="orderId" label="来源订单" min-width="180" show-overflow-tooltip />
      </el-table>
      <div v-if="!memListLoading && !memList.length" class="form-tip">暂无会员，可点击「订阅」为会员开通。</div>
      <template #footer><el-button @click="memListVisible = false">关闭</el-button></template>
    </el-dialog>

    <!-- 城市合伙人 -->
    <el-dialog v-model="partnerVisible" title="城市合伙人" width="460px">
      <el-form label-width="90px">
        <el-form-item label="城市"><el-input v-model="partnerForm.city" /></el-form-item>
        <el-form-item label="负责人"><el-input v-model="partnerForm.ownerAgentName" /></el-form-item>
        <el-form-item label="层级">
          <el-select v-model="partnerForm.tier" style="width: 100%">
            <el-option label="城市合伙人" value="城市合伙人" />
            <el-option label="城市代理" value="城市代理" />
            <el-option label="社群运营" value="社群运营" />
          </el-select>
        </el-form-item>
        <el-form-item label="分成比例"><el-input-number v-model="partnerForm.shareRate" :min="0" :max="100" :step="5" style="width: 100%" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="partnerForm.status" style="width: 100%">
            <el-option label="筹备中" value="筹备中" />
            <el-option label="运营中" value="运营中" />
            <el-option label="已暂停" value="已暂停" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="partnerVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPartner">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.muted { color: var(--dt-ink-3); }
.full { color: var(--dt-error); font-weight: 600; }
.form-tip { margin-top: 6px; font-size: 0.75rem; color: var(--dt-ink-3); line-height: 1.6; }
.sub-head { display: flex; align-items: baseline; gap: 12px; flex-wrap: wrap; margin-bottom: 14px; }
.sub-head .sub-title { font-size: 0.9375rem; font-weight: 600; color: var(--dt-ink-1); }
.sub-head .sub-price { font-size: 1.125rem; font-weight: 600; color: var(--dt-error); }
.summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 16px; margin: 16px 0; }
.sum-card { background: var(--dt-card-solid); border: 1px solid var(--dt-border); border-radius: var(--dt-radius-md); padding: 16px 18px; display: flex; flex-direction: column; gap: 4px; }
.sum-label { font-size: 0.75rem; color: var(--dt-ink-3); }
.sum-value { font-size: 1.5rem; font-weight: 600; color: var(--dt-ink-1); }
.sum-value.ok { color: var(--dt-success); }
.sum-sub { font-size: 0.75rem; color: var(--dt-ink-3); }
.toolbar { display: flex; align-items: center; gap: 12px; margin: 12px 0; flex-wrap: wrap; }
</style>
