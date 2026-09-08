<script setup lang="ts">
/**
 * 我的 — 红娘个人中心
 * 模块：毛玻璃头部（头像/姓名/角色/手机） / 业绩概览 Bento / 业务入口列表 / 账号设置 / 退出登录
 */
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { api, clearAuth, getStoredUser, setStoredUser } from '@/api'
import { toDisplay } from '@/utils/pairStatus'

const statusBarHeight = ref(0)
try {
  statusBarHeight.value = uni.getWindowInfo().statusBarHeight ?? 0
} catch {
  statusBarHeight.value = 0
}

/* ---------- 当前登录红娘（ref 化：改资料后即时刷新头部） ---------- */
const me = ref(getStoredUser())
const initial = computed(() => (me.value?.name || '鹊')[0])
const avatarBg = computed(() => me.value?.avatarBg || 'linear-gradient(135deg, #E3C766 0%, #D4AF37 48%, #B8941F 100%)')
const roleLabel = computed(
  () => (me.value?.role === 'ADMIN' ? '管理员' : me.value?.role === 'MANAGER' ? '主管' : '金牌红娘')
)
const maskedPhone = computed(() => {
  const p = me.value?.phone || ''
  return p.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2') || '未绑定手机'
})

/* ---------- 业绩概览 ---------- */
const stats = ref([
  { label: '今日约见', value: '—', icon: 'stat-calendar' },
  { label: '牵线中', value: '—', icon: 'stat-match' },
  { label: '已脱单', value: '—', icon: 'heart-white' },
])

const fetchStats = async () => {
  try {
    const [, pairRes] = await Promise.all([api.calendarToday(), api.pairs({ size: 100 })])
    const today = pairRes.list.filter((p) => p.status === '已约见' || p.status === '待反馈')
    const done = pairRes.list.filter(
      (p) => toDisplay(p.status) === '已脱单' || toDisplay(p.stage) === '已脱单'
    )
    stats.value = [
      { label: '今日约见', value: String(today.length), icon: 'stat-calendar' },
      { label: '牵线中', value: String(pairRes.list.length - done.length), icon: 'stat-match' },
      { label: '已脱单', value: String(done.length), icon: 'heart-white' },
    ]
  } catch {
    /* 后端未连接时保持占位值 */
  }
}
onShow(fetchStats)

/* ---------- 入口列表 ---------- */
const businessMenus = [
  { icon: 'users-primary', title: '会员库', sub: '建档 / 检索 / 跟进', to: '/pages/members/index', tab: true },
  { icon: 'handshake-white', title: '智能牵线', sub: 'AI 匹配推荐', to: '/pages/match/index', tab: true },
  { icon: 'stat-calendar', title: '约见日程', sub: '安排与提醒', to: '/pages/schedule/index', tab: false },
  { icon: 'act-chart', title: '跟进看板', sub: '牵线进度一览', to: '/pages/followup/kanban', tab: false },
]
const accountMenus = [
  { icon: 'message-primary', title: '消息中心', sub: '会员消息 / 系统通知', to: '/pages/message/index', tab: true },
  { icon: 'pen-ink', title: '编辑资料', sub: '姓名 / 手机 / 邮箱', action: 'edit' },
  { icon: 'shield-white', title: '安全设置', sub: '修改登录密码', action: 'password' },
]

/* ---------- 编辑资料（PUT /api/auth/profile） ---------- */
const editOpen = ref(false)
const editSaving = ref(false)
const editForm = ref({ name: '', phone: '', email: '' })

const openEdit = () => {
  editForm.value = {
    name: me.value?.name || '',
    phone: me.value?.phone || '',
    email: me.value?.email || '',
  }
  editOpen.value = true
}
const closeEdit = () => {
  editOpen.value = false
}

/** 邮箱格式宽松校验：允许为空，填了就必须是合法形态 */
const isValidEmail = (v: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v)

