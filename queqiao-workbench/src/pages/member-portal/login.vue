<script setup lang="ts">
import { ref } from 'vue'
import { memberApi, memberLoginAndStore } from '@/api/member-portal'
import { errMsg } from '@/api/index'

const phone = ref('')
const loading = ref(false)
const error = ref('')

async function doLogin() {
  error.value = ''
  const p = phone.value.trim()
  if (!/^1\d{10}$/.test(p)) {
    error.value = '请输入正确的 11 位手机号'
    return
  }
  loading.value = true
  try {
    const r = await memberApi.login({ phone: p })
    memberLoginAndStore(r)
    uni.showToast({ title: `您好，${r.user.name}`, icon: 'none' })
    setTimeout(() => uni.reLaunch({ url: '/pages/member-portal/home' }), 400)
  } catch (e: any) {
    error.value = errMsg(e)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <view class="page">
    <aurora-bg dense />
    <view class="brand">
      <view class="logo-wrap anim-float">
        <view class="logo-glow" />
        <view class="logo">鹊</view>
      </view>
      <text class="brand-title">鹊桥 · 会员端</text>
      <text class="brand-sub">专属你的缘分，触手可及</text>
    </view>

    <view class="form-shell">
      <view class="form-shine" />
      <view class="form-inner">
        <view class="field">
          <text class="label">手机号</text>
          <input
            class="input"
            v-model="phone"
            type="number"
            placeholder="请输入建档时登记的手机号"
            placeholder-class="ph"
            :maxlength="11"
            confirm-type="done"
            @confirm="doLogin"
          />
        </view>

        <view v-if="error" class="error">{{ error }}</view>

        <button class="btn" :disabled="loading" @click="doLogin">
          {{ loading ? '登录中…' : '登 录' }}
        </button>

        <text class="tip">登录即代表你已阅读并同意《用户协议》与《隐私政策》</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  position: relative;
  min-height: 100vh;
  background: transparent;
  padding: 140rpx 48rpx 60rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.brand {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 72rpx;
}
.logo-wrap { position: relative; margin-bottom: 12rpx; }
.logo-glow {
  position: absolute; inset: -20rpx;
  border-radius: 56rpx;
  background: radial-gradient(circle, rgba(212, 175, 55, 0.45) 0%, rgba(212, 175, 55, 0) 70%);
  filter: blur(20rpx);
}
.logo {
  position: relative;
  width: 128rpx; height: 128rpx; border-radius: 48rpx;
  background: linear-gradient(135deg, #a8456c 0%, #8b254a 60%, #6d1d3c 100%);
  color: #fff; font-size: 72rpx; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 16rpx 40rpx -12rpx rgba(139, 37, 74, 0.5), inset 0 2rpx 0 rgba(255, 255, 255, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.25);
}
.brand-title { font-size: 48rpx; font-weight: 700; color: var(--q-ink-1); letter-spacing: -0.02em; }
.brand-sub { font-size: 26rpx; color: var(--q-ink-3); letter-spacing: 0.06em; }

.form-shell {
  position: relative; z-index: 2; width: 100%;
  border-radius: 48rpx;
  background: var(--q-glass-bg-strong);
  backdrop-filter: var(--q-glass-blur);
  -webkit-backdrop-filter: var(--q-glass-blur);
  border: 1px solid var(--q-glass-border);
  box-shadow: var(--q-glass-shadow-lift);
  overflow: hidden;
}
.form-shine { position: absolute; inset: 0; background: var(--q-glass-specular); pointer-events: none; }
.form-inner { position: relative; padding: 48rpx 40rpx; display: flex; flex-direction: column; gap: 28rpx; }
.field { display: flex; flex-direction: column; gap: 12rpx; }
.label { font-size: 26rpx; color: var(--q-ink-2); font-weight: 500; }
.input {
  height: 88rpx; padding: 0 28rpx; border-radius: var(--q-radius-md);
  background: rgba(255, 255, 255, 0.85); border: 1px solid var(--q-glass-border);
  font-size: 30rpx; color: var(--q-ink-1);
}
.ph { color: var(--q-ink-4); }
.error { color: var(--q-error); font-size: 26rpx; }
.btn {
  height: 96rpx; border-radius: var(--q-radius-md);
  background: linear-gradient(135deg, #a8456c 0%, #8b254a 60%, #6d1d3c 100%);
  color: #fff; font-size: 34rpx; font-weight: 600; line-height: 96rpx; border: none;
  box-shadow: 0 16rpx 32rpx -12rpx rgba(139, 37, 74, 0.5), inset 0 2rpx 0 rgba(255, 255, 255, 0.25);
}
.btn[disabled] { opacity: 0.7; }
.tip { font-size: 22rpx; color: var(--q-ink-4); text-align: center; }
</style>
