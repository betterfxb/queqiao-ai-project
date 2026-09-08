<script setup lang="ts">
/**
 * 会员详情 — 数据 GET /api/members/:id → { member, pairs, followups }
 * 含 AI 跟进摘要 + 跟进时间线（可新增）+ 牵线记录
 */
import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { api, maskPhone, type Member, type Pair, type FollowUp, errMsg } from '@/api'

const statusBarHeight = ref(0)
try {
  statusBarHeight.value = uni.getWindowInfo().statusBarHeight ?? 0
} catch {
  statusBarHeight.value = 0
}

const id = ref('')
const member = ref<Member | null>(null)
const pairs = ref<Pair[]>([])
const followups = ref<FollowUp[]>([])
const summary = ref<any>(null)
const loading = ref(false)

const fetchAll = async () => {
  loading.value = true
  try {
    const r = await api.memberDetail(id.value)
    member.value = r.member
    pairs.value = r.pairs || []
    followups.value = r.followups || []
    try {
      summary.value = await api.followupSummary(id.value)
    } catch {
      summary.value = null
    }
  } catch (e: any) {
    uni.showToast({ title: errMsg(e), icon: 'none' })
  } finally {
    loading.value = false
  }
}
onLoad((q) => {
  id.value = String(q?.id || '')
  if (id.value) fetchAll()
})

const tagList = (tags?: string) => (tags ? tags.split(/[,，、]/).filter(Boolean) : [])

const outcomeCls = (o?: string) =>
  o === '成交意向' ? 'oc-good' : o === '无意向' ? 'oc-bad' : 'oc-mid'

/**
 * 基本信息网格（对齐采集页收集的全部字段）
 * 说明：原「籍贯 hometown」采集页并不收集，恒为空显示「—」，
 * 现替换为采集页真实收集的省/市/区三级地址；「现居」与地区重复，改为展示职业。
 * 另：后端 income 枚举为月收入区间（5k 以下 / 5k-1w / …），故标签为「月收入」而非「年收入」。
 */
const infoGrid = () => {
  const m = member.value
  if (!m) return []
  return [
    { label: '身高', value: m.height ? `${m.height}cm` : '—' },
    { label: '手机', value: m.phone ? maskPhone(m.phone) : '—' },
    { label: '学历', value: m.education || '—' },
    { label: '月收入', value: m.income || '—' },
    { label: '婚姻', value: m.marriage || '—' },
    { label: '子女', value: m.hasChild || '—' },
    { label: '房产', value: m.house || '—' },
    { label: '座驾', value: m.car || '—' },
    { label: '地区', value: regionText() },
    { label: '职业', value: m.job || '—' },
    { label: '等级', value: m.level || '—' },
  ]
}

/**
 * 省/市/区三级地址（采集页收集）。
 * 直辖市会出现「北京 / 北京 / 朝阳区」这类省市同名，需去重后再拼接。
 */
const regionText = () => {
  const m = member.value
  if (!m) return '—'
  const parts = [m.province, m.city, m.district].filter(Boolean) as string[]
  if (!parts.length) return '—'
  const uniq = parts.filter((v, i) => parts.indexOf(v) === i)
  return uniq.join(' ')
}

/** 点击头像：有照片则全屏预览，无照片给出明确提示（不做无反应的死按钮） */
const onPreviewPhoto = () => {
  const p = member.value?.photo
  if (!p) {
    uni.showToast({ title: '该会员未上传照片', icon: 'none' })
    return
  }
  uni.previewImage({ urls: [p], current: 0 })
}

/* ---------- 跟进 ---------- */
const addMode = ref(false)
const fuType = ref('电话')
const fuContent = ref('')
const fuOutcome = ref('待定')
const fuNext = ref('')
const fuSaving = ref(false)

const FU_TYPES = ['电话', '微信', '约见', '回访', '系统']
const FU_OUTCOMES = ['待定', '成交意向', '无意向']

