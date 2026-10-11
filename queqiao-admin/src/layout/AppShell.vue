<script setup lang="ts">
/**
 * AppShell — 运营中心共享外壳
 * 左侧 64px 玻璃图标导航 + 顶部 64px 顶栏（面包屑/标题 + 全局搜索 + 操作）+ 内容滚动区
 */
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NAV_ITEMS } from '@/constants/nav'
import { useRealtimeStore } from '@/stores/realtime'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const realtimeStore = useRealtimeStore()
const authStore = useAuthStore()

const navItems = NAV_ITEMS

const activeKey = computed(() => (route.meta.nav as string) ?? 'dashboard')
const pageTitle = computed(() => (route.meta.title as string) ?? '牵线运营中心')

const crumbs = computed(() => ['运营中心', pageTitle.value])

const search = ref('')

const onlineCount = computed(() => realtimeStore.onlineAgents.length)
const isWsOpen = computed(() => realtimeStore.status === 'open')
const onlineTip = computed(() =>
  realtimeStore.onlineAgents.length ? '在线红娘：' + realtimeStore.onlineAgents.join('、') : '当前无红娘在线',
)

const go = (path: string) => router.push(path)
const goScreen = () => router.push('/screen')
const goLogin = async () => {
  // 先走 store.logout()（清 sessionStorage + 调后端 /auth/logout）再跳转，避免令牌残留
  try {
    await authStore.logout()
  } catch {
    /* 忽略登出异常，仍跳转登录页 */
  }
  router.push('/login')
}

// 顶部用户菜单（退出登录入口）
const showUserMenu = ref(false)
const toggleUserMenu = () => (showUserMenu.value = !showUserMenu.value)
const closeUserMenu = () => (showUserMenu.value = false)
const roleLabel = computed(() => {
  const r = authStore.role
  if (r === 'ADMIN') return '系统管理员'
  if (r === 'MANAGER') return '运营经理'
  if (r === 'AGENT') return '红娘'
  return r || ''
})

// 登录后即建立实时长连接（红娘在线看板 + 会话消息实时推送）
onMounted(() => realtimeStore.init())
</script>

<template>
  <div id="app-shell">
    <!-- ============ 左侧图标导航 ============ -->
    <nav id="left-nav">
      <div class="nav-logo" title="牵线运营中心">牵</div>
      <div class="nav-items">
        <a
          v-for="item in navItems"
          :key="item.key"
          class="nav-item"
          :class="{ active: activeKey === item.key }"
          :title="item.label"
          @click="go(item.path)"
        >
          <app-icon :name="item.icon" :size="20" />
          <span class="nav-label">{{ item.label }}</span>
          <span v-if="item.badge" class="nav-dot" />
        </a>
      </div>
      <div class="nav-bottom">
        <div class="nav-item screen-link" title="数据大屏" @click="goScreen">
          <app-icon name="monitor" :size="20" />
          <span class="nav-label">数据大屏</span>
        </div>
        <div class="nav-avatar" title="退出登录" @click="goLogin">王</div>
      </div>
    </nav>

    <!-- ============ 主内容区 ============ -->
    <div id="main-area">
      <div id="top-bar">
        <div class="title-wrap">
          <span class="crumb-root">运营中心</span>
          <app-icon name="chevron-right" :size="14" class="crumb-sep" />
          <span class="page-title">{{ pageTitle }}</span>
        </div>
        <div class="search-wrap">
          <div class="search-box">
            <app-icon name="search" :size="16" />
            <input v-model="search" type="text" placeholder="搜索会员、红娘、活动..." />
            <kbd>Ctrl K</kbd>
          </div>
        </div>
        <div class="top-bar-actions">
          <button class="icon-btn" title="通知">
            <app-icon name="bell" :size="18" />
            <span class="badge" />
          </button>
          <button class="icon-btn" title="消息" @click="go('/messages')">
            <app-icon name="message" :size="18" />
          </button>
          <!-- 实时在线红娘看板（WebSocket /topic/presence） -->
          <div class="presence-pill" :title="onlineTip" :class="{ live: isWsOpen }">
            <span class="presence-dot" :class="{ on: isWsOpen }" />
            <span class="presence-count">{{ onlineCount }}</span>
            <span class="presence-label">在线</span>
          </div>
          <div class="v-divider" />
          <div class="top-avatar" title="点击查看账号 / 退出登录" @click="toggleUserMenu">
            王
            <div v-if="showUserMenu" class="user-menu" @click.stop>
              <div class="um-head">
                <div class="um-name">{{ authStore.user?.name || '用户' }}</div>
                <div class="um-role">{{ roleLabel }}</div>
              </div>
              <button class="um-item" @click="goLogin">退出登录</button>
            </div>
          </div>
        </div>
      </div>

      <div id="content-scroll">
        <router-view />
      </div>
      <div v-if="showUserMenu" class="user-mask" @click="closeUserMenu" />
    </div>
  </div>
</template>

<style scoped>
#app-shell {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--dt-bg-base);
}