const submitEdit = async () => {
  if (editSaving.value) return
  const f = editForm.value
  if (!f.name.trim()) {
    uni.showToast({ title: '请填写姓名', icon: 'none' })
    return
  }
  const phoneDigits = f.phone.replace(/\D/g, '')
  if (phoneDigits && phoneDigits.length !== 11) {
    uni.showToast({ title: '手机号需为 11 位', icon: 'none' })
    return
  }
  if (f.email.trim() && !isValidEmail(f.email.trim())) {
    uni.showToast({ title: '邮箱格式不正确', icon: 'none' })
    return
  }
  editSaving.value = true
  try {
    const updated = await api.updateProfile({
      name: f.name.trim(),
      phone: f.phone.trim(),
      email: f.email.trim(),
    })
    // 回写本地存储，避免下次进入仍是旧资料
    setStoredUser(updated)
    me.value = updated
    uni.showToast({ title: '资料已更新', icon: 'success' })
    editOpen.value = false
  } catch (e: any) {
    uni.showToast({ title: (e && e.message) || '保存失败', icon: 'none' })
  } finally {
    editSaving.value = false
  }
}

/* ---------- 修改密码（POST /api/auth/change-password） ---------- */
const pwdOpen = ref(false)
const pwdSaving = ref(false)
const pwdForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })

const openPwd = () => {
  pwdForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  pwdOpen.value = true
}
const closePwd = () => {
  pwdOpen.value = false
}

const submitPwd = async () => {
  if (pwdSaving.value) return
  const f = pwdForm.value
  if (!f.oldPassword) {
    uni.showToast({ title: '请输入原密码', icon: 'none' })
    return
  }
  if (f.newPassword.length < 6) {
    uni.showToast({ title: '新密码至少 6 位', icon: 'none' })
    return
  }
  if (f.newPassword !== f.confirmPassword) {
    uni.showToast({ title: '两次输入的新密码不一致', icon: 'none' })
    return
  }
  if (f.oldPassword === f.newPassword) {
    uni.showToast({ title: '新密码不能与原密码相同', icon: 'none' })
    return
  }
  pwdSaving.value = true
  try {
    await api.changePassword({ oldPassword: f.oldPassword, newPassword: f.newPassword })
    uni.showToast({ title: '密码已修改，请重新登录', icon: 'success' })
    pwdOpen.value = false
    // 改密后票据失效，退回登录页重新认证
    setTimeout(() => {
      clearAuth()
      uni.reLaunch({ url: '/pages/login/index' })
    }, 800)
  } catch (e: any) {
    uni.showToast({ title: (e && e.message) || '修改失败', icon: 'none' })
  } finally {
    pwdSaving.value = false
  }
}

const onMenu = (m: any) => {
  if (m.action === 'edit') {
    openEdit()
    return
  }
  if (m.action === 'password') {
    openPwd()
    return
  }
  if (m.tab) {
    uni.switchTab({ url: m.to })
  } else {
    uni.navigateTo({ url: m.to })
  }
}

const onLogout = () => {
  uni.showModal({
    title: '退出登录',
    content: '确定要退出当前账号吗？',
    confirmColor: '#C2405A',
    success: (res) => {
      if (!res.confirm) return
      api.logout().catch(() => {})
      clearAuth()
      uni.reLaunch({ url: '/pages/login/index' })
    },
  })
}
</script>

