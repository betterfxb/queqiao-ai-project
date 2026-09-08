<script setup lang="ts">
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app'
import { getToken } from '@/api/index'

// 最小路由守卫：未登录时拦截页面跳转，强制回登录页（Token 透传结构已在 api/index.ts 请求拦截器搭建）
const guard = (args: any) => {
  if (!getToken() && args && typeof args.url === 'string' && !args.url.includes('/pages/login/')) {
    uni.reLaunch({ url: '/pages/login/index' })
    return false
  }
  return true
}

onLaunch(() => {
  console.log('鹊桥 · 红娘工作台 launched')
  uni.addInterceptor('navigateTo', { invoke: guard })
  uni.addInterceptor('redirectTo', { invoke: guard })
  if (!getToken()) {
    uni.reLaunch({ url: '/pages/login/index' })
  }
})
onShow(() => {
  console.log('App Show')
})
onHide(() => {
  console.log('App Hide')
})
</script>

<style lang="scss">
/* 全局样式已在 tokens.scss 中通过 page 选择器注入 */
</style>
