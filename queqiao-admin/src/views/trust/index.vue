<script setup lang="ts">
/**
 * 信任合规中心（#110 落地页）
 *  - 5.2.1 认证标签产品化：会员认证徽章 + 实名/学历/资产率统计
 *  - 5.2.2 第三方核验：发起核验任务 + 录入结果（通过回写认证标签）
 *  - 5.2.3 风控：举报处理 / 黑名单（拉黑限制牵线）/ 风险扫描与处置
 *  - 5.2.4 合规三件套：四类文档模板配置 + 强制同意留痕
 * 权限：认证标签、合规文档对所有登录角色可见；核验、风控仅主管/管理员可操作。
 */
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchMembers,
  fetchMemberVerify,
  fetchVerifyTasks,
  createVerifyTask,
  recordVerifyResult,
  createRealPersonVerify,
  fetchComplaints,
  createComplaint,
  handleComplaint,
  fetchBlacklist,
  addBlacklist,
  unbanBlacklist,
  scanRisk,
  fetchRiskEvents,
  handleRisk,
  fetchRiskSummary,
  fetchWorkOrders,
  handleWorkOrder,
  fetchWorkOrderSummary,
  fetchDocs,
  fetchDoc,
  saveDoc,
  fetchConsents,
  type VerifyTask,
  type Complaint,
  type BlacklistItem,
  type RiskEvent,
  type WorkOrder,
  type DocTemplate,
  type ConsentRecord,
} from '@/api'
import VerifyBadge from '@/components/VerifyBadge.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const isManager = computed(() => auth.role === 'MANAGER' || auth.role === 'ADMIN')
const isAdmin = computed(() => auth.role === 'ADMIN')

interface MemberRow {
  id: string
  name: string
  gender?: string
  city?: string
  level?: string
  status?: string
  verifyRealName?: boolean
  verifyEducation?: boolean
  verifyAsset?: boolean
  verifyRealPerson?: boolean
  [k: string]: any
}

const loading = ref(false)
const members = ref<MemberRow[]>([])
const memberOptions = computed(() => members.value.map((m) => ({ value: m.id, label: m.name })))

const tab = ref('verify')

// —— 5.2.1 认证标签统计 ——
const verifyStats = computed(() => {
  const list = members.value
  const total = list.length || 1
  const c = (f: string) => list.filter((m) => m[f]).length
  return {
    realName: Math.round((c('verifyRealName') * 100) / total),
    education: Math.round((c('verifyEducation') * 100) / total),
    asset: Math.round((c('verifyAsset') * 100) / total),
    realPerson: Math.round((c('verifyRealPerson') * 100) / total),
    verified: list.filter((m) => m.verifyRealName || m.verifyEducation || m.verifyAsset || m.verifyRealPerson).length,
  }
})

const verifyDetail = ref<any>(null)
const verifyDetailId = ref('')
const verifyDetailVisibleProxy = ref(false)
const showVerifyDetail = async (id: string) => {
  verifyDetailId.value = id
  try {
    verifyDetail.value = await fetchMemberVerify(id)
    verifyDetailVisibleProxy.value = true
  } catch (e: any) {
    ElMessage.error('加载认证详情失败：' + (e?.message ?? ''))
  }
}

// —— 5.2.2 第三方核验 ——
const tasks = ref<VerifyTask[]>([])
const taskForm = ref({ memberId: '', type: '学历', channel: '学信网', rawProof: '' })
const recordDialog = ref(false)
const recordTarget = ref<VerifyTask | null>(null)
const recordForm = ref({ status: '已通过', result: '' })

const loadTasks = async () => {
  try {
    tasks.value = await fetchVerifyTasks()
  } catch (e: any) {
    ElMessage.error('加载核验任务失败：' + (e?.message ?? ''))
  }
}
const submitTask = async () => {
  if (!taskForm.value.memberId) return ElMessage.warning('请选择会员')
  try {
    await createVerifyTask({ ...taskForm.value })
    ElMessage.success('核验任务已发起')
    taskForm.value = { memberId: '', type: '学历', channel: '学信网', rawProof: '' }
    await loadTasks()
  } catch (e: any) {
    ElMessage.error('发起失败：' + (e?.message ?? ''))
  }
}
const openRecord = (t: VerifyTask) => {
  recordTarget.value = t
  recordForm.value = { status: '已通过', result: t.result || '' }
  recordDialog.value = true
}
const submitRecord = async () => {
  if (!recordTarget.value) return
  try {
    await recordVerifyResult(recordTarget.value.id, recordForm.value)
    ElMessage.success('核验结果已录入' + (recordForm.value.status === '已通过' ? '，认证标签已回写' : ''))
    recordDialog.value = false
    await loadTasks()
  } catch (e: any) {
    ElMessage.error('录入失败：' + (e?.message ?? ''))
  }
}

