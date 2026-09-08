<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { memberApi } from '@/api/member-portal'
import { errMsg, maskPhone } from '@/api/index'

const loading = ref(false)
const guardians = ref<any[]>([])
const families = ref<any[]>([]) // 家长视角：我代管的子女

// 绑定家长表单
const form = ref({ guardianPhone: '', guardianName: '', relation: '父' })
const relations = ['父', '母', '其他']
const binding = ref(false)

async function loadAll() {
  loading.value = true
  try {
    const [g, f] = await Promise.all([
      memberApi.myGuardians().catch(() => []),
      memberApi.guardianConversations().catch(() => []),
    ])
    guardians.value = g || []
    families.value = f || []
  } catch (e: any) {
    uni.showToast({ title: errMsg(e), icon: 'none' })
  } finally {
    loading.value = false
  }
}
onShow(loadAll)

async function bind() {
  if (!/^1[3-9]\d{9}$/.test(form.value.guardianPhone || '')) {
    uni.showToast({ title: '请输入正确的家长手机号', icon: 'none' })
    return
  }
  binding.value = true
  try {
    await memberApi.bindGuardian({ ...form.value })
    uni.showToast({ title: '已提交，待你授权', icon: 'none' })
    form.value = { guardianPhone: '', guardianName: '', relation: '父' }
    await loadAll()
  } catch (e: any) {
    uni.showToast({ title: errMsg(e), icon: 'none' })
  } finally {
    binding.value = false
  }
}

async function authorize(id: string) {
  try {
    await memberApi.authorizeGuardian(id)
    uni.showToast({ title: '已授权', icon: 'success' })
    await loadAll()
  } catch (e: any) {
    uni.showToast({ title: errMsg(e), icon: 'none' })
  }
}

async function revoke(id: string) {
  try {
    await memberApi.revokeGuardian(id)
    uni.showToast({ title: '已撤销', icon: 'none' })
    await loadAll()
  } catch (e: any) {
    uni.showToast({ title: errMsg(e), icon: 'none' })
  }
}

async function send(convId: string, text: string) {
  const content = (text || '').trim()
  if (!content) return
  try {
    await memberApi.guardianSend(convId, content)
    uni.showToast({ title: '已发送', icon: 'none' })
    await loadAll()
  } catch (e: any) {
    uni.showToast({ title: errMsg(e), icon: 'none' })
  }
}

function statusClass(s?: string) {
  if (s === '已授权') return 'ok'
  if (s === '待授权') return 'pend'
  return 'rev'
}
</script>

