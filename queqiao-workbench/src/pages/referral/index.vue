<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { memberApi } from '@/api/member-portal'
import { errMsg, maskPhone } from '@/api/index'

const loading = ref(false)
const code = ref('')
const referrals = ref<any[]>([])
const points = ref<any>(null)

const stats = computed(() => {
  const list = referrals.value || []
  const converted = list.filter((r) => r.status === '已转化' || r.status === '已发奖').length
  const rewarded = list.filter((r) => r.status === '已发奖').length
  return { total: list.length, converted, rewarded }
})

const referralLogs = computed(() => {
  const logs = (points.value?.logs as any[]) || []
  return logs.filter((l) => l.type === 'REFERRAL_SUCCESS')
})

async function loadAll() {
  loading.value = true
  try {
    const [c, r, p] = await Promise.all([
      memberApi.myCode().catch(() => ({ code: '' })),
      memberApi.myReferrals().catch(() => []),
      memberApi.myPoints().catch(() => null),
    ])
    code.value = c?.code || ''
    referrals.value = r || []
    points.value = p
  } catch (e: any) {
    uni.showToast({ title: errMsg(e), icon: 'none' })
  } finally {
    loading.value = false
  }
}

onShow(() => loadAll())

function copyCode() {
  if (!code.value) return
  uni.setClipboardData({
    data: code.value,
    success: () => uni.showToast({ title: '推荐码已复制', icon: 'none' }),
  })
}

function share() {
  if (!code.value) return
  const link = `/pages/referral/register?code=${code.value}`
  // 二维码/海报分享：优先系统分享，失败则复制落地页链接
  // #ifdef H5
  uni.setClipboardData({
    data: location.origin + '/#' + link,
    success: () => uni.showToast({ title: '分享链接已复制', icon: 'none' }),
  })
  // #endif
  // #ifndef H5
  uni.showActionSheet({
    itemList: ['复制推荐码', '复制邀请链接'],
    success: (res) => {
      if (res.tapIndex === 0) copyCode()
      else
        uni.setClipboardData({
          data: link,
          success: () => uni.showToast({ title: '邀请链接已复制', icon: 'none' }),
        })
    },
  })
  // #endif
}

function statusClass(s?: string) {
  if (s === '已发奖') return 'ok'
  if (s === '已转化') return 'conv'
  return 'reg'
}
</script>

<template>
  <view class="page">
    <view class="hero">
      <text class="title">邀请好友 · 得积分</text>
      <text class="sub">好友注册并开通会员，你即可获得推荐积分奖励</text>
    </view>

    <view v-if="loading" class="empty">加载中…</view>

    <template v-else>
      <!-- 专属推荐码 -->
      <view class="card code-card">
        <view class="sec-title">我的专属邀请码</view>
        <view class="code-box">
          <text class="code">{{ code || '———' }}</text>
        </view>
        <view class="code-actions">
          <button class="btn ghost" @click="copyCode">复制推荐码</button>
          <button class="btn primary" @click="share">分享邀请</button>
        </view>
      </view>

      <!-- 数据概览 -->
      <view class="stats">
        <view class="stat">
          <text class="num">{{ stats.total }}</text>
          <text class="lbl">累计邀请</text>
        </view>
        <view class="stat">
          <text class="num">{{ stats.converted }}</text>
          <text class="lbl">已转化</text>
        </view>
        <view class="stat">
          <text class="num">{{ stats.rewarded }}</text>
          <text class="lbl">已发奖</text>
        </view>
      </view>

      <!-- 积分奖励 -->
      <view class="card col">
        <view class="row">
          <text class="sec-title">我的积分奖励</text>
          <text class="pts">{{ points?.total || 0 }} 分 · {{ points?.level || '见习红娘' }}</text>
        </view>
        <view v-if="!referralLogs.length" class="hint">暂无推荐成功奖励，邀请好友开通会员即得积分～</view>
        <view v-for="(l, i) in referralLogs" :key="i" class="log">
          <view class="log-l">
            <text class="log-t">{{ l.typeLabel || '推荐成功' }}</text>
            <text class="log-r">+{{ l.delta }} 分</text>
          </view>
          <text class="log-time">{{ l.createdAt || '' }}</text>
        </view>
      </view>

      <!-- 推荐记录 -->
      <view class="card col">
        <view class="sec-title">推荐记录</view>
        <view v-if="!referrals.length" class="hint">还没有邀请记录，快去分享专属码吧～</view>
        <view v-for="r in referrals" :key="r.id" class="rec">
          <view class="rec-l">
            <text class="rec-name">{{ r.refereeName || maskPhone(r.refereePhone) }}</text>
            <text class="rec-phone">{{ maskPhone(r.refereePhone) }}</text>
          </view>
          <text class="badge" :class="statusClass(r.status)">{{ r.status }}</text>
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