// —— 2-A 自动实人认证（实名公安 + 人脸活体）——
const realPersonDialog = ref(false)
const realPersonLoading = ref(false)
const realPersonTargetId = ref('')
const realPersonTargetName = ref('')
const realPersonForm = ref({ authToken: '', liveImage: '' })
const liveImageList = ref<any[]>([])
const onLiveImageChange = (file: any) => {
  const raw = file?.raw
  if (!raw) return
  const reader = new FileReader()
  reader.onload = () => {
    realPersonForm.value.liveImage = String(reader.result)
    liveImageList.value = [file]
  }
  reader.readAsDataURL(raw)
}
const onLiveImageRemove = () => {
  realPersonForm.value.liveImage = ''
  liveImageList.value = []
}
const openRealPerson = (id: string, name: string) => {
  realPersonTargetId.value = id
  realPersonTargetName.value = name
  realPersonForm.value = { authToken: '', liveImage: '' }
  liveImageList.value = []
  realPersonDialog.value = true
}
const submitRealPerson = async () => {
  if (!realPersonForm.value.liveImage && !realPersonForm.value.authToken) {
    return ElMessage.warning('请上传活体照或填写活体令牌')
  }
  realPersonLoading.value = true
  try {
    const r = await createRealPersonVerify({
      memberId: realPersonTargetId.value,
      authToken: realPersonForm.value.authToken,
      liveImage: realPersonForm.value.liveImage,
    })
    const tip =
      r.status === '已通过' ? '，已实人徽章已点亮' : r.status === '待核验' ? '，已转人工审核留痕' : ''
    ElMessage.success('实人认证已提交：' + r.status + tip)
    realPersonDialog.value = false
    await Promise.all([loadMembers(), loadTasks()])
    if (verifyDetailId.value === realPersonTargetId.value) await showVerifyDetail(realPersonTargetId.value)
  } catch (e: any) {
    ElMessage.error('提交失败：' + (e?.message ?? ''))
  } finally {
    realPersonLoading.value = false
  }
}

// —— 5.2.3 风控：举报 ——
const complaints = ref<Complaint[]>([])
const complaintForm = ref({ targetMemberId: '', type: '欺诈', content: '' })
const handleForm = ref({ status: '已受理', note: '' })
const complaintDialog = ref(false)
const handleTarget = ref<Complaint | null>(null)

const loadComplaints = async () => {
  try {
    complaints.value = await fetchComplaints()
  } catch (e: any) {
    ElMessage.error('加载举报失败：' + (e?.message ?? ''))
  }
}
const submitComplaint = async () => {
  if (!complaintForm.value.targetMemberId) return ElMessage.warning('请选择被举报会员')
  try {
    await createComplaint({ ...complaintForm.value })
    ElMessage.success('举报已提交')
    complaintForm.value = { targetMemberId: '', type: '欺诈', content: '' }
    await loadComplaints()
  } catch (e: any) {
    ElMessage.error('提交失败：' + (e?.message ?? ''))
  }
}
const openHandle = (c: Complaint) => {
  handleTarget.value = c
  handleForm.value = { status: '已受理', note: '' }
  complaintDialog.value = true
}
const submitHandle = async () => {
  if (!handleTarget.value) return
  try {
    await handleComplaint(handleTarget.value.id, handleForm.value)
    ElMessage.success('举报已处理')
    complaintDialog.value = false
    await loadComplaints()
  } catch (e: any) {
    ElMessage.error('处理失败：' + (e?.message ?? ''))
  }
}

// —— 5.2.3 风控：黑名单 ——
const blacklist = ref<BlacklistItem[]>([])
const blackForm = ref({ memberId: '', reason: '', until: '' })
const loadBlacklist = async () => {
  try {
    blacklist.value = await fetchBlacklist()
  } catch (e: any) {
    ElMessage.error('加载黑名单失败：' + (e?.message ?? ''))
  }
}
const submitBlack = async () => {
  if (!blackForm.value.memberId) return ElMessage.warning('请选择会员')
  try {
    await addBlacklist({ ...blackForm.value })
    ElMessage.success('已拉黑，该会员将被限制牵线')
    blackForm.value = { memberId: '', reason: '', until: '' }
    await loadBlacklist()
  } catch (e: any) {
    ElMessage.error('拉黑失败：' + (e?.message ?? ''))
  }
}
const doUnban = async (b: BlacklistItem) => {
  try {
    await ElMessageBox.confirm(`确认解除对 ${b.memberName ?? b.memberId} 的拉黑？`, '解除拉黑', { type: 'warning' })
    await unbanBlacklist(b.id)
    ElMessage.success('已解除拉黑')
    await loadBlacklist()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('操作失败：' + (e?.message ?? ''))
  }
}

