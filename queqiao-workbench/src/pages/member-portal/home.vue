<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { memberApi } from '@/api/member-portal'
import { errMsg, maskPhone } from '@/api/index'

const tab = ref<'pairs' | 'activities' | 'mine'>('pairs')
const loading = ref(false)

const pairs = ref<any[]>([])
const activities = ref<any[]>([])
const me = ref<any>(null)
const verify = ref<any>(null)

async function loadAll() {
  loading.value = true
  try {
    const [p, a, m, v] = await Promise.all([
      memberApi.pairs().catch(() => []),
      memberApi.activities().catch(() => []),
      memberApi.me().catch(() => null),
      memberApi.verify().catch(() => null),
    ])
    pairs.value = p
    activities.value = a
    me.value = m
    verify.value = v
  } catch (e: any) {
    uni.showToast({ title: errMsg(e), icon: 'none' })
  } finally {
    loading.value = false
  }
}

onShow(() => loadAll())

function openPair(p: any) {
  const id = p.pair?.id
  if (id) uni.navigateTo({ url: `/pages/member-portal/match-detail?id=${id}` })
}

async function enroll(a: any) {
  const act = a.activity
  try {
    const r = await memberApi.enroll(act.id)
    uni.showToast({ title: r.already ? '你已报名' : '报名成功', icon: 'none' })
    a.enrolled = true
  } catch (e: any) {
    uni.showToast({ title: errMsg(e), icon: 'none' })
  }
}

function initial(name?: string) {
  return name ? name.slice(0, 1) : '?'
}
function goReferral() {
  uni.navigateTo({ url: '/pages/referral/index' })
}
function goGuardian() {
  uni.navigateTo({ url: '/pages/guardian/index' })
}
function genderTag(g?: string) {
  return g === '男' ? '♂' : g === '女' ? '♀' : ''
}
const verifyItems = [
  { key: 'verifyRealName', label: '实名认证' },
  { key: 'verifyEducation', label: '学历认证' },
  { key: 'verifyAsset', label: '资产认证' },
  { key: 'verifyRealPerson', label: '实人认证' },
]

/** 2-A 自动实人认证：选择活体照 → 读为 base64 → 提交后端实名公安 + 人脸活体比对 */
async function doRealPerson() {
  uni.chooseImage({
    count: 1,
    sourceType: ['camera', 'album'],
    success: async (res: any) => {
      const path = (res.tempFilePaths && res.tempFilePaths[0]) || (res.tempFiles && res.tempFiles[0]?.path)
      if (!path) return
      let base64 = ''
      try {
        base64 = await new Promise<string>((resolve, reject) => {
          // #ifdef H5
          const xhr = new XMLHttpRequest()
          xhr.open('GET', path, true)
          xhr.responseType = 'blob'
          xhr.onload = () => {
            const reader = new FileReader()
            reader.onloadend = () => resolve(String(reader.result))
            reader.readAsDataURL(xhr.response)
          }
          xhr.onerror = () => reject(new Error('读取图片失败'))
          xhr.send()
          // #endif
          // #ifndef H5
          uni.getFileSystemManager().readFile({
            filePath: path,
            encoding: 'base64',
            success: (r: any) => resolve('data:image/jpeg;base64,' + r.data),
            fail: () => reject(new Error('读取图片失败')),
          })
          // #endif
        })
      } catch {
        uni.showToast({ title: '读取活体照失败', icon: 'none' })
        return
      }
      try {
        uni.showLoading({ title: '认证中…', mask: true })
        const r = await memberApi.realPerson({ authToken: '', liveImage: base64 })
        await loadAll()
        uni.hideLoading()
        const st = r?.status
        if (st === '已通过') uni.showToast({ title: '实人认证通过', icon: 'success' })
        else if (st === '待核验') uni.showToast({ title: '已提交，等待人工审核', icon: 'none' })
        else uni.showToast({ title: '实人认证未通过', icon: 'none' })
      } catch (e: any) {
        uni.hideLoading()
        uni.showToast({ title: errMsg(e), icon: 'none' })
      }
    },
    fail: () => {},
  })
}
</script>

