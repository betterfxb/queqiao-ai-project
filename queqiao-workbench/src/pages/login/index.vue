<script setup lang="ts">
import { ref } from 'vue'
import { api, setToken, setStoredUser, errMsg } from '@/api/index'

const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')
const showPwd = ref(false)

const ROLES: Record<string, string> = {
  ADMIN: '管理员',
  AGENT: '红娘',
  MANAGER: '主管',
}

async function doLogin() {
  if (loading.value) return
  error.value = ''
  if (!username.value.trim() || !password.value) {
    error.value = '请输入账号和密码'
    return
  }
  loading.value = true
  try {
    const r = await api.login(username.value.trim(), password.value)
    setToken(r.token)
    setStoredUser(r.user)
    uni.showToast({ title: `欢迎回来，${r.user.name}`, icon: 'none' })
    setTimeout(() => uni.reLaunch({ url: '/pages/workspace/index' }), 400)
  } catch (e: any) {
    error.value = errMsg(e)
  } finally {
    loading.value = false
  }
}

function fill(name: string) {
  username.value = name
  password.value = '123456'
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
      <text class="brand-title">鹊桥 · 红娘工作台</text>
      <text class="brand-sub">让每一次牵线，都有迹可循</text>
    </view>

    <view class="form-shell">
      <view class="form-shine" />
      <view class="form-inner">
        <view class="field">
          <text class="label">账号</text>
          <input
            class="input"
            v-model="username"
            placeholder="请输入红娘账号"
            placeholder-class="ph"
            :maxlength="30"
          />
        </view>
        <view class="field">
          <text class="label">密码</text>
          <view class="pwd-row">
            <input
              class="input"
              v-model="password"
              :type="showPwd ? 'text' : 'password'"
              placeholder="请输入密码"
              placeholder-class="ph"
              :maxlength="30"
              confirm-type="done"
              @confirm="doLogin"
            />
            <text class="toggle" @click="showPwd = !showPwd">{{ showPwd ? '隐藏' : '显示' }}</text>
          </view>
        </view>

        <view v-if="error" class="error">{{ error }}</view>

        <button class="btn" :disabled="loading" @click="doLogin">
          {{ loading ? '登录中…' : '登 录' }}
        </button>

        <view class="demo">
          <text class="demo-title">演示账号</text>
          <view class="demo-row">
            <text class="demo-tag">管理员</text>
            <text class="demo-name" @click="fill('admin')">admin</text>
            <text class="demo-tag">红娘</text>
            <text class="demo-name" @click="fill('wangjie')">wangjie</text>
          </view>
          <text class="demo-tip">点击账号自动填入（密码 123456）</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  position: relative;
  min-height: 100vh;
  background: transparent;
  padding: 120rpx 48rpx 60rpx;
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
.logo-wrap {
  position: relative;
  margin-bottom: 12rpx;
}
.logo-glow {
  position: absolute;
  inset: -20rpx;
  border-radius: 56rpx;
  background: radial-gradient(circle, rgba(212, 175, 55, 0.45) 0%, rgba(212, 175, 55, 0) 70%);
  filter: blur(20rpx);
}
.logo {
  position: relative;
  width: 128rpx;
  height: 128rpx;
  border-radius: 48rpx;
  background: linear-gradient(135deg, #a8456c 0%, #8b254a 60%, #6d1d3c 100%);
  color: #fff;
  font-size: 72rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 16rpx 40rpx -12rpx rgba(139, 37, 74, 0.5), inset 0 2rpx 0 rgba(255, 255, 255, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.25);
}
.brand-title {
  font-size: 48rpx;
  font-weight: 700;
  color: var(--q-ink-1);
  letter-spacing: -0.02em;
}
.brand-sub {
  font-size: 26rpx;
  color: var(--q-ink-3);
  letter-spacing: 0.06em;
}

.form-shell {
  position: relative;
  z-index: 2;
  width: 100%;
  border-radius: 48rpx;
  background: var(--q-glass-bg-strong);
  backdrop-filter: var(--q-glass-blur);
  -webkit-backdrop-filter: var(--q-glass-blur);
  border: 1px solid var(--q-glass-border);
  box-shadow: var(--q-glass-shadow-lift);
  overflow: hidden;
}
.form-shine {
  position: absolute;
  inset: 0;
  background: var(--q-glass-specular);
  pointer-events: none;
}
.form-inner {
  position: relative;
  padding: 48rpx 40rpx;
  display: flex;
  flex-direction: column;
  gap: 28rpx;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}
.label {
  font-size: 26rpx;
  color: var(--q-ink-2);
  font-weight: 500;
}
.input {
  height: 88rpx;
  padding: 0 28rpx;
  border-radius: var(--q-radius-md);
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid var(--q-glass-border);
  font-size: 30rpx;
  color: var(--q-ink-1);
}
.ph {
  color: var(--q-ink-4);
}
.pwd-row {
  position: relative;
}
.toggle {
  position: absolute;
  right: 24rpx;
  top: 50%;
  transform: translateY(-50%);
  font-size: 26rpx;
  color: var(--q-primary);
}
.error {
  color: var(--q-error);
  font-size: 26rpx;
}
.btn {
  height: 96rpx;
  border-radius: var(--q-radius-md);
  background: linear-gradient(135deg, #a8456c 0%, #8b254a 60%, #6d1d3c 100%);
  color: #fff;
  font-size: 34rpx;
  font-weight: 600;
  line-height: 96rpx;
  border: none;
  box-shadow: 0 16rpx 32rpx -12rpx rgba(139, 37, 74, 0.5), inset 0 2rpx 0 rgba(255, 255, 255, 0.25);
}
.btn[disabled] {
  opacity: 0.7;
}

.demo {
  margin-top: 12rpx;
  padding-top: 28rpx;
  border-top: 1rpx solid rgba(139, 37, 74, 0.08);
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.demo-title {
  font-size: 24rpx;
  color: var(--q-ink-3);
}
.demo-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.demo-tag {
  font-size: 22rpx;
  color: var(--q-gold-dark);
  background: var(--q-gold-soft);
  padding: 4rpx 14rpx;
  border-radius: 12rpx;
}
.demo-name {
  font-size: 26rpx;
  color: var(--q-primary);
  font-weight: 600;
}
.demo-tip {
  font-size: 22rpx;
  color: var(--q-ink-4);
}
</style>
