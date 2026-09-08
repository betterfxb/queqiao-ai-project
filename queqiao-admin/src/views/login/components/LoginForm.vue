<script setup lang="ts">
/**
 * 登录表单卡片：账号 / 密码 + 滑块人机验证 + 失败限流
 * 校验全部通过后 emit('submit', account)，由页面负责写会话与跳转。
 */
import { reactive, ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

const props = withDefaults(defineProps<{ loading?: boolean }>(), { loading: false })
const emit = defineEmits<{
  (e: 'submit', payload: { account: string; password: string }): void
}>()

const form = reactive({ account: '', password: '' })
const formRef = ref<FormInstance>()
const rules: FormRules = {
  account: [{ required: true, message: '请输入账号或手机号', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' },
  ],
}

const remember = ref(true)
const showPwd = ref(false)

// —— 安全：滑块验证码 + 登录失败限流 ——
const captchaOk = ref(false)
const MAX_FAILS = 5
const LOCK_MS = 60_000
const fails = ref(0)
const lockUntil = ref(0)
const locked = computed(() => Date.now() < lockUntil.value)
const remainSec = ref(0)
let timer: number | undefined

const onCaptchaSuccess = () => {
  captchaOk.value = true
}
const onCaptchaReset = () => {
  captchaOk.value = false
}

function tick() {
  if (locked.value) {
    remainSec.value = Math.ceil((lockUntil.value - Date.now()) / 1000)
    if (remainSec.value <= 0) {
      fails.value = 0
      window.clearInterval(timer)
      timer = undefined
    }
  }
}

/** 未开放能力：给出明确提示，避免装饰按钮误导 */
const comingSoon = (name: string) => {
  ElMessage.info(`${name} 即将开放，敬请期待`)
}

const onLogin = async () => {
  if (locked.value) {
    ElMessage.warning(`尝试过于频繁，请 ${remainSec.value}s 后再试`)
    return
  }
  if (!captchaOk.value) {
    ElMessage.warning('请先完成滑块拼图验证')
    return
  }
  // Element Plus 表单校验：账号必填、密码至少 6 位
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  // 原型阶段：任意合法账号密码即通过；真实环境在此调用 /api/auth/login 并由后端校验
  const ok = form.account.trim().length > 0 && form.password.length >= 6
  if (!ok) {
    fails.value += 1
    ElMessage.error('账号或密码错误')
    if (fails.value >= MAX_FAILS) {
      lockUntil.value = Date.now() + LOCK_MS
      remainSec.value = LOCK_MS / 1000
      timer = window.setInterval(tick, 1000)
      ElMessage.error('失败次数过多，账号已临时锁定 60 秒')
    }
    return
  }
  emit('submit', { account: form.account.trim(), password: form.password })
}
</script>

<template>
  <div class="login-card">
    <div class="card-trim" />
    <span class="welcome-tag">WELCOME</span>
    <h1 class="login-title">欢迎回来</h1>
    <p class="login-sub">请登录您的红娘工作台账户</p>

    <el-form ref="formRef" :model="form" :rules="rules" class="login-form">
      <el-form-item prop="account">
        <div class="field">
          <app-icon name="user" :size="18" class="field-icon" />
          <input v-model="form.account" type="text" placeholder="账号 / 手机号" class="field-input" />
        </div>
      </el-form-item>
      <el-form-item prop="password">
        <div class="field">
          <app-icon name="shield-check" :size="18" class="field-icon" />
          <input v-model="form.password" :type="showPwd ? 'text' : 'password'" placeholder="密码" class="field-input" @keyup.enter="onLogin" />
          <app-icon :name="showPwd ? 'eye' : 'eye'" :size="18" class="field-eye" @click="showPwd = !showPwd" />
        </div>
      </el-form-item>
    </el-form>

    <div class="login-meta">
      <label class="remember">
        <input v-model="remember" type="checkbox" />
        <span>记住我</span>
      </label>
      <a class="forgot" @click="comingSoon('找回密码')">忘记密码？</a>
    </div>

    <div class="captcha-wrap">
      <slider-captcha @success="onCaptchaSuccess" @reset="onCaptchaReset" />
      <p v-if="locked" class="lock-tip">账号已临时锁定，请 {{ remainSec }}s 后重试</p>
    </div>

    <button class="btn-login" :class="{ loading: props.loading }" :disabled="locked" @click="onLogin">
      <span v-if="!props.loading">登 录</span>
      <span v-else>登录中…</span>
      <app-icon name="arrow-left" :size="18" style="transform: rotate(180deg)" />
    </button>

    <div class="divider"><span>其他登录方式</span></div>
    <div class="oauth-row">
      <div class="oauth-btn wechat disabled" title="微信登录（即将开放）" @click="comingSoon('微信登录')">微</div>
      <div class="oauth-btn dingtalk disabled" title="钉钉登录（即将开放）" @click="comingSoon('钉钉登录')">钉</div>
      <div class="oauth-btn sso disabled" title="SSO 登录（即将开放）" @click="comingSoon('SSO 登录')">S</div>
    </div>
    <p class="oauth-tip">第三方登录陆续开放中</p>

    <div class="login-footer">
      <span>还没有账号？<a class="apply" @click="comingSoon('账号申请开通')">申请开通</a></span>
    </div>
    <div class="copyright">© 2026 牵线运营中心 · 让爱更专业</div>
    <div class="policy"><a>用户协议</a> · <a>隐私政策</a></div>
  </div>
</template>

<style scoped>
.login-card {
  position: relative; width: 100%; max-width: 460px; background: var(--dt-glass);
  backdrop-filter: var(--dt-glass-blur); border: 1px solid var(--dt-glass-border);
  border-radius: var(--dt-radius-xl); padding: 44px 40px; box-shadow: var(--dt-shadow-xl);
}
.card-trim { position: absolute; top: 0; left: 32px; right: 32px; height: 4px; border-radius: 0 0 4px 4px; background: var(--dt-gold-gradient); }
.welcome-tag {
  display: inline-block; font-size: 0.6875rem; letter-spacing: 0.2em; font-weight: 700;
  color: var(--dt-gold-dark); background: var(--dt-gold-soft); padding: 4px 12px; border-radius: 999px;
}
.login-title { font-size: 2.125rem; font-weight: 800; color: var(--dt-primary); margin: 14px 0 6px; }
.login-sub { font-size: 0.875rem; color: var(--dt-ink-3); margin-bottom: 28px; }
.login-form :deep(.el-form-item) { margin-bottom: 0; }
.field {
  width: 100%;
  position: relative; display: flex; align-items: center; margin-bottom: 18px;
  background: var(--dt-input); border: 1px solid var(--dt-input-border); border-radius: var(--dt-radius-md);
  transition: box-shadow 0.18s, border-color 0.18s;
}
.field:focus-within { border-color: var(--dt-gold); box-shadow: 0 0 0 4px rgba(201, 169, 110, 0.18); }
.field-icon { margin-left: 14px; color: var(--dt-ink-3); flex-shrink: 0; }
.field-input { flex: 1; height: 50px; border: none; outline: none; background: transparent; padding: 0 14px; font-size: 0.875rem; font-family: var(--dt-font); color: var(--dt-ink-1); }
.field-input::placeholder { color: var(--dt-ink-4); }
.field-eye { margin-right: 14px; color: var(--dt-ink-4); cursor: pointer; }
.login-meta { display: flex; align-items: center; justify-content: space-between; margin-bottom: 22px; }
.remember { display: flex; align-items: center; gap: 8px; font-size: 0.8125rem; color: var(--dt-ink-2); cursor: pointer; }
.remember input { accent-color: var(--dt-gold); width: 16px; height: 16px; }
.forgot { font-size: 0.8125rem; color: var(--dt-primary); cursor: pointer; }
.captcha-wrap { margin-bottom: 18px; }
.lock-tip { margin-top: 10px; font-size: 0.8125rem; color: #b53a4a; text-align: center; }
.btn-login:disabled { opacity: 0.55; cursor: not-allowed; }
.btn-login {
  width: 100%; height: 52px; border: none; border-radius: var(--dt-radius-md);
  background: var(--dt-gold-gradient); color: #fff; font-size: 1rem; font-weight: 700;
  letter-spacing: 0.3em; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 10px;
  box-shadow: 0 12px 28px -10px rgba(168, 138, 80, 0.55); transition: all 0.2s; font-family: var(--dt-font);
}
.btn-login:hover { filter: brightness(1.06); transform: translateY(-1px); }
.btn-login.loading { opacity: 0.8; }
.divider { display: flex; align-items: center; gap: 12px; margin: 26px 0 18px; color: var(--dt-ink-4); font-size: 0.75rem; }
.divider::before, .divider::after { content: ''; flex: 1; height: 1px; background: var(--dt-border); }
.oauth-row { display: flex; justify-content: center; gap: 18px; margin-bottom: 8px; }
.oauth-btn {
  width: 46px; height: 46px; border-radius: 50%; display: flex; align-items: center; justify-content: center;
  font-weight: 700; font-size: 0.9375rem; cursor: pointer; border: 1px solid var(--dt-border);
  background: var(--dt-card-solid); transition: transform 0.15s;
}
.oauth-btn:hover { transform: translateY(-2px); }
.oauth-btn.wechat { color: #07c160; }
.oauth-btn.dingtalk { color: #1677ff; }
.oauth-btn.sso { color: var(--dt-gold-dark); border-color: var(--dt-gold); }
.oauth-btn.disabled { opacity: 0.45; cursor: not-allowed; filter: grayscale(0.4); }
.oauth-btn.disabled:hover { transform: none; }
.oauth-tip { text-align: center; font-size: 0.75rem; color: var(--dt-ink-4); margin-bottom: 20px; }
.login-footer { text-align: center; font-size: 0.8125rem; color: var(--dt-ink-3); }
.apply { color: var(--dt-primary); font-weight: 600; cursor: pointer; }
.copyright { text-align: center; font-size: 0.75rem; color: var(--dt-ink-4); margin-top: 20px; }
.policy { text-align: center; font-size: 0.75rem; color: var(--dt-ink-4); margin-top: 6px; }
.policy a { cursor: pointer; }
.policy a:hover { color: var(--dt-primary); }
</style>