/* ---------- 左侧导航 ---------- */
#left-nav {
  width: 64px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 14px 0;
  background: var(--dt-card-solid);
  border-right: 1px solid var(--dt-border);
  z-index: 20;
  position: relative;
}
.nav-logo {
  width: 36px;
  height: 36px;
  border-radius: var(--dt-radius-md);
  background: var(--dt-primary);
  color: #fff;
  font-size: 1.0625rem;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 18px;
  flex-shrink: 0;
}
.nav-items {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  overflow-y: auto;
  overflow-x: hidden;
  flex: 1;
  padding: 0 8px;
  scrollbar-width: none;
}
.nav-items::-webkit-scrollbar { display: none; }
.nav-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  height: 52px;
  border-radius: var(--dt-radius-md);
  color: var(--dt-ink-3);
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
  flex-shrink: 0;
}
.nav-item:hover { color: var(--dt-ink-1); background: var(--dt-muted); }
.nav-item.active {
  color: var(--dt-primary);
  background: var(--dt-primary-soft-2);
}
.nav-item.active::before {
  content: '';
  position: absolute;
  left: -8px;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 22px;
  border-radius: 0 2px 2px 0;
  background: var(--dt-primary);
}
.nav-label {
  font-size: 0.625rem;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
}
.nav-dot {
  position: absolute;
  top: 9px;
  right: 10px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--dt-error);
}
.nav-bottom {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  width: 100%;
  padding: 6px 8px 0;
  border-top: 1px solid var(--dt-border);
}
.screen-link { width: 100%; }
.nav-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #8d847c;
  color: #fff;
  font-size: 0.875rem;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
}

/* ---------- 主区域 ---------- */
#main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  position: relative;
  z-index: 1;
}
#top-bar {
  height: 56px;
  flex-shrink: 0;
  /* 顶栏为独立堆叠上下文：显式给 z-index，
     避免头像下拉卡片(.user-menu z-index:40)被 #content-scroll(z-index:1) 覆盖。 */
  position: relative;
  z-index: 40;
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 0 24px;
  background: var(--dt-card-solid);
  border-bottom: 1px solid var(--dt-border);
}
.title-wrap { display: flex; align-items: center; gap: 6px; white-space: nowrap; }
.crumb-root { font-size: 0.8125rem; color: var(--dt-ink-3); }
.crumb-sep { color: var(--dt-ink-4); }
.page-title { font-size: 1rem; font-weight: 600; color: var(--dt-ink-1); letter-spacing: -0.005em; }
.search-wrap { flex: 1; display: flex; justify-content: center; }
.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  width: min(400px, 100%);
  height: 34px;
  padding: 0 12px;
  border-radius: var(--dt-radius-md);
  background: var(--dt-bg-base);
  border: 1px solid var(--dt-border);
  color: var(--dt-ink-3);
  transition: border-color 0.15s ease, background 0.15s ease;
}
.search-box:focus-within { background: var(--dt-card-solid); border-color: var(--dt-primary); }
.search-box input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-family: var(--dt-font);
  font-size: 0.8125rem;
  color: var(--dt-ink-1);
}
.search-box input::placeholder { color: var(--dt-ink-4); }
.search-box kbd {
  font-size: 0.6875rem;
  color: var(--dt-ink-4);
  background: var(--dt-card-solid);
  padding: 1px 5px;
  border-radius: var(--dt-radius-sm);
  border: 1px solid var(--dt-border);
  font-family: inherit;
}
.top-bar-actions { display: flex; align-items: center; gap: 6px; }
.presence-pill {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 9px;
  border-radius: var(--dt-radius-md);
  background: var(--dt-bg-base);
  border: 1px solid var(--dt-border);
  color: var(--dt-ink-3);
  font-size: 0.75rem;
  cursor: default;
  user-select: none;
  transition: border-color 0.2s ease;
}
.presence-pill.live { border-color: rgba(63, 112, 80, 0.35); }
.presence-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--dt-ink-4);
  transition: background 0.2s ease;
}
.presence-dot.on { background: #3f9c68; }
.presence-count { font-weight: 600; color: var(--dt-ink-1); }
.presence-label { font-size: 0.6875rem; color: var(--dt-ink-3); }
.v-divider { width: 1px; height: 20px; background: var(--dt-border); margin: 0 4px; }
.top-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--dt-primary);
  color: #fff;
  font-size: 0.8125rem;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

#content-scroll {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 20px 24px 40px;
  position: relative;
  z-index: 1;
}

/* ---------- 顶部用户菜单（退出登录） ---------- */
.user-mask {
  position: fixed;
  inset: 0;
  z-index: 30;
  background: transparent;
}
.top-avatar { position: relative; }
.user-menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  width: 196px;
  padding: 6px;
  border-radius: var(--dt-radius-lg);
  background: var(--dt-card-solid);
  border: 1px solid var(--dt-border);
  box-shadow: var(--dt-shadow-lg);
  z-index: 40;
  animation: um-pop 0.14s ease;
}
@keyframes um-pop {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}
.um-head {
  padding: 8px 10px 10px;
  border-bottom: 1px solid var(--dt-border);
  margin-bottom: 6px;
}
.um-name {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--dt-ink-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.um-role {
  margin-top: 3px;
  display: inline-block;
  font-size: 0.6875rem;
  font-weight: 500;
  color: var(--dt-ink-3);
  background: var(--dt-muted);
  padding: 1px 6px;
  border-radius: var(--dt-radius-sm);
}
.um-item {
  width: 100%;
  height: 34px;
  border: none;
  border-radius: var(--dt-radius-md);
  background: transparent;
  color: var(--dt-ink-2);
  font-family: var(--dt-font);
  font-size: 0.8125rem;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: background 0.15s ease, color 0.15s ease;
}
.um-item:hover {
  background: var(--dt-muted);
  color: var(--dt-ink-1);
}
</style>