<template>
  <view class="page">
    <view class="hero">
      <text class="title">家长管理</text>
      <text class="sub">授权家长代看/代沟通，匹配成功后家长先聊更安心</text>
    </view>

    <view v-if="loading" class="empty">加载中…</view>

    <template v-else>
      <!-- 子女视角：我绑定的家长 -->
      <view class="card col">
        <view class="sec-title">绑定家长</view>
        <view class="bind-row">
          <input class="inp" v-model="form.guardianPhone" type="number" maxlength="11" placeholder="家长手机号" />
          <input class="inp" v-model="form.guardianName" placeholder="家长称呼" />
        </view>
        <view class="rel">
          <view v-for="r in relations" :key="r" class="rel-opt" :class="{ on: form.relation === r }" @click="form.relation = r">{{ r }}</view>
        </view>
        <button class="btn primary" :disabled="binding" @click="bind">{{ binding ? '提交中…' : '绑定家长' }}</button>
      </view>

      <view class="card col">
        <view class="sec-title">我绑定的家长</view>
        <view v-if="!guardians.length" class="hint">尚未绑定家长，绑定后由你授权代管。</view>
        <view v-for="g in guardians" :key="g.id" class="g">
          <view class="g-l">
            <text class="g-name">{{ g.guardianName || maskPhone(g.guardianPhone) }}</text>
            <text class="g-phone">{{ maskPhone(g.guardianPhone) }} · {{ g.relation || '—' }}</text>
          </view>
          <view class="g-r">
            <text class="badge" :class="statusClass(g.authStatus)">{{ g.authStatus }}</text>
            <view class="acts">
              <text v-if="g.authStatus === '待授权'" class="lk ok" @click="authorize(g.id)">授权</text>
              <text v-if="g.authStatus === '已授权'" class="lk rev" @click="revoke(g.id)">撤销</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 家长视角：我代管的子女 -->
      <view v-if="families.length" class="card col">
        <view class="sec-title">我代管的子女</view>
        <view v-for="f in families" :key="f.memberId" class="fam">
          <text class="fam-name">{{ f.memberName }}</text>
          <view v-if="!f.conversations || !f.conversations.length" class="hint">暂无可查看的会话</view>
          <view v-for="c in (f.conversations || [])" :key="c.id" class="conv">
            <view class="conv-top">
              <text class="conv-name">{{ c.name }}</text>
              <text class="conv-prev">{{ c.preview || '（暂无消息）' }}</text>
            </view>
            <view class="conv-send">
              <input class="inp flex1" :value="''" :data-cid="c.id" placeholder="以家长身份代沟通…"
                     @confirm="(e:any)=>send(c.id, e.detail.value)" />
              <text class="lk ok" @click="send(c.id, '')">发送</text>
            </view>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.page { min-height: 100vh; background: var(--q-bg, #fdfbf7); padding: 40rpx 32rpx 60rpx; box-sizing: border-box; }
.hero { margin-bottom: 24rpx; }
.title { font-size: 40rpx; font-weight: 700; color: var(--q-ink-1); display: block; }
.sub { font-size: 24rpx; color: var(--q-ink-3); margin-top: 8rpx; display: block; }
.empty { text-align: center; color: var(--q-ink-3); padding: 120rpx 0; font-size: 28rpx; }

.card { background: #fff; border-radius: 28rpx; padding: 28rpx; margin-bottom: 20rpx; box-shadow: var(--q-glass-shadow-lift); display: flex; flex-direction: column; gap: 14rpx; }
.sec-title { font-size: 28rpx; font-weight: 600; color: var(--q-ink-1); }
.hint { font-size: 24rpx; color: var(--q-ink-4); }

.bind-row { display: flex; gap: 12rpx; }
.inp { background: #f7f3ee; border-radius: 16rpx; height: 80rpx; padding: 0 20rpx; font-size: 28rpx; color: var(--q-ink-1); }
.bind-row .inp { flex: 1; }
.flex1 { flex: 1; }
.rel { display: flex; gap: 12rpx; }
.rel-opt { padding: 12rpx 32rpx; border-radius: 999rpx; background: #f2ece6; color: var(--q-ink-3); font-size: 26rpx; }
.rel-opt.on { background: linear-gradient(135deg, #a8456c, #8b254a); color: #fff; }
.btn { margin-top: 8rpx; border-radius: 999rpx; height: 80rpx; line-height: 80rpx; border: none; font-size: 28rpx; }
.btn.primary { background: linear-gradient(135deg, #a8456c, #8b254a); color: #fff; }
.btn[disabled] { opacity: 0.6; }
.btn::after { border: none; }

.g { display: flex; justify-content: space-between; align-items: center; border-top: 1rpx solid #f0ece6; padding-top: 14rpx; }
.g-l { display: flex; flex-direction: column; }
.g-name { font-size: 28rpx; color: var(--q-ink-1); font-weight: 500; }
.g-phone { font-size: 22rpx; color: var(--q-ink-4); margin-top: 4rpx; }
.g-r { display: flex; flex-direction: column; align-items: flex-end; gap: 8rpx; }
.badge { font-size: 22rpx; padding: 4rpx 16rpx; border-radius: 999rpx; }
.badge.ok { background: #e6f4ea; color: #2e7d32; }
.badge.pend { background: #fff3e0; color: #c77a1a; }
.badge.rev { background: #f0ece6; color: var(--q-ink-4); }
.acts { display: flex; gap: 16rpx; }
.lk { font-size: 26rpx; }
.lk.ok { color: #2e7d32; }
.lk.rev { color: #b3261e; }

.fam { border-top: 1rpx solid #f0ece6; padding-top: 14rpx; }
.fam-name { font-size: 28rpx; font-weight: 600; color: var(--q-ink-1); }
.conv { background: #faf6f1; border-radius: 16rpx; padding: 16rpx; margin-top: 12rpx; }
.conv-top { display: flex; flex-direction: column; }
.conv-name { font-size: 26rpx; color: var(--q-ink-1); font-weight: 500; }
.conv-prev { font-size: 22rpx; color: var(--q-ink-4); margin-top: 4rpx; }
.conv-send { display: flex; gap: 12rpx; align-items: center; margin-top: 10rpx; }
</style>