const today = () => {
  const d = new Date()
  const p = (n: number) => (n < 10 ? '0' + n : '' + n)
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
const in3days = () => {
  const d = new Date()
  d.setDate(d.getDate() + 3)
  const p = (n: number) => (n < 10 ? '0' + n : '' + n)
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

const submitFollowup = async () => {
  if (!fuContent.value.trim()) {
    uni.showToast({ title: '请填写沟通要点', icon: 'none' })
    return
  }
  fuSaving.value = true
  try {
    await api.createFollowup(id.value, {
      type: fuType.value,
      content: fuContent.value.trim(),
      outcome: fuOutcome.value,
      nextFollowUp: fuNext.value || undefined,
    })
    fuContent.value = ''
    fuNext.value = ''
    addMode.value = false
    uni.showToast({ title: '跟进已记录', icon: 'success' })
    fetchAll()
  } catch (e: any) {
    uni.showToast({ title: errMsg(e), icon: 'none' })
  } finally {
    fuSaving.value = false
  }
}

/* ---------- 交互 ---------- */
const goBack = () => uni.navigateBack()
const onEdit = () => uni.navigateTo({ url: `/pages/member/input?id=${id.value}` })
const onSchedule = () => uni.navigateTo({ url: '/pages/schedule/index' })
const onMatch = () => uni.navigateTo({ url: `/pages/match/index?memberId=${id.value}` })
const onMessage = async () => {
  try {
    const c = await api.createConversation(id.value)
    uni.navigateTo({ url: `/pages/message/chat?id=${c.id}` })
  } catch (e: any) {
    uni.showToast({ title: errMsg(e), icon: 'none' })
  }
}
// 牵线记录进入独立牵线详情页（pages/pair/detail），展示双方资料/反馈/约见与状态流转
const onPairTap = (p: Pair) => uni.navigateTo({ url: `/pages/pair/detail?id=${p.id}` })
</script>

<template>
  <view class="page">
    <aurora-bg />
    <loading-red-line v-if="loading" :top="statusBarHeight + 'px'" />
    <view :style="{ height: statusBarHeight + 'px' }" />

    <!-- 顶栏 -->
    <view class="glass-bar topbar">
      <view class="circle-btn flex-center" hover-class="hover-opacity" @tap="goBack">
        <app-icon name="back-ink" size="44rpx" />
      </view>
      <text class="topbar-title">会员资料</text>
      <view class="circle-btn flex-center" hover-class="hover-opacity" style="visibility: hidden">
        <app-icon name="more-ink" size="36rpx" />
      </view>
    </view>

    <template v-if="member">
      <!-- Profile Hero -->
      <view class="hero">
        <view class="avatar-wrap">
          <view
            class="hero-avatar flex-center"
            :style="member.photo ? {} : { background: member.avatarBg }"
            hover-class="hover-opacity"
            @tap="onPreviewPhoto"
          >
            <image v-if="member.photo" :src="member.photo" class="hero-photo" mode="aspectFill" />
            <text v-else>{{ member.name[0] }}</text>
          </view>
          <view v-if="member.verified" class="badge-gold flex-center">
            <app-icon name="check-white" size="24rpx" />
          </view>
        </view>
        <view class="flex items-baseline justify-center" style="gap: 12rpx; margin-top: 24rpx">
          <text class="hero-name">{{ member.name }}</text>
          <text class="hero-gender">{{ member.gender === '女' ? '♀' : '♂' }}</text>
          <text class="hero-age">· {{ member.age }}岁</text>
        </view>
        <text class="hero-sub">{{ member.city || member.level }} · {{ member.job || '—' }}</text>

        <view class="flex justify-center" style="gap: 16rpx; margin-top: 20rpx">
          <view class="cert-pill cert-gold flex items-center">
            <app-icon name="star-gold" size="24rpx" />
            <text>{{ member.level || '普通' }}</text>
          </view>
          <view class="cert-pill" :class="member.status === '已认证' ? 'cert-green' : 'cert-primary'">
            <text>{{ member.status || '待审核' }}</text>
          </view>
          <view class="cert-pill cert-primary" v-if="member.agent">
            <text>红娘：{{ member.agent }}</text>
          </view>
        </view>

        <view class="score-pill flex items-center justify-center">
          <app-icon name="handshake-white" size="28rpx" />
          <text class="score-label">牵线</text>
          <text class="score-num">{{ member.matchCount ?? 0 }}</text>
          <text class="score-label">次 · 成功率</text>
          <text class="score-num">{{ member.successRate ?? 0 }}%</text>
        </view>
      </view>

      <!-- 快捷操作行 -->
      <view class="quick-row flex">
        <view class="q-btn q-msg flex-center" hover-class="hover-opacity" @tap="onMessage">
          <app-icon name="message-primary" size="32rpx" />
          <text>发消息</text>
        </view>
        <view class="q-btn q-match flex-center" hover-class="hover-opacity" @tap="onMatch">
          <app-icon name="handshake-white" size="32rpx" />
          <text>发起牵线</text>
        </view>
        <view class="q-btn q-edit flex-center" hover-class="hover-opacity" @tap="onEdit">
          <app-icon name="edit-primary" size="32rpx" />
          <text>编辑</text>
        </view>
      </view>

      <view class="cards">
        <!-- 基本信息 -->
        <glass-card strong padding="36rpx" radius="40rpx">
          <view class="section-title card-title"><text class="card-title-text">基本信息</text></view>
          <view class="info-grid">
            <view v-for="g in infoGrid()" :key="g.label">
              <text class="ig-label">{{ g.label }}</text>
              <text class="ig-value">{{ g.value }}</text>
            </view>
          </view>
        </glass-card>

        <!-- 择偶要求 -->
        <glass-card strong padding="36rpx" radius="40rpx">
          <view class="section-title card-title"><text class="card-title-text">择偶要求</text></view>
          <text class="req-text">{{ member.requirement || '未填写择偶要求' }}</text>
        </glass-card>

        <!-- 自我介绍 -->
        <glass-card strong padding="36rpx" radius="40rpx" v-if="member.bio || member.tags">
          <view class="section-title card-title"><text class="card-title-text">自我介绍</text></view>
          <text class="intro-text">{{ member.bio || '暂未填写自我介绍' }}</text>
          <view class="flex flex-wrap" style="gap: 14rpx; margin-top: 20rpx">
            <view v-for="h in tagList(member.tags)" :key="h" class="hobby-tag flex items-center">
              <app-icon name="act-heart" size="22rpx" />
              <text>{{ h }}</text>
            </view>
          </view>
        </glass-card>

        <!-- AI 跟进摘要 -->
        <glass-card strong padding="36rpx" radius="40rpx" v-if="summary">
          <view class="section-title card-title"><text class="card-title-text">AI 跟进摘要</text></view>
          <text class="intro-text">{{ summary.summary }}</text>
          <view class="flex" style="gap: 16rpx; margin-top: 20rpx; flex-wrap: wrap">
            <view class="mini-stat">
              <text class="mini-label">意向</text>
              <text class="mini-value">{{ summary.intention }}</text>
            </view>
            <view class="mini-stat">
              <text class="mini-label">跟进次数</text>
              <text class="mini-value">{{ summary.count }}</text>
            </view>
          </view>
          <view class="suggest-box">
            <text class="suggest-text">💡 {{ summary.suggestion }}</text>
          </view>
          <text v-for="(r, i) in summary.reminders" :key="i" class="remind-text">· {{ r }}</text>
        </glass-card>

        <!-- 跟进时间线 -->
        <glass-card strong padding="36rpx" radius="40rpx">
          <view class="flex-between">
            <view class="section-title"><text class="card-title-text">跟进时间线</text></view>
            <view class="add-btn flex-center" hover-class="hover-opacity" @tap="addMode = !addMode">
              <app-icon :name="addMode ? 'minus-primary' : 'plus-primary'" size="28rpx" />
              <text>{{ addMode ? '收起' : '记录跟进' }}</text>
            </view>
          </view>

          <!-- 新增跟进表单 -->
          <view v-if="addMode" class="fu-form">
            <view class="fu-row">
              <text class="fu-label">方式</text>
              <view class="flex" style="gap: 12rpx; flex-wrap: wrap">
                <view
                  v-for="t in FU_TYPES"
                  :key="t"
                  class="sel-pill flex-center"
                  :class="{ 'sel-active': fuType === t }"
                  @tap="fuType = t"
                >{{ t }}</view>
              </view>
            </view>
            <view class="fu-row">
              <text class="fu-label">要点</text>
              <textarea
                v-model="fuContent"
                class="fu-textarea"
                placeholder="记录本次沟通内容…"
                placeholder-class="ph"
                :maxlength="1000"
              />
            </view>
            <view class="fu-row">
              <text class="fu-label">意向</text>
              <view class="flex" style="gap: 12rpx; flex-wrap: wrap">
                <view
                  v-for="o in FU_OUTCOMES"
                  :key="o"
                  class="sel-pill flex-center"
                  :class="{ 'sel-active': fuOutcome === o }"
                  @tap="fuOutcome = o"
                >{{ o }}</view>
              </view>
            </view>
            <view class="fu-row">
              <text class="fu-label">下次</text>
              <view class="flex" style="gap: 12rpx">
                <input v-model="fuNext" class="fu-input" placeholder="yyyy-MM-dd" :placeholder="in3days()" style="flex: 1" />
                <view class="fill-btn flex-center" @tap="fuNext = in3days()">+3天</view>
              </view>
            </view>
            <view class="submit-btn flex-center" :class="{ disabled: fuSaving }" @tap="submitFollowup">
              {{ fuSaving ? '保存中…' : '保存跟进' }}
            </view>
          </view>

          <!-- 时间线列表 -->
          <view v-if="followups.length" class="timeline">
            <view v-for="f in followups" :key="f.id" class="tl-item">
              <view class="tl-dot" />
              <view class="tl-body">
                <view class="flex-between">
                  <view class="flex" style="gap: 12rpx; align-items: center">
                    <text class="tl-type">{{ f.type }}</text>
                    <text class="tl-outcome" :class="outcomeCls(f.outcome)">{{ f.outcome }}</text>
                  </view>
                  <text class="tl-time">{{ f.timeAgo || f.createdAt }}</text>
                </view>
                <text class="tl-content">{{ f.content }}</text>
                <text v-if="f.nextFollowUp" class="tl-next">下次跟进：{{ f.nextFollowUp }}</text>
              </view>
            </view>
          </view>
          <view v-else class="empty-fu">
            <text>暂无跟进记录，点击右上角「记录跟进」开始留痕</text>
          </view>
        </glass-card>

        <!-- 牵线记录 -->
        <glass-card strong padding="36rpx" radius="40rpx" v-if="pairs.length">
          <view class="section-title card-title"><text class="card-title-text">牵线记录</text></view>
          <view class="pair-list">
            <view v-for="p in pairs" :key="p.id" class="pair-row" hover-class="hover-opacity" @tap="onPairTap(p)">
              <view class="flex items-center" style="gap: 16rpx; flex: 1; min-width: 0">
                <text class="pair-name">{{ p.maleName }}</text>
                <text class="pair-x">×</text>
                <text class="pair-name">{{ p.femaleName }}</text>
              </view>
              <view class="flex items-center" style="gap: 12rpx">
                <text class="pair-score">{{ p.score }}分</text>
                <view class="pair-status" :class="'ps-' + p.status">{{ p.status || p.stage }}</view>
              </view>
            </view>
          </view>
        </glass-card>
      </view>
    </template>

    <!-- 空态 -->
    <template v-else-if="!loading">
      <view class="empty-state flex-center">
        <view class="empty-icon flex-center">
          <app-icon name="search-ink" size="56rpx" />
        </view>
        <text class="empty-title">未找到会员资料</text>
        <text class="empty-sub">该会员可能已不存在，或网络异常</text>
        <view class="empty-btn flex-center" hover-class="hover-opacity" @tap="goBack">返回上一页</view>
      </view>
    </template>

    <view v-if="member" style="height: 60rpx" />
  </view>
</template>

<style lang="scss" scoped>
.page {
  position: relative;
  min-height: 100vh;
  background: transparent;
}
.topbar {
  position: relative;
  z-index: 2;
  width: 100%;
  padding: 20rpx 32rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-sizing: border-box;
}
.topbar-title {
  font-size: 34rpx;
  font-weight: 600;
  color: var(--q-ink-1);
}
.circle-btn {
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  background: var(--q-glass-bg);
  border: 1px solid var(--q-glass-border);
  backdrop-filter: var(--q-glass-blur-sm);
  -webkit-backdrop-filter: var(--q-glass-blur-sm);
}

/* ---------- Hero ---------- */
.hero {
  position: relative;
  z-index: 2;
  margin: 32rpx 40rpx 0;
  text-align: center;
}
.avatar-wrap {
  position: relative;
  display: inline-block;
}
.hero-avatar {
  width: 200rpx;
  height: 200rpx;
  border-radius: 50%;
  overflow: hidden;
  font-size: 80rpx;
  font-weight: 300;
  color: rgba(255, 255, 255, 0.85);
  box-shadow: 0 16rpx 48rpx rgba(139, 37, 74, 0.2);
}
/* 会员本人照片（采集页上传的 base64 dataURL），撑满圆形头像 */
.hero-photo {
  width: 200rpx;
  height: 200rpx;
  border-radius: 50%;
}
.badge-gold {
  position: absolute;
  bottom: 4rpx;
  right: 4rpx;
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  background: var(--q-gold-gradient);
  border: 6rpx solid var(--q-bg-base);
}
.hero-name {
  font-size: 48rpx;
  font-weight: 700;
  color: var(--q-ink-1);
}
.hero-gender {
  font-size: 32rpx;
  color: var(--q-primary);
}
.hero-age {
  font-size: 36rpx;
  color: var(--q-ink-2);
}
.hero-sub {
  display: block;
  font-size: 28rpx;
  color: var(--q-ink-3);
  margin-top: 8rpx;
}
.cert-pill {
  gap: 8rpx;
  padding: 10rpx 24rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 500;
}
.cert-green {
  background: var(--q-success-soft);
  color: var(--q-success);
}
.cert-gold {
  background: var(--q-gold-soft);
  color: var(--q-gold-dark);
}
.cert-primary {
  background: var(--q-primary-soft);
  color: var(--q-primary);
}
.score-pill {
  margin: 24rpx auto 0;
  width: max-content;
  gap: 10rpx;
  padding: 20rpx 40rpx;
  border-radius: 32rpx;
  background: var(--q-glass-bg-strong);
  border: 1px solid var(--q-glass-border);
  backdrop-filter: var(--q-glass-blur);
  -webkit-backdrop-filter: var(--q-glass-blur);
}
.score-label {
  font-size: 26rpx;
  color: var(--q-ink-2);
}
.score-num {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--q-primary);
}

/* ---------- 快捷操作 ---------- */
.quick-row {
  position: relative;
  z-index: 2;
  margin: 32rpx 40rpx 0;
  gap: 20rpx;
  align-items: center;
}
.q-btn {
  flex: 1;
  height: 88rpx;
  border-radius: 28rpx;
  gap: 10rpx;
  font-size: 28rpx;
  font-weight: 600;
}
.q-msg {
  background: var(--q-glass-bg-strong);
  border: 1px solid var(--q-glass-border);
  color: var(--q-primary);
}
.q-match {
  background: var(--q-primary);
  color: #fff;
  box-shadow: 0 8rpx 24rpx rgba(139, 37, 74, 0.25);
}
.q-edit {
  background: var(--q-gold-soft);
  color: var(--q-gold-dark);
}

/* ---------- 信息卡片 ---------- */
.cards {
  position: relative;
  z-index: 2;
  margin: 40rpx;
  display: flex;
  flex-direction: column;
  gap: 32rpx;
}
.card-title {
  margin-bottom: 24rpx;
}
.card-title-text {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--q-ink-1);
}
.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24rpx;
}
.ig-label {
  display: block;
  font-size: 24rpx;
  color: var(--q-ink-3);
}
.ig-value {
  display: block;
  font-size: 28rpx;
  font-weight: 500;
  color: var(--q-ink-1);
  margin-top: 4rpx;
}
.req-text {
  font-size: 28rpx;
  color: var(--q-ink-2);
  line-height: 1.7;
}
.intro-text {
  font-size: 28rpx;
  color: var(--q-ink-2);
  line-height: 1.7;
}
.hobby-tag {
  gap: 6rpx;
  padding: 8rpx 20rpx;
  border-radius: 999rpx;
  background: rgba(139, 37, 74, 0.05);
  font-size: 24rpx;
  color: var(--q-ink-2);
}

