<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { memberApi } from '@/api/member-portal'
import { errMsg } from '@/api/index'

const id = ref('')
const item = ref<any>(null)
const loading = ref(false)
const acting = ref(false)

onLoad((opt: any) => {
  id.value = opt?.id || ''
  load()
})

async function load() {
  loading.value = true
  try {
    const list = await memberApi.pairs().catch(() => [])
    item.value = list.find((p: any) => p.pair?.id === id.value) || null
  } catch (e: any) {
    uni.showToast({ title: errMsg(e), icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function respond(kind: 'success' | 'reject') {
  if (acting.value) return
  acting.value = true
  try {
    const r = await memberApi.respond(id.value, kind)
    uni.showToast({ title: kind === 'success' ? '已接受' : '已暂不', icon: 'none' })
    item.value = { ...item.value, pair: r.pair, myResponseKind: kind }
  } catch (e: any) {
    uni.showToast({ title: errMsg(e), icon: 'none' })
  } finally {
    acting.value = false
  }
}

function initial(name?: string) {
  return name ? name.slice(0, 1) : '?'
}
function genderTag(g?: string) {
  return g === '男' ? '♂' : g === '女' ? '♀' : ''
}
</script>

<template>
  <view class="page">
    <view class="nav">
      <text class="back" @click="uni.navigateBack()">‹ 返回</text>
      <text class="title">牵线详情</text>
    </view>

    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="!item" class="empty">未找到该牵线</view>

    <template v-else>
      <view class="hero card">
        <view class="avatar" :style="{ background: item.other?.avatarBg || '#8b254a' }">{{ initial(item.other?.name) }}</view>
        <view class="hinfo">
          <view class="name">{{ item.other?.name }} <text class="gt">{{ genderTag(item.other?.gender) }}</text> <text class="age">{{ item.other?.age }}岁</text></view>
          <view class="meta">{{ item.other?.city }} · {{ item.other?.education }} · {{ item.other?.job }}</view>
          <view v-if="item.other?.tags" class="tags">{{ item.other.tags }}</view>
        </view>
      </view>

      <view class="card col">
        <view class="sec-title">匹配进度</view>
        <view class="meta">当前状态：{{ item.pair?.status }}</view>
        <view v-if="item.myResponseKind" class="mine">你的表态：{{ item.myResponseKind === 'success' ? '已接受' : '暂不' }}</view>
      </view>

      <view v-if="item.other?.bio" class="card col">
        <view class="sec-title">TA 的自我介绍</view>
        <text class="bio">{{ item.other.bio }}</text>
      </view>

      <view class="actions">
        <button class="act accept" :disabled="acting" @click="respond('success')">接受牵线</button>
        <button class="act later" :disabled="acting" @click="respond('reject')">暂不考虑</button>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.page { min-height: 100vh; background: var(--q-bg, #fdfbf7); padding: 0 32rpx 60rpx; box-sizing: border-box; }
.nav { display: flex; align-items: center; gap: 16rpx; padding: 24rpx 0 16rpx; }
.back { font-size: 30rpx; color: var(--q-primary); }
.title { font-size: 34rpx; font-weight: 700; color: var(--q-ink-1); }

.card { background: #fff; border-radius: 28rpx; padding: 28rpx; margin-bottom: 20rpx; box-shadow: var(--q-glass-shadow-lift); display: flex; gap: 24rpx; align-items: center; }
.card.col { flex-direction: column; align-items: stretch; gap: 12rpx; }
.hero { margin-top: 8rpx; }
.avatar { width: 110rpx; height: 110rpx; border-radius: 28rpx; color: #fff; font-size: 50rpx; font-weight: 700; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.hinfo { flex: 1; min-width: 0; }
.name { font-size: 34rpx; font-weight: 700; color: var(--q-ink-1); }
.gt { color: #8b254a; margin: 0 6rpx; }
.age { color: var(--q-ink-3); font-weight: 400; font-size: 28rpx; }
.meta { font-size: 24rpx; color: var(--q-ink-3); margin-top: 6rpx; }
.tags { font-size: 22rpx; color: var(--q-gold-dark); margin-top: 6rpx; }
.sec-title { font-size: 28rpx; font-weight: 600; color: var(--q-ink-1); }
.mine { font-size: 26rpx; color: #8b254a; }

.actions { display: flex; gap: 20rpx; margin-top: 16rpx; }
.act { flex: 1; height: 92rpx; border-radius: var(--q-radius-md); font-size: 30rpx; font-weight: 600; line-height: 92rpx; border: none; color: #fff; }
.accept { background: linear-gradient(135deg, #a8456c, #8b254a); }
.later { background: #b9a7ad; }
.act[disabled] { opacity: 0.7; }

.empty { text-align: center; color: var(--q-ink-3); padding: 120rpx 0; font-size: 28rpx; }
</style>