<template>
  <view class="page">
    <aurora-bg />
    <view :style="{ height: statusBarHeight + 'px' }" />

    <!-- 顶栏（大字号） -->
    <view class="glass-bar topbar">
      <text class="topbar-title">我的</text>
      <view class="circle-btn flex-center" hover-class="hover-opacity" @tap="onLogout">
        <app-icon name="share-ink" size="34rpx" />
      </view>
    </view>

    <!-- 红娘头部卡 -->
    <view class="profile-card">
      <view class="pc-avatar flex-center" :style="{ background: avatarBg }">
        <text class="pc-initial">{{ initial }}</text>
      </view>
      <text class="pc-name">{{ me?.name || '红娘' }}</text>
      <view class="flex items-center" style="gap: 12rpx; margin-top: 14rpx">
        <view class="pc-role flex-center">{{ roleLabel }}</view>
        <text class="pc-phone">{{ maskedPhone }}</text>
      </view>
    </view>

    <!-- 业绩概览（Bento） -->
    <view class="bento stat-bento">
      <glass-card
        v-for="(s, i) in stats"
        :key="s.label"
        padding="28rpx"
        radius="var(--q-bento-radius)"
        shadow="soft"
        class="sc"
        :class="{ 'sc-hero': i === 0 }"
      >
        <view class="sc-icon flex-center" :class="'sc-tone-' + (i + 1)">
          <app-icon :name="s.icon" size="32rpx" />
        </view>
        <text class="sc-label">{{ s.label }}</text>
        <text class="sc-value" :class="{ 'sc-value-hero': i === 0 }">{{ s.value }}</text>
      </glass-card>
    </view>

    <!-- 业务入口 -->
    <view class="menu-card glass-card-strong">
      <text class="menu-title">业务</text>
      <view
        v-for="(m, i) in businessMenus"
        :key="m.title"
        class="menu-row flex items-center"
        hover-class="menu-hover"
        @tap="onMenu(m)"
      >
        <view class="menu-ico flex-center" :class="{ 'menu-ico-gold': i === 0 }">
          <app-icon :name="m.icon" size="34rpx" />
        </view>
        <view class="menu-body">
          <text class="menu-name">{{ m.title }}</text>
          <text class="menu-sub">{{ m.sub }}</text>
        </view>
        <app-icon name="chevron-right" size="26rpx" />
      </view>
    </view>

    <!-- 账号设置 -->
    <view class="menu-card glass-card-strong">
      <text class="menu-title">账号</text>
      <view
        v-for="m in accountMenus"
        :key="m.title"
        class="menu-row flex items-center"
        hover-class="menu-hover"
        @tap="onMenu(m)"
      >
        <view class="menu-ico flex-center">
          <app-icon :name="m.icon" size="34rpx" />
        </view>
        <view class="menu-body">
          <text class="menu-name">{{ m.title }}</text>
          <text class="menu-sub">{{ m.sub }}</text>
        </view>
        <app-icon name="chevron-right" size="26rpx" />
      </view>
    </view>

    <!-- 退出登录 -->
    <view class="logout-btn flex-center" hover-class="hover-opacity" @tap="onLogout">
      <app-icon name="x-ink" size="30rpx" />
      <text>退出登录</text>
    </view>

    <text class="version">鹊桥 · 红娘工作台 v1.0.0</text>

    <view style="height: 48rpx" />

    <!-- ===== 编辑资料弹层 ===== -->
    <view v-if="editOpen" class="sheet-mask" @tap="closeEdit" />
    <view class="sheet" :class="{ 'sheet-open': editOpen }">
      <view class="sheet-head flex-between items-center">
        <text class="sheet-title">编辑资料</text>
        <view class="sheet-close flex-center" hover-class="hover-opacity" @tap="closeEdit">
          <app-icon name="close-ink" size="32rpx" />
        </view>
      </view>
      <view class="sheet-body">
        <view class="f-row">
          <text class="f-label">姓名</text>
          <input v-model="editForm.name" class="f-input" placeholder="请输入姓名" placeholder-class="f-ph" />
        </view>
        <view class="f-row">
          <text class="f-label">手机号</text>
          <input
            v-model="editForm.phone"
            type="text"
            inputmode="numeric"
            class="f-input"
            placeholder="11 位手机号"
            placeholder-class="f-ph"
          />
        </view>
        <view class="f-row" style="border-bottom: none">
          <text class="f-label">邮箱</text>
          <input v-model="editForm.email" class="f-input" placeholder="用于接收系统通知" placeholder-class="f-ph" />
        </view>
      </view>
      <view class="sheet-submit flex-center" :class="{ 'is-busy': editSaving }" hover-class="hover-scale" @tap="submitEdit">
        {{ editSaving ? '保存中…' : '保存资料' }}
      </view>
    </view>

    <!-- ===== 修改密码弹层 ===== -->
    <view v-if="pwdOpen" class="sheet-mask" @tap="closePwd" />
    <view class="sheet" :class="{ 'sheet-open': pwdOpen }">
      <view class="sheet-head flex-between items-center">
        <text class="sheet-title">修改登录密码</text>
        <view class="sheet-close flex-center" hover-class="hover-opacity" @tap="closePwd">
          <app-icon name="close-ink" size="32rpx" />
        </view>
      </view>
      <view class="sheet-body">
        <view class="f-row">
          <text class="f-label">原密码</text>
          <input
            v-model="pwdForm.oldPassword"
            type="text"
            password
            class="f-input"
            placeholder="请输入当前登录密码"
            placeholder-class="f-ph"
          />
        </view>
        <view class="f-row">
          <text class="f-label">新密码</text>
          <input
            v-model="pwdForm.newPassword"
            type="text"
            password
            class="f-input"
            placeholder="至少 6 位"
            placeholder-class="f-ph"
          />
        </view>
        <view class="f-row" style="border-bottom: none">
          <text class="f-label">确认密码</text>
          <input
            v-model="pwdForm.confirmPassword"
            type="text"
            password
            class="f-input"
            placeholder="请再次输入新密码"
            placeholder-class="f-ph"
          />
        </view>
        <text class="pwd-tip">修改成功后需使用新密码重新登录。</text>
      </view>
      <view class="sheet-submit flex-center" :class="{ 'is-busy': pwdSaving }" hover-class="hover-scale" @tap="submitPwd">
        {{ pwdSaving ? '提交中…' : '确认修改' }}
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  position: relative;
  min-height: 100vh;
  background: transparent;
}

