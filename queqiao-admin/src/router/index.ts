import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { routes } from './routes'

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

// 鉴权守卫：除登录页与公开大屏外，未登录一律重定向到登录页
const PUBLIC_PATHS = ['/login', '/screen']
router.beforeEach((to) => {
  const auth = useAuthStore()
  const path = to.path
  const isPublic = PUBLIC_PATHS.some((p) => path === p || path.startsWith(`${p}/`))
  if (!isPublic && !auth.isAuthenticated) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (path === '/login' && auth.isAuthenticated) {
    return { path: (to.query.redirect as string) || '/' }
  }
  return true
})

router.afterEach((to) => {
  document.title = `${to.meta.title ?? ''} · 牵线运营中心`
})

export default router