/* ---------- AI 摘要 ---------- */
.mini-stat {
  flex: 1;
  padding: 20rpx;
  border-radius: 20rpx;
  background: var(--q-primary-soft);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
}
.mini-label {
  font-size: 22rpx;
  color: var(--q-ink-3);
}
.mini-value {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--q-primary);
}
.suggest-box {
  margin-top: 24rpx;
  padding: 24rpx;
  border-radius: 20rpx;
  background: var(--q-gold-soft);
  border: 1px solid rgba(212, 175, 55, 0.25);
}
.suggest-text {
  font-size: 26rpx;
  color: var(--q-gold-dark);
  line-height: 1.6;
}
.remind-text {
  display: block;
  font-size: 24rpx;
  color: var(--q-ink-3);
  margin-top: 12rpx;
}

/* ---------- 跟进时间线 ---------- */
.add-btn {
  gap: 8rpx;
  padding: 12rpx 24rpx;
  border-radius: 999rpx;
  background: var(--q-primary-soft);
  color: var(--q-primary);
  font-size: 24rpx;
  font-weight: 600;
}
.fu-form {
  margin-top: 24rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.5);
  border: 1px solid rgba(139, 37, 74, 0.08);
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}
.fu-row {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}
.fu-label {
  font-size: 24rpx;
  color: var(--q-ink-3);
}
.sel-pill {
  padding: 10rpx 24rpx;
  border-radius: 999rpx;
  background: var(--q-glass-bg);
  border: 1px solid var(--q-glass-border);
  font-size: 24rpx;
  color: var(--q-ink-2);
}
.sel-active {
  background: var(--q-primary);
  border-color: var(--q-primary);
  color: #fff;
  font-weight: 600;
}
.fu-textarea {
  height: 160rpx;
  padding: 20rpx;
  border-radius: 16rpx;
  background: #fff;
  font-size: 26rpx;
}
.fu-input {
  height: 72rpx;
  padding: 0 24rpx;
  border-radius: 16rpx;
  background: #fff;
  font-size: 26rpx;
}
.fill-btn {
  padding: 0 24rpx;
  border-radius: 16rpx;
  background: var(--q-primary-soft);
  font-size: 24rpx;
  color: var(--q-primary);
}
.submit-btn {
  height: 84rpx;
  border-radius: 20rpx;
  background: var(--q-primary);
  color: #fff;
  font-size: 28rpx;
  font-weight: 600;
}
.submit-btn.disabled {
  opacity: 0.6;
}
.timeline {
  margin-top: 28rpx;
  display: flex;
  flex-direction: column;
  gap: 0;
}
.tl-item {
  display: flex;
  gap: 20rpx;
  position: relative;
  padding-bottom: 32rpx;
}
.tl-item::before {
  content: "";
  position: absolute;
  left: 9rpx;
  top: 28rpx;
  bottom: 0;
  width: 2rpx;
  background: rgba(139, 37, 74, 0.12);
}
.tl-item:last-child::before {
  display: none;
}
.tl-dot {
  width: 20rpx;
  height: 20rpx;
  border-radius: 50%;
  background: var(--q-primary);
  margin-top: 10rpx;
  flex-shrink: 0;
  box-shadow: 0 0 0 6rpx var(--q-primary-soft);
}
.tl-body {
  flex: 1;
  min-width: 0;
}
.tl-type {
  font-size: 26rpx;
  font-weight: 600;
  color: var(--q-ink-1);
}
.tl-outcome {
  font-size: 22rpx;
  padding: 2rpx 14rpx;
  border-radius: 999rpx;
}
.oc-good {
  background: var(--q-success-soft);
  color: var(--q-success);
}
.oc-mid {
  background: var(--q-warning-soft);
  color: var(--q-warning);
}
.oc-bad {
  background: var(--q-error-soft);
  color: var(--q-error);
}
.tl-time {
  font-size: 22rpx;
  color: var(--q-ink-4);
}
.tl-content {
  display: block;
  font-size: 26rpx;
  color: var(--q-ink-2);
  margin-top: 6rpx;
  line-height: 1.6;
}
.tl-next {
  display: block;
  font-size: 22rpx;
  color: var(--q-gold-dark);
  margin-top: 8rpx;
}
.empty-fu {
  padding: 40rpx 20rpx;
  text-align: center;
  font-size: 24rpx;
  color: var(--q-ink-4);
}