// —— 5.2.3 风控：风险事件 ——
const riskEvents = ref<RiskEvent[]>([])
const riskSummary = ref<{ total: number; unhandled: number; byType: Record<string, number> } | null>(null)
const riskHandleNote = ref('')
const riskDialog = ref(false)
const riskTarget = ref<RiskEvent | null>(null)
const loadRisk = async () => {
  try {
    riskEvents.value = await fetchRiskEvents()
    riskSummary.value = await fetchRiskSummary()
  } catch (e: any) {
    ElMessage.error('加载风险事件失败：' + (e?.message ?? ''))
  }
}
const doScan = async () => {
  loading.value = true
  try {
    const r = await scanRisk()
    ElMessage.success(`扫描完成：已扫描 ${r.scanned} 名会员，新增风险事件 ${r.created} 条`)
    await loadRisk()
  } catch (e: any) {
    ElMessage.error('扫描失败：' + (e?.message ?? ''))
  } finally {
    loading.value = false
  }
}
const openRiskHandle = (e: RiskEvent) => {
  riskTarget.value = e
  riskHandleNote.value = ''
  riskDialog.value = true
}
const submitRiskHandle = async () => {
  if (!riskTarget.value) return
  try {
    await handleRisk(riskTarget.value.id, { note: riskHandleNote.value })
    ElMessage.success('风险事件已处置')
    riskDialog.value = false
    await loadRisk()
  } catch (e: any) {
    ElMessage.error('处置失败：' + (e?.message ?? ''))
  }
}

// —— 3-C 反诈核查：高危事件 + 工单 ——
const ANTI_FRAUD_TYPES = ['涉钱关键词', '高频私信']
const antiFraudEvents = computed(() =>
  riskEvents.value.filter((e) => ANTI_FRAUD_TYPES.includes(e.type)),
)
const workOrders = ref<WorkOrder[]>([])
const woSummary = ref<{ total: number; open: number; byRiskType: Record<string, number> } | null>(null)
const loadWorkOrders = async () => {
  try {
    workOrders.value = await fetchWorkOrders()
    woSummary.value = await fetchWorkOrderSummary()
  } catch (e: any) {
    ElMessage.error('加载反诈工单失败：' + (e?.message ?? ''))
  }
}
const woDialog = ref(false)
const woTarget = ref<WorkOrder | null>(null)
const woForm = ref({ status: '已结案', note: '' })
const openWoHandle = (w: WorkOrder) => {
  woTarget.value = w
  woForm.value = { status: '已结案', note: '' }
  woDialog.value = true
}
const submitWoHandle = async () => {
  if (!woTarget.value) return
  try {
    await handleWorkOrder(woTarget.value.id, { status: woForm.value.status, note: woForm.value.note })
    ElMessage.success('反诈工单已处置')
    woDialog.value = false
    await loadWorkOrders()
  } catch (e: any) {
    ElMessage.error('处置失败：' + (e?.message ?? ''))
  }
}

// —— 5.2.4 合规文档 ——
const docs = ref<DocTemplate[]>([])
const docTypes = ['用户协议', '隐私政策', '退费规则', '婚介服务合同']
const activeDocType = ref('用户协议')
const docDraft = ref<{ title: string; content: string }>({ title: '', content: '' })
const loadDocs = async () => {
  try {
    docs.value = await fetchDocs()
    await selectDoc(activeDocType.value)
  } catch (e: any) {
    ElMessage.error('加载合规文档失败：' + (e?.message ?? ''))
  }
}
const selectDoc = async (type: string) => {
  activeDocType.value = type
  try {
    const d = await fetchDoc(type)
    docDraft.value = { title: d.title, content: d.content }
  } catch (e: any) {
    docDraft.value = { title: type, content: '' }
  }
}
const saveDocDraft = async () => {
  try {
    const r = await saveDoc(activeDocType.value, docDraft.value)
    ElMessage.success(`「${r.title}」已保存（v${r.version}）`)
    await loadDocs()
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.message ?? ''))
  }
}
const consentMemberId = ref('')
const consents = ref<ConsentRecord[]>([])
const loadConsents = async () => {
  if (!consentMemberId.value) return ElMessage.warning('请输入会员编号')
  try {
    consents.value = await fetchConsents(consentMemberId.value)
  } catch (e: any) {
    ElMessage.error('加载同意记录失败：' + (e?.message ?? ''))
  }
}