.card { background: #fff; border-radius: 28rpx; padding: 28rpx; margin-bottom: 20rpx; box-shadow: var(--q-glass-shadow-lift); }
.card.col { display: flex; flex-direction: column; gap: 14rpx; }
.sec-title { font-size: 28rpx; font-weight: 600; color: var(--q-ink-1); }

.code-card { background: linear-gradient(135deg, #a8456c, #8b254a); color: #fff; box-shadow: var(--q-glass-shadow-lift); }
.code-card .sec-title { color: rgba(255, 255, 255, 0.85); }
.code-box { margin: 16rpx 0; padding: 28rpx; background: rgba(255, 255, 255, 0.14); border-radius: 20rpx; text-align: center; }
.code { font-size: 56rpx; font-weight: 800; letter-spacing: 6rpx; color: #fff; }
.code-actions { display: flex; gap: 16rpx; }
.btn { flex: 1; height: 76rpx; line-height: 76rpx; border-radius: 999rpx; font-size: 28rpx; border: none; }
.btn.primary { background: #fff; color: #8b254a; font-weight: 600; }
.btn.ghost { background: rgba(255, 255, 255, 0.18); color: #fff; }
.btn::after { border: none; }

.stats { display: flex; gap: 16rpx; margin-bottom: 20rpx; }
.stat { flex: 1; background: #fff; border-radius: 24rpx; padding: 24rpx 0; text-align: center; box-shadow: var(--q-glass-shadow-lift); }
.num { font-size: 44rpx; font-weight: 800; color: #8b254a; display: block; }
.lbl { font-size: 22rpx; color: var(--q-ink-3); margin-top: 4rpx; display: block; }

.row { display: flex; justify-content: space-between; align-items: center; }
.pts { font-size: 26rpx; color: var(--q-gold-dark); font-weight: 600; }
.hint { font-size: 24rpx; color: var(--q-ink-4); }
.log { border-top: 1rpx solid #f0ece6; padding-top: 14rpx; }
.log-l { display: flex; justify-content: space-between; }
.log-t { font-size: 26rpx; color: var(--q-ink-1); }
.log-r { font-size: 26rpx; color: #2e7d32; font-weight: 600; }
.log-time { font-size: 22rpx; color: var(--q-ink-4); }

.rec { display: flex; justify-content: space-between; align-items: center; border-top: 1rpx solid #f0ece6; padding-top: 14rpx; }
.rec-l { display: flex; flex-direction: column; }
.rec-name { font-size: 28rpx; color: var(--q-ink-1); font-weight: 500; }
.rec-phone { font-size: 22rpx; color: var(--q-ink-4); margin-top: 4rpx; }
.badge { font-size: 22rpx; padding: 4rpx 16rpx; border-radius: 999rpx; }
.badge.reg { background: #f3e7ec; color: #8b254a; }
.badge.conv { background: #fff3e0; color: #c77a1a; }
.badge.ok { background: #e6f4ea; color: #2e7d32; }
</style>