/* ---------- 牵线记录 ---------- */
.pair-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}
.pair-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  background: var(--q-primary-soft);
}
.pair-name {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--q-ink-1);
}
.pair-x {
  font-size: 24rpx;
  color: var(--q-ink-4);
}
.pair-score {
  font-size: 26rpx;
  font-weight: 700;
  color: var(--q-gold-dark);
}
.pair-status {
  font-size: 22rpx;
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
  background: var(--q-gold-soft);
  color: var(--q-gold-dark);
}
.ps-已成功 {
  background: var(--q-success-soft);
  color: var(--q-success);
}
.ps-已终止 {
  background: var(--q-error-soft);
  color: var(--q-error);
}

/* ---------- 空态 ---------- */
.empty-state {
  position: relative;
  z-index: 2;
  flex-direction: column;
  gap: 20rpx;
  padding: 160rpx 60rpx;
  text-align: center;
}
.empty-icon {
  width: 128rpx;
  height: 128rpx;
  border-radius: 50%;
  background: var(--q-primary-soft);
  border: 2rpx solid rgba(139, 37, 74, 0.12);
  margin-bottom: 8rpx;
}
.empty-title {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--q-ink-1);
}
.empty-sub {
  font-size: 26rpx;
  color: var(--q-ink-3);
}
.empty-btn {
  margin-top: 16rpx;
  padding: 20rpx 48rpx;
  border-radius: 999rpx;
  background: var(--q-primary);
  color: #fff;
  font-size: 28rpx;
  font-weight: 600;
  box-shadow: 0 10rpx 28rpx rgba(139, 37, 74, 0.25);
}

.hover-opacity {
  opacity: 0.6;
}
</style>
