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
    <h1 class="login-title">登录红娘工作台</h1>
    <p class="login-sub">请使用运营中心账号登录</p>

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
      <span v-if="!props.loading">登录</span>
      <span v-else>登录中…</span>
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
  position: relative; width: 100%; max-width: 420px; background: var(--dt-card-solid);
  border: 1px solid var(--dt-border);
  border-radius: var(--dt-radius-lg); padding: 36px 34px; box-shadow: var(--dt-shadow-lg);
}
.login-title { font-size: 1.5rem; font-weight: 600; color: var(--dt-ink-1); letter-spacing: -0.015em; margin: 0 0 6px; }
.login-sub { font-size: 0.875rem; color: var(--dt-ink-3); margin: 0 0 26px; }
.login-form :deep(.el-form-item) { margin-bottom: 0; }
.field {
  width: 100%;
  position: relative; display: flex; align-items: center; margin-bottom: 14px;
  background: var(--dt-input); border: 1px solid var(--dt-input-border); border-radius: var(--dt-radius-md);
  transition: border-color 0.15s, box-shadow 0.15s;
}
.field:focus-within { border-color: var(--dt-primary); box-shadow: 0 0 0 3px var(--dt-primary-soft-2); }
.field-icon { margin-left: 12px; color: var(--dt-ink-4); flex-shrink: 0; }
.field-input { flex: 1; height: 44px; border: none; outline: none; background: transparent; padding: 0 12px; font-size: 0.875rem; font-family: var(--dt-font); color: var(--dt-ink-1); }
.field-input::placeholder { color: var(--dt-ink-4); }
.field-eye { margin-right: 12px; color: var(--dt-ink-4); cursor: pointer; }
.login-meta { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; }
.remember { display: flex; align-items: center; gap: 8px; font-size: 0.8125rem; color: var(--dt-ink-2); cursor: pointer; }
.remember input { accent-color: var(--dt-primary); width: 15px; height: 15px; }
.forgot { font-size: 0.8125rem; color: var(--dt-primary); cursor: pointer; }
.captcha-wrap { margin-bottom: 16px; }
.lock-tip { margin-top: 10px; font-size: 0.8125rem; color: var(--dt-error); text-align: center; }
.btn-login:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-login {
  width: 100%; height: 44px; border: none; border-radius: var(--dt-radius-md);
  background: var(--dt-primary); color: #fff; font-size: 0.9375rem; font-weight: 500;
  cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 10px;
  transition: background 0.15s; font-family: var(--dt-font);
}
.btn-login:hover:not(:disabled) { background: var(--dt-primary-light); }
.btn-login.loading { opacity: 0.8; }
.divider { display: flex; align-items: center; gap: 12px; margin: 24px 0 16px; color: var(--dt-ink-4); font-size: 0.75rem; }
.divider::before, .divider::after { content: ''; flex: 1; height: 1px; background: var(--dt-border); }
.oauth-row { display: flex; justify-content: center; gap: 12px; margin-bottom: 8px; }
.oauth-btn {
  width: 42px; height: 42px; border-radius: var(--dt-radius-md); display: flex; align-items: center; justify-content: center;
  font-weight: 500; font-size: 0.875rem; cursor: pointer; border: 1px solid var(--dt-border);
  background: var(--dt-card-solid); transition: border-color 0.15s, background 0.15s;
}
.oauth-btn:hover { border-color: var(--dt-border-strong); background: var(--dt-muted); }
.oauth-btn.wechat { color: #1a9c5b; }
.oauth-btn.dingtalk { color: #1c6fd0; }
.oauth-btn.sso { color: var(--dt-ink-2); }
.oauth-btn.disabled { opacity: 0.5; cursor: not-allowed; }
.oauth-btn.disabled:hover { border-color: var(--dt-border); background: var(--dt-card-solid); }
.oauth-tip { text-align: center; font-size: 0.75rem; color: var(--dt-ink-4); margin-bottom: 20px; }
.login-footer { text-align: center; font-size: 0.8125rem; color: var(--dt-ink-3); }
.apply { color: var(--dt-primary); font-weight: 500; cursor: pointer; }
.copyright { text-align: center; font-size: 0.75rem; color: var(--dt-ink-4); margin-top: 18px; }
.policy { text-align: center; font-size: 0.75rem; color: var(--dt-ink-4); margin-top: 6px; }
.policy a { cursor: pointer; }
.policy a:hover { color: var(--dt-primary); }
</style>