const loadMembers = async () => {
  try {
    loading.value = true
    const list = await fetchMembers({ size: 300 })
    members.value = list as unknown as MemberRow[]
  } catch (e: any) {
    ElMessage.error('加载会员失败：' + (e?.message ?? ''))
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadMembers()
  if (isManager.value) {
    await Promise.all([loadTasks(), loadComplaints(), loadBlacklist(), loadRisk(), loadWorkOrders(), loadDocs()])
  } else {
    await loadDocs()
  }
})
</script>

<template>
  <div class="trust-page">
    <div class="page-head">
      <div>
        <h1>信任合规中心</h1>
        <p>认证标签产品化 · 第三方核验 · 风控举报黑名单 · 合规三件套，构建婚恋平台的信任护城河。</p>
      </div>
    </div>

    <el-tabs v-model="tab" class="tabs">
      <el-tab-pane label="认证标签" name="verify" />
      <el-tab-pane v-if="isManager" label="第三方核验" name="verifyTask" />
      <el-tab-pane v-if="isManager" label="风控中心" name="risk" />
      <el-tab-pane label="合规管理" name="docs" />
    </el-tabs>

    <!-- ============ 5.2.1 认证标签 ============ -->
    <div v-if="tab === 'verify'" v-loading="loading">
      <div class="summary-grid">
        <div class="sum-card">
          <span class="sum-label">实名率</span>
          <span class="sum-value">{{ verifyStats.realName }}%</span>
          <span class="sum-sub">已实名认证会员占比</span>
        </div>
        <div class="sum-card">
          <span class="sum-label">学历核验率</span>
          <span class="sum-value">{{ verifyStats.education }}%</span>
          <span class="sum-sub">学历已核会员占比</span>
        </div>
        <div class="sum-card">
          <span class="sum-label">资产认证率</span>
          <span class="sum-value">{{ verifyStats.asset }}%</span>
          <span class="sum-sub">资产已认证会员占比</span>
        </div>
        <div class="sum-card">
          <span class="sum-label">实人认证率</span>
          <span class="sum-value">{{ verifyStats.realPerson }}%</span>
          <span class="sum-sub">人脸活体实人认证占比</span>
        </div>
        <div class="sum-card">
          <span class="sum-label">已认证会员</span>
          <span class="sum-value ok">{{ verifyStats.verified }}</span>
          <span class="sum-sub">至少完成一项认证</span>
        </div>
      </div>
      <p class="hint">
        认证标签会在会员详情 / 推荐卡片展示「实名 / 学历 / 资产」徽章；智能匹配中，完成认证的会员将获得最高 +14 的信任加权。
      </p>

      <el-table :data="members" stripe border max-height="520">
        <el-table-column prop="name" label="会员" width="120" />
        <el-table-column prop="gender" label="性别" width="70" />
        <el-table-column prop="city" label="城市" width="100" />
        <el-table-column prop="level" label="等级" width="100" />
        <el-table-column label="认证标签" min-width="200">
          <template #default="{ row }">
            <VerifyBadge :real-name="!!row.verifyRealName" :education="!!row.verifyEducation" :asset="!!row.verifyAsset" :real-person="!!row.verifyRealPerson" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="showVerifyDetail(row.id)">查看详情</el-button>
            <el-button v-if="isManager" link type="primary" @click="openRealPerson(row.id, row.name)">实人认证</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-dialog v-model="verifyDetailVisibleProxy" title="认证标签详情" width="420px">
        <template v-if="verifyDetail">
          <VerifyBadge :real-name="verifyDetail.realName" :education="verifyDetail.education" :asset="verifyDetail.asset" :real-person="!!verifyDetail.realPerson" />
          <el-descriptions :column="1" border class="mt">
            <el-descriptions-item label="实名认证">{{ verifyDetail.realName ? '已认证' : '未认证' }}<template v-if="verifyDetail.realNameAt">（{{ verifyDetail.realNameAt }}）</template></el-descriptions-item>
            <el-descriptions-item label="学历核验">{{ verifyDetail.education ? '已核验' : '未核验' }}<template v-if="verifyDetail.educationAt">（{{ verifyDetail.educationAt }}）</template></el-descriptions-item>
            <el-descriptions-item label="资产认证">{{ verifyDetail.asset ? '已认证' : '未认证' }}<template v-if="verifyDetail.assetAt">（{{ verifyDetail.assetAt }}）</template></el-descriptions-item>
            <el-descriptions-item label="实人认证">{{ verifyDetail.realPerson ? '已认证' : '未认证' }}<template v-if="verifyDetail.realPersonAt">（{{ verifyDetail.realPersonAt }}）</template></el-descriptions-item>
          </el-descriptions>
          <div v-if="isManager" class="detail-actions mt">
            <el-button type="primary" size="small" @click="openRealPerson(verifyDetailId, members.find((m) => m.id === verifyDetailId)?.name || verifyDetailId)">发起实人认证</el-button>
          </div>
        </template>
      </el-dialog>
    </div>

    <!-- ============ 5.2.2 第三方核验 ============ -->
    <div v-else-if="tab === 'verifyTask'" v-loading="loading">
      <div class="panel">
        <h3>发起核验任务</h3>
        <div class="form-row">
          <el-select v-model="taskForm.memberId" filterable placeholder="选择会员" style="width: 200px">
            <el-option v-for="o in memberOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-select v-model="taskForm.type" style="width: 140px">
            <el-option label="实名" value="实名" />
            <el-option label="学历" value="学历" />
            <el-option label="资产" value="资产" />
            <el-option label="房车" value="房车" />
            <el-option label="收入" value="收入" />
          </el-select>
          <el-select v-model="taskForm.channel" style="width: 140px">
            <el-option label="学信网" value="学信网" />
            <el-option label="实名公安" value="实名公安" />
            <el-option label="材料审核" value="材料审核" />
            <el-option label="人工" value="人工" />
          </el-select>
          <el-input v-model="taskForm.rawProof" placeholder="核验材料留痕（如证件编号/链接）" style="width: 260px" />
          <el-button type="primary" @click="submitTask">发起核验</el-button>
        </div>
        <p class="hint">实时接口密钥待配置时，核验将进入「待核验」人工留痕，由运营在结果列录入结论；核验「已通过」自动回写对应认证标签。</p>
      </div>

      <el-table :data="tasks" stripe border>
        <el-table-column prop="memberName" label="会员" width="120" />
        <el-table-column prop="type" label="类型" width="90" />
        <el-table-column prop="channel" label="渠道" width="110" />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === '已通过' ? 'success' : row.status === '未通过' ? 'danger' : 'warning'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="result" label="核验结论" min-width="200" />
        <el-table-column prop="operator" label="录入人" width="100" />
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button link type="primary" @click="openRecord(row)">录入结果</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- ============ 5.2.3 风控中心 ============ -->
    <div v-else-if="tab === 'risk'" v-loading="loading">
      <div class="risk-grid">
        <!-- 举报 -->
        <div class="risk-col">
          <div class="col-head"><h3>会员举报</h3><el-button size="small" type="primary" plain @click="submitComplaint">提交举报</el-button></div>
          <div class="form-row compact">
            <el-select v-model="complaintForm.targetMemberId" filterable placeholder="被举报会员" style="width: 160px">
              <el-option v-for="o in memberOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
            <el-select v-model="complaintForm.type" style="width: 110px">
              <el-option label="欺诈" value="欺诈" /><el-option label="婚托" value="婚托" />
              <el-option label="虚假资料" value="虚假资料" /><el-option label="骚扰" value="骚扰" /><el-option label="其他" value="其他" />
            </el-select>
          </div>
          <el-input v-model="complaintForm.content" type="textarea" :rows="2" placeholder="举报说明" class="mb" />
          <el-table :data="complaints" stripe border size="small" max-height="320">
            <el-table-column prop="targetName" label="被举报" width="90" />
            <el-table-column prop="type" label="类型" width="80" />
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }"><el-tag :type="row.status === '待处理' ? 'info' : 'success'" size="small">{{ row.status }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button v-if="isManager && row.status === '待处理'" link type="primary" @click="openHandle(row)">处理</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 黑名单 -->
        <div class="risk-col">
          <div class="col-head"><h3>黑名单</h3></div>
          <div class="form-row compact">
            <el-select v-model="blackForm.memberId" filterable placeholder="拉黑会员" style="width: 160px">
              <el-option v-for="o in memberOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
            <el-date-picker v-model="blackForm.until" type="date" placeholder="解封日(空=永久)" value-format="YYYY-MM-DD" style="width: 160px" />
          </div>
          <el-input v-model="blackForm.reason" placeholder="拉黑原因" class="mb" />
          <el-button v-if="isAdmin" type="danger" plain size="small" @click="submitBlack">加入黑名单</el-button>
          <p v-else class="hint">仅管理员可拉黑 / 解封。</p>
          <el-table :data="blacklist" stripe border size="small" max-height="300" class="mt">
            <el-table-column prop="memberName" label="会员" width="100" />
            <el-table-column prop="reason" label="原因" min-width="120" />
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button v-if="isAdmin && row.status === '生效'" link type="danger" @click="doUnban(row)">解封</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 风险事件 -->
        <div class="risk-col">
          <div class="col-head">
            <h3>风险预警</h3>
            <el-button size="small" type="warning" plain :loading="loading" @click="doScan">扫描风险</el-button>
          </div>
          <div v-if="riskSummary" class="risk-sum">
            共 <b>{{ riskSummary.total }}</b> 条 · 未处置 <b class="warn">{{ riskSummary.unhandled }}</b>
            <span v-for="(v, k) in riskSummary.byType" :key="k" class="risk-chip">{{ k }}:{{ v }}</span>
          </div>
          <el-table :data="riskEvents" stripe border size="small" max-height="320">
            <el-table-column prop="memberName" label="会员" width="90" />
            <el-table-column prop="type" label="类型" width="100" />
            <el-table-column prop="score" label="分" width="50" />
            <el-table-column prop="handled" label="状态" width="80">
              <template #default="{ row }"><el-tag :type="row.handled ? 'success' : 'danger'" size="small">{{ row.handled ? '已处置' : '待处置' }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button v-if="!row.handled" link type="primary" @click="openRiskHandle(row)">处置</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 3-C 反诈核查 -->
        <div class="risk-col antifraud-col" v-if="antiFraudEvents.length">
          <div class="col-head">
            <h3>反诈核查</h3>
            <el-tag type="danger" size="small">{{ antiFraudEvents.length }} 高危</el-tag>
          </div>
          <el-alert type="error" :closable="false" show-icon title="检测到疑似诈骗信号，请引导会员防范并走救助通道" class="mb" />
          <div v-for="e in antiFraudEvents" :key="e.id" class="af-card">
            <div class="af-head"><b>{{ e.memberName }}</b> · {{ e.type }} · <span class="af-score">{{ e.score }}分</span></div>
            <div class="af-detail">{{ e.detail }}</div>
            <div class="af-rescue">
              <div class="af-rescue-title"><app-icon name="shield-check" :size="14" /> 被害人救助路径</div>
              <pre class="af-guidance">{{ e.guidance }}</pre>
            </div>
            <el-button v-if="!e.handled" link type="primary" size="small" @click="openRiskHandle(e)">处置风险</el-button>
          </div>
        </div>
        <div v-else class="risk-col antifraud-col">
          <div class="col-head"><h3>反诈核查</h3></div>
          <p class="hint">暂无高危诈骗信号。运行「扫描风险」可基于会话内容（涉钱关键词）与私信频次自动识别并建工单。</p>
        </div>
      </div>

      <!-- 3-C 反诈工单 -->
      <div v-if="workOrders.length" class="wo-block">
        <div class="col-head">
          <h3>反诈工单（自动建单）</h3>
          <el-tag v-if="woSummary" type="warning" size="small">未结案 {{ woSummary.open }}</el-tag>
        </div>
        <el-table :data="workOrders" stripe border size="small" max-height="360">
          <el-table-column prop="memberName" label="会员" width="100" />
          <el-table-column prop="riskType" label="风险类型" width="100" />
          <el-table-column prop="priority" label="优先级" width="70">
            <template #default="{ row }"><el-tag :type="row.priority === '高' ? 'danger' : 'warning'" size="small">{{ row.priority }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="90">
            <template #default="{ row }"><el-tag :type="row.status === '已结案' ? 'success' : 'danger'" size="small">{{ row.status }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="detail" label="风险详情" min-width="200" show-overflow-tooltip />
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button v-if="row.status !== '已结案'" link type="primary" @click="openWoHandle(row)">处置</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- ============ 5.2.4 合规管理 ============ -->
    <div v-else-if="tab === 'docs'" v-loading="loading">
      <div class="docs-wrap">
        <div class="doc-types">
          <div v-for="t in docTypes" :key="t" class="doc-type" :class="{ active: activeDocType === t }" @click="selectDoc(t)">
            {{ t }}
          </div>
        </div>
        <div class="doc-editor">
          <el-input v-model="docDraft.title" placeholder="文档标题" class="mb" />
          <el-input v-model="docDraft.content" type="textarea" :rows="14" placeholder="文档正文（支持多行文本）" />
          <div class="action-row">
            <el-button v-if="isAdmin" type="primary" @click="saveDocDraft">保存并更新版本</el-button>
            <span v-else class="hint">仅管理员可编辑合规文档。</span>
          </div>
        </div>
      </div>

      <div class="consent-block">
        <h3>合规同意留痕</h3>
        <div class="form-row compact">
          <el-input v-model="consentMemberId" placeholder="输入会员编号查询同意记录" style="width: 240px" />
          <el-button type="primary" plain @click="loadConsents">查询</el-button>
        </div>
        <el-table :data="consents" stripe border size="small" class="mt">
          <el-table-column prop="docType" label="文档" width="140" />
          <el-table-column prop="version" label="版本" width="80" />
          <el-table-column prop="channel" label="场景" width="100" />
          <el-table-column prop="acceptedAt" label="确认时间" min-width="180" />
        </el-table>
        <p class="hint">注册 / 付费流程须强制用户勾选四类文档；后端已提供 <code>/api/docs/consent</code> 留痕与「四类是否全部同意」判定。</p>
      </div>
    </div>

    <!-- 录入核验结果 -->
    <el-dialog v-model="recordDialog" title="录入核验结果" width="440px">
      <el-form v-if="recordTarget" label-width="80px">
        <el-form-item label="会员"><span>{{ recordTarget.memberName }} · {{ recordTarget.type }}</span></el-form-item>
        <el-form-item label="结论">
          <el-select v-model="recordForm.status" style="width: 160px">
            <el-option label="已通过" value="已通过" /><el-option label="未通过" value="未通过" />
            <el-option label="核验中" value="核验中" /><el-option label="待核验" value="待核验" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="recordForm.result" type="textarea" :rows="3" placeholder="核验结论说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="recordDialog = false">取消</el-button>
        <el-button type="primary" @click="submitRecord">保存</el-button>
      </template>
    </el-dialog>

    <!-- 处理举报 -->
    <el-dialog v-model="complaintDialog" title="处理举报" width="440px">
      <el-form v-if="handleTarget" label-width="80px">
        <el-form-item label="被举报"><span>{{ handleTarget.targetName }} · {{ handleTarget.type }}</span></el-form-item>
        <el-form-item label="处理结论">
          <el-select v-model="handleForm.status" style="width: 160px">
            <el-option label="已受理" value="已受理" /><el-option label="已结案" value="已结案" />
            <el-option label="已驳回" value="已驳回" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="handleForm.note" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="complaintDialog = false">取消</el-button>
        <el-button type="primary" @click="submitHandle">保存</el-button>
      </template>
    </el-dialog>

    <!-- 处置风险 -->
    <el-dialog v-model="riskDialog" title="处置风险事件" width="440px">
      <el-form v-if="riskTarget" label-width="80px">
        <el-form-item label="会员"><span>{{ riskTarget.memberName }} · {{ riskTarget.type }}（{{ riskTarget.score }}分）</span></el-form-item>
        <el-form-item label="详情"><span class="hint">{{ riskTarget.detail }}</span></el-form-item>
        <el-form-item label="处置说明">
          <el-input v-model="riskHandleNote" type="textarea" :rows="3" placeholder="处置方式与结果" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="riskDialog = false">取消</el-button>
        <el-button type="primary" @click="submitRiskHandle">标记已处置</el-button>
      </template>
    </el-dialog>

    <!-- 3-C 处置反诈工单 -->
    <el-dialog v-model="woDialog" title="处置反诈工单" width="460px">
      <el-form v-if="woTarget" label-width="88px">
        <el-form-item label="工单"><span>{{ woTarget.title }}</span></el-form-item>
        <el-form-item label="风险类型"><span>{{ woTarget.riskType }}（{{ woTarget.priority }}优先级）</span></el-form-item>
        <el-form-item label="救助引导">
          <pre class="af-guidance">{{ woTarget.guidance }}</pre>
        </el-form-item>
        <el-form-item label="处置结论">
          <el-select v-model="woForm.status" style="width: 160px">
            <el-option label="处理中" value="处理中" /><el-option label="已结案" value="已结案" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="woForm.note" type="textarea" :rows="3" placeholder="处置方式与结果（如已联系会员、已冻结账号、已转交警方）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="woDialog = false">取消</el-button>
        <el-button type="primary" @click="submitWoHandle">保存</el-button>
      </template>
    </el-dialog>

    <!-- 2-A 发起实人认证 -->
    <el-dialog v-model="realPersonDialog" title="发起实人认证" width="460px">
      <el-form label-width="88px">
        <el-form-item label="会员">
          <span>{{ realPersonTargetName }}</span>
        </el-form-item>
        <el-form-item label="活体照">
          <el-upload
            list-type="picture-card"
            :auto-upload="false"
            :limit="1"
            :file-list="liveImageList"
            :on-change="onLiveImageChange"
            :on-remove="onLiveImageRemove"
          >
            <i class="el-icon-plus">＋</i>
          </el-upload>
          <div class="hint">上传一张活体自拍照（或人脸活体 SDK 采集照），用于实名公安 + 人脸活体比对。密钥未配置时自动降级人工审核留痕。</div>
        </el-form-item>
        <el-form-item label="活体令牌">
          <el-input v-model="realPersonForm.authToken" placeholder="人脸活体 SDK 一次性令牌（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="realPersonDialog = false">取消</el-button>
        <el-button type="primary" :loading="realPersonLoading" @click="submitRealPerson">提交认证</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.trust-page { display: flex; flex-direction: column; gap: 20px; }