<template>
  <view class="page">
    <view class="topbar">
      <text class="hi">你好，{{ me?.name || '会员' }}</text>
      <text class="sub">为你推送的缘分都在这里</text>
    </view>

    <view class="tabs">
      <view class="tab" :class="{ active: tab === 'pairs' }" @click="tab = 'pairs'">牵线</view>
      <view class="tab" :class="{ active: tab === 'activities' }" @click="tab = 'activities'">活动</view>
      <view class="tab" :class="{ active: tab === 'mine' }" @click="tab = 'mine'">我的</view>
    </view>

    <view v-if="loading" class="empty">加载中…</view>

    <!-- 牵线 -->
    <view v-else-if="tab === 'pairs'">
      <view v-if="!pairs.length" class="empty">暂无进行中的牵线，红娘正在为你物色～</view>
      <view v-for="p in pairs" :key="p.pair?.id" class="card" @click="openPair(p)">
        <view class="avatar" :style="{ background: p.other?.avatarBg || '#8b254a' }">{{ initial(p.other?.name) }}</view>
        <view class="info">
          <view class="name">
            {{ p.other?.name }}
            <text class="gt">{{ genderTag(p.other?.gender) }}</text>
            <text class="age">{{ p.other?.age }}岁</text>
          </view>
          <view class="meta">{{ p.other?.city }} · {{ p.other?.education }} · {{ p.other?.job }}</view>
          <view v-if="p.other?.tags" class="tags">{{ p.other.tags }}</view>
          <view class="status">
            状态：{{ p.pair?.status }}
            <text v-if="p.myResponseKind" class="mine">（我已{{ p.myResponseKind === 'success' ? '接受' : '暂不' }}）</text>
          </view>
        </view>
        <text class="arrow">›</text>
      </view>
    </view>

    <!-- 活动 -->
    <view v-else-if="tab === 'activities'">
      <view v-if="!activities.length" class="empty">近期暂无开放活动</view>
      <view v-for="a in activities" :key="a.activity?.id" class="card col">
        <view class="act-title">{{ a.activity?.title }}</view>
        <view class="meta">{{ a.activity?.date }} · {{ a.activity?.time }} · {{ a.activity?.place }}</view>
        <view class="act-foot">
          <text class="cap">已报名 {{ a.activity?.signed || 0 }}/{{ a.activity?.capacity || '—' }}</text>
          <button v-if="!a.enrolled" class="mini" @click.stop="enroll(a)">报名</button>
          <text v-else class="done">已报名</text>
        </view>
      </view>
    </view>

    <!-- 我的 -->
    <view v-else class="mine-wrap">
      <view class="card col">
        <view class="row"><text class="k">姓名</text><text class="v">{{ me?.name }}</text></view>
        <view class="row"><text class="k">性别</text><text class="v">{{ me?.gender }} · {{ me?.age }}岁</text></view>
        <view class="row"><text class="k">城市</text><text class="v">{{ me?.city }}</text></view>
        <view class="row"><text class="k">手机</text><text class="v">{{ maskPhone(me?.phone) }}</text></view>
        <view class="row"><text class="k">等级</text><text class="v">{{ me?.level || '—' }}</text></view>
      </view>

      <view class="card col">
        <view class="sec-title">认证状态</view>
        <view v-for="it in verifyItems" :key="it.key" class="row">
          <text class="k">{{ it.label }}</text>
          <text class="v" :class="verify?.[it.key] ? 'ok' : 'no'">{{ verify?.[it.key] ? '已认证' : '未认证' }}</text>
        </view>
        <view class="row"><text class="k">综合认证</text><text class="v" :class="verify?.verified ? 'ok' : 'no'">{{ verify?.verified ? '已认证' : '未认证' }}</text></view>
        <button class="real-btn" @click="doRealPerson">{{ verify?.verifyRealPerson ? '重新实人认证' : '去实人认证' }}</button>
      </view>

      <view class="card col">
        <view class="row"><text class="sec-title">邀请有礼</text><text class="arrow-sm">›</text></view>
        <text class="hint">邀请好友注册并开通会员，你得积分奖励</text>
        <button class="ref-btn" @click="goReferral">我的专属邀请码</button>
      </view>

      <view class="card col">
        <view class="row"><text class="sec-title">家长管理</text><text class="arrow-sm">›</text></view>
        <text class="hint">授权家长代看/代沟通，更安心</text>
        <button class="guard-btn" @click="goGuardian">绑定与授权家长</button>
      </view>

      <view v-if="me?.bio" class="card col">
        <view class="sec-title">自我介绍</view>
        <text class="bio">{{ me.bio }}</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page { min-height: 100vh; background: var(--q-bg, #fdfbf7); padding: 40rpx 32rpx 60rpx; box-sizing: border-box; }
.topbar { margin-bottom: 24rpx; }
.hi { font-size: 40rpx; font-weight: 700; color: var(--q-ink-1); display: block; }
.sub { font-size: 24rpx; color: var(--q-ink-3); }

.tabs { display: flex; gap: 12rpx; background: #fff; border-radius: 999rpx; padding: 8rpx; margin-bottom: 24rpx; box-shadow: var(--q-glass-shadow-lift); }
.tab { flex: 1; text-align: center; padding: 18rpx 0; font-size: 28rpx; color: var(--q-ink-3); border-radius: 999rpx; }
.tab.active { background: linear-gradient(135deg, #a8456c, #8b254a); color: #fff; font-weight: 600; }

.empty { text-align: center; color: var(--q-ink-3); padding: 120rpx 0; font-size: 28rpx; }

.card { background: #fff; border-radius: 28rpx; padding: 28rpx; margin-bottom: 20rpx; box-shadow: var(--q-glass-shadow-lift); display: flex; gap: 24rpx; align-items: center; }
.card.col { flex-direction: column; align-items: stretch; gap: 14rpx; }
.avatar { width: 96rpx; height: 96rpx; border-radius: 24rpx; color: #fff; font-size: 44rpx; font-weight: 700; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.info { flex: 1; min-width: 0; }
.name { font-size: 32rpx; font-weight: 600; color: var(--q-ink-1); }
.gt { color: #8b254a; margin: 0 6rpx; }
.age { color: var(--q-ink-3); font-weight: 400; font-size: 26rpx; }
.meta { font-size: 24rpx; color: var(--q-ink-3); margin-top: 6rpx; }
.tags { font-size: 22rpx; color: var(--q-gold-dark); margin-top: 6rpx; }
.status { font-size: 24rpx; color: var(--q-ink-2); margin-top: 8rpx; }
.mine { color: #8b254a; }
.arrow { color: var(--q-ink-4); font-size: 40rpx; }

.act-title { font-size: 30rpx; font-weight: 600; color: var(--q-ink-1); }
.act-foot { display: flex; align-items: center; justify-content: space-between; margin-top: 8rpx; }
.cap { font-size: 24rpx; color: var(--q-ink-3); }
.mini { background: linear-gradient(135deg, #a8456c, #8b254a); color: #fff; font-size: 26rpx; padding: 0 36rpx; height: 64rpx; line-height: 64rpx; border-radius: 999rpx; border: none; }
.done { font-size: 26rpx; color: var(--q-primary); }

.row { display: flex; justify-content: space-between; font-size: 28rpx; }
.k { color: var(--q-ink-3); }
.v { color: var(--q-ink-1); font-weight: 500; }
.ok { color: #2e7d32; }
.no { color: var(--q-ink-4); }
.sec-title { font-size: 28rpx; font-weight: 600; color: var(--q-ink-1); }
.bio { font-size: 26rpx; color: var(--q-ink-2); line-height: 1.6; }
.real-btn { margin-top: 16rpx; background: linear-gradient(135deg, #a8456c, #8b254a); color: #fff; font-size: 28rpx; height: 76rpx; line-height: 76rpx; border-radius: 999rpx; border: none; }
.real-btn::after { border: none; }
.hint { font-size: 24rpx; color: var(--q-ink-3); }
.arrow-sm { color: var(--q-ink-4); font-size: 36rpx; }
.ref-btn { margin-top: 14rpx; background: linear-gradient(135deg, #d4af37, #b8902a); color: #fff; font-size: 28rpx; height: 76rpx; line-height: 76rpx; border-radius: 999rpx; border: none; }
.ref-btn::after { border: none; }
.guard-btn { margin-top: 14rpx; background: linear-gradient(135deg, #8b254a, #6e1c3a); color: #fff; font-size: 28rpx; height: 76rpx; line-height: 76rpx; border-radius: 999rpx; border: none; }
.guard-btn::after { border: none; }
</style>