/* ---------- 底部弹层（编辑资料 / 修改密码） ---------- */
.sheet-mask {
  position: fixed;
  inset: 0;
  background: rgba(46, 34, 42, 0.35);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  z-index: 98;
}
.sheet {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  max-height: 86vh;
  border-radius: 48rpx 48rpx 0 0;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: var(--q-glass-blur);
  -webkit-backdrop-filter: var(--q-glass-blur);
  border-top: 1px solid var(--q-glass-border);
  box-shadow: 0 -20rpx 60rpx rgba(120, 70, 80, 0.18);
  transform: translateY(110%);
  transition: transform 0.3s cubic-bezier(0.22, 1, 0.36, 1);
  z-index: 99;
  padding: 36rpx 40rpx calc(36rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
  overflow-y: auto;
}
.sheet-open {
  transform: translateY(0);
}
.sheet-head {
  margin-bottom: 12rpx;
}
.sheet-title {
  font-size: 34rpx;
  font-weight: 700;
  color: var(--q-ink-1);
}
.sheet-close {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: rgba(150, 128, 137, 0.12);
}
.sheet-body {
  padding-top: 8rpx;
}
.f-row {
  display: flex;
  align-items: center;
  padding: 26rpx 0;
  border-bottom: 1px solid rgba(139, 37, 74, 0.07);
}
.f-label {
  width: 150rpx;
  flex-shrink: 0;
  font-size: 28rpx;
  color: var(--q-ink-2);
  font-weight: 500;
}
.f-input {
  flex: 1;
  font-size: 30rpx;
  color: var(--q-ink-1);
  background: var(--q-glass-bg-light);
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 20rpx;
  padding: 16rpx 22rpx;
}
.f-ph {
  color: var(--q-ink-4);
  font-size: 26rpx;
}
.sheet-submit {
  margin-top: 32rpx;
  height: 96rpx;
  border-radius: 24rpx;
  background: var(--q-gold-gradient);
  color: #fff;
  font-size: 30rpx;
  font-weight: 700;
  box-shadow: 0 12rpx 28rpx rgba(168, 134, 30, 0.35);
}
.sheet-submit.is-busy {
  opacity: 0.7;
}
.pwd-tip {
  display: block;
  margin-top: 20rpx;
  font-size: 24rpx;
  color: var(--q-ink-4);
  line-height: 1.6;
}

/* ---------- 顶栏 ---------- */
.topbar {
  position: relative;
  z-index: 2;
  width: 100%;
  padding: 20rpx 40rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-sizing: border-box;
}
.topbar-title {
  font-size: var(--q-type-title-lg);
  font-weight: 700;
  color: var(--q-ink-1);
  letter-spacing: -0.02em;
}
.circle-btn {
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: var(--q-glass-blur-sm);
  -webkit-backdrop-filter: var(--q-glass-blur-sm);
  border: 1px solid rgba(255, 255, 255, 0.7);
  box-shadow: var(--q-glass-shadow), var(--q-glass-hi-line);
}

/* ---------- 红娘头部卡 ---------- */
.profile-card {
  position: relative;
  z-index: 2;
  margin: 32rpx 40rpx 0;
  padding: 48rpx 40rpx;
  border-radius: 44rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  background: linear-gradient(160deg, rgba(255, 255, 255, 0.82) 0%, rgba(255, 255, 255, 0.55) 100%);
  backdrop-filter: var(--q-glass-blur);
  -webkit-backdrop-filter: var(--q-glass-blur);
  border: 1px solid var(--q-glass-border);
  box-shadow: var(--q-glass-shadow);
}
.pc-avatar {
  width: 148rpx;
  height: 148rpx;
  border-radius: 50%;
  border: 6rpx solid rgba(255, 255, 255, 0.9);
  box-shadow: 0 16rpx 32rpx -12rpx rgba(168, 134, 30, 0.45), var(--q-glass-hi-line);
  overflow: hidden;
}
.pc-initial {
  font-size: 64rpx;
  font-weight: 700;
  color: #fff;
  text-shadow: 0 2rpx 10rpx rgba(120, 90, 10, 0.35);
}
.pc-name {
  margin-top: 24rpx;
  font-size: 40rpx;
  font-weight: 700;
  color: var(--q-ink-1);
  letter-spacing: -0.01em;
}
.pc-role {
  padding: 6rpx 20rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 600;
  color: #fff;
  background: var(--q-gold-gradient);
  box-shadow: 0 8rpx 20rpx -8rpx rgba(168, 134, 30, 0.55);
}
.pc-phone {
  font-size: 24rpx;
  color: var(--q-ink-3);
  letter-spacing: 0.03em;
}

/* ---------- 业绩概览 ---------- */
.stat-bento {
  position: relative;
  z-index: 2;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20rpx;
  margin: 32rpx 40rpx 0;
}
.sc {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
  min-height: 168rpx;
}
.sc-hero {
  background: var(--q-gold-gradient);
  border: none;
  box-shadow: 0 20rpx 40rpx -16rpx rgba(168, 134, 30, 0.5), inset 0 2rpx 0 rgba(255, 255, 255, 0.5);
}
.sc-icon {
  width: 60rpx;
  height: 60rpx;
  border-radius: 20rpx;
  margin-bottom: 4rpx;
}
.sc-tone-1 {
  background: rgba(255, 255, 255, 0.28);
  border: 1px solid rgba(255, 255, 255, 0.5);
}
.sc-tone-2 {
  background: var(--q-primary-soft);
}
.sc-tone-3 {
  background: var(--q-success-soft);
}
.sc-label {
  font-size: 22rpx;
  color: var(--q-ink-3);
}
.sc-hero .sc-label {
  color: rgba(255, 255, 255, 0.85);
}
.sc-value {
  font-size: 40rpx;
  font-weight: 700;
  color: var(--q-ink-1);
  line-height: 1;
}
.sc-value-hero {
  color: #fff;
  text-shadow: 0 2rpx 8rpx rgba(120, 90, 10, 0.35);
}

/* ---------- 菜单卡 ---------- */
.menu-card {
  position: relative;
  z-index: 2;
  margin: 32rpx 40rpx 0;
  padding: 16rpx 32rpx;
  border-radius: 40rpx;
}
.menu-title {
  display: block;
  padding: 20rpx 0 8rpx;
  font-size: 24rpx;
  font-weight: 600;
  color: var(--q-ink-3);
  letter-spacing: 0.04em;
}
.menu-row {
  padding: 24rpx 4rpx;
  gap: 24rpx;
}
.menu-hover {
  opacity: 0.75;
}
.menu-ico {
  width: 76rpx;
  height: 76rpx;
  border-radius: 24rpx;
  background: var(--q-primary-soft);
  flex-shrink: 0;
}
.menu-ico-gold {
  background: var(--q-gold-soft);
}
.menu-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}
.menu-name {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--q-ink-1);
}
.menu-sub {
  font-size: 22rpx;
  color: var(--q-ink-3);
}

/* ---------- 退出 ---------- */
.logout-btn {
  position: relative;
  z-index: 2;
  margin: 40rpx 40rpx 0;
  padding: 28rpx 0;
  border-radius: 999rpx;
  gap: 12rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: var(--q-error);
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: var(--q-glass-blur-sm);
  -webkit-backdrop-filter: var(--q-glass-blur-sm);
  border: 1px solid rgba(194, 64, 90, 0.25);
  box-shadow: var(--q-glass-shadow-soft);
}
.version {
  display: block;
  margin-top: 32rpx;
  text-align: center;
  font-size: 22rpx;
  color: var(--q-ink-3);
}
</style>