.page-head h1 { font-size: 1.375rem; font-weight: 600; color: var(--dt-ink-1); margin: 0; }
.page-head p { margin: 6px 0 0; color: var(--dt-ink-3); font-size: 0.8125rem; }
.tabs { margin-top: 0; }
.hint { color: var(--dt-ink-3); font-size: 0.8125rem; margin: 10px 0; }
.mb { margin-bottom: 10px; } .mt { margin-top: 12px; }
.summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; margin: 16px 0; }
.sum-card { background: var(--dt-card-solid); border: 1px solid var(--dt-border); border-radius: var(--dt-radius-md); padding: 16px 18px; display: flex; flex-direction: column; gap: 4px; }
.sum-label { font-size: 0.75rem; color: var(--dt-ink-3); }
.sum-value { font-size: 1.5rem; font-weight: 600; color: var(--dt-ink-1); }
.sum-value.ok { color: var(--dt-success); }
.sum-sub { font-size: 0.75rem; color: var(--dt-ink-3); }
.panel { background: var(--dt-card-solid); border: 1px solid var(--dt-border); border-radius: var(--dt-radius-lg); padding: 16px 18px; margin-bottom: 16px; }
.panel h3 { margin: 0 0 12px; font-size: 0.9375rem; }
.form-row { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
.form-row.compact { margin-bottom: 8px; }
.risk-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 16px; }
.risk-col { background: var(--dt-card-solid); border: 1px solid var(--dt-border); border-radius: var(--dt-radius-lg); padding: 14px 16px; }
.col-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.col-head h3 { margin: 0; font-size: 0.9375rem; }
.risk-sum { font-size: 0.8125rem; color: var(--dt-ink-3); margin-bottom: 10px; }
.risk-sum .warn { color: var(--dt-error); }
.risk-chip { display: inline-block; margin-left: 8px; padding: 0 8px; background: var(--dt-warning-soft); color: var(--dt-warning); border-radius: var(--dt-radius-sm); font-size: 0.75rem; }
.docs-wrap { display: flex; gap: 16px; }
.doc-types { width: 140px; display: flex; flex-direction: column; gap: 8px; }
.doc-type { padding: 10px 12px; border-radius: var(--dt-radius-md); border: 1px solid var(--dt-border); cursor: pointer; font-size: 0.875rem; color: var(--dt-ink-2); }
.doc-type.active { background: var(--dt-primary); color: #fff; border-color: transparent; }
.doc-editor { flex: 1; }
.action-row { margin-top: 12px; }
.consent-block { margin-top: 20px; background: var(--dt-card-solid); border: 1px solid var(--dt-border); border-radius: var(--dt-radius-lg); padding: 16px 18px; }
.consent-block h3 { margin: 0 0 12px; font-size: 0.9375rem; }
code { background: var(--dt-muted); color: var(--dt-ink-2); padding: 1px 6px; border-radius: var(--dt-radius-sm); font-size: 0.75rem; }
.antifraud-col { border-color: rgba(163, 55, 69, 0.24); }
.antifraud-col .col-head h3 { color: var(--dt-error); }
.af-card { background: var(--dt-error-soft); border: 1px solid rgba(163, 55, 69, 0.2); border-radius: var(--dt-radius-md); padding: 10px 12px; margin-bottom: 10px; }
.af-head { font-size: 0.8125rem; margin-bottom: 4px; }
.af-score { color: var(--dt-error); font-weight: 600; }
.af-detail { font-size: 0.75rem; color: var(--dt-ink-3); margin-bottom: 6px; }
.af-rescue { background: var(--dt-card-solid); border: 1px dashed rgba(163, 55, 69, 0.3); border-radius: var(--dt-radius-md); padding: 8px 10px; margin-bottom: 6px; }
.af-rescue-title { display: inline-flex; align-items: center; gap: 5px; font-size: 0.75rem; font-weight: 600; color: var(--dt-error); margin-bottom: 4px; }
.af-guidance { white-space: pre-wrap; word-break: break-word; font-family: inherit; font-size: 0.75rem; line-height: 1.6; color: var(--dt-ink-2); margin: 0; }
.wo-block { margin-top: 16px; background: var(--dt-card-solid); border: 1px solid var(--dt-border); border-radius: var(--dt-radius-lg); padding: 14px 16px; }
</style>
