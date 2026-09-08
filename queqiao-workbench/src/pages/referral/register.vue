<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { memberApi, memberLoginAndStore } from '@/api/member-portal'
import { errMsg } from '@/api/index'

const code = ref('')
const name = ref('')
const phone = ref('')
const submitting = ref(false)

onLoad((opts: any) => {
  if (opts?.code) code.value = String(opts.code)
})

async function submit() {
  if (!code.value) {
    uni.showToast({ title: '缺少推荐码', icon: 'none' })
    return
  }
  if (!/^1[3-9]\d{9}$/.test(phone.value || '')) {
    uni.showToast({ title: '请输入正确的手机号', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    uni.showLoading({ title: '提交中…', mask: true })
    await memberApi.registerByCode({ code: code.value, refereeName: name.value, refereePhone: phone.value })
    // 完成「好友注册」：以手机号建立会员身份并落地登录态
    const login = await memberApi.login({ phone: phone.value })
    memberLoginAndStore(login)
    uni.hideLoading()
    uni.showToast({ title: '注册成功，去开通会员吧', icon: 'success' })
    setTimeout(() => uni.reLaunch({ url: '/pages/member-portal/home' }), 900)
  } catch (e: any) {
    uni.hideLoading()
    uni.showToast({ title: errMsg(e), icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="page">
    <view class="hero">
      <text class="title">好友邀请你加入鹊桥</text>
      <text class="sub">完善信息注册会员，开启你的专属缘分</text>
    </view>

    <view class="card col">
      <view class="field">
        <text class="lbl">推荐码</text>
        <input class="inp" :value="code" disabled placeholder="由邀请人分享" />
      </view>
      <view class="field">
        <text class="lbl">昵称</text>
        <input class="inp" v-model="name" placeholder="怎么称呼你" />
      </view>
      <view class="field">
        <text class="lbl">手机号</text>
        <input class="inp" v-model="phone" type="number" maxlength="11" placeholder="用于接收牵线通知" />
      </view>
      <button class="submit" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中…' : '注册并领取专属服务' }}
      </button>
    </view>

    <view class="tip">注册即表示同意《用户协议》与《隐私政策》，信息仅用于牵线匹配。</view>
  </view>
</template>

<style lang="scss" scoped>
.page { min-height: 100vh; background: var(--q-bg, #fdfbf7); padding: 60rpx 32rpx; box-sizing: border-box; }
.hero { margin-bottom: 32rpx; }
.title { font-size: 40rpx; font-weight: 700; color: var(--q-ink-1); display: block; }
.sub { font-size: 24rpx; color: var(--q-ink-3); margin-top: 8rpx; display: block; }

.card { background: #fff; border-radius: 28rpx; padding: 28rpx; box-shadow: var(--q-glass-shadow-lift); display: flex; flex-direction: column; gap: 24rpx; }
.field { display: flex; flex-direction: column; gap: 10rpx; }
.lbl { font-size: 26rpx; color: var(--q-ink-3); }
.inp { background: #f7f3ee; border-radius: 16rpx; height: 84rpx; padding: 0 24rpx; font-size: 28rpx; color: var(--q-ink-1); }
.submit { margin-top: 8rpx; background: linear-gradient(135deg, #a8456c, #8b254a); color: #fff; font-size: 30rpx; height: 88rpx; line-height: 88rpx; border-radius: 999rpx; border: none; }
.submit[disabled] { opacity: 0.6; }
.submit::after { border: none; }
.tip { font-size: 22rpx; color: var(--q-ink-4); margin-top: 24rpx; text-align: center; }
</style>
