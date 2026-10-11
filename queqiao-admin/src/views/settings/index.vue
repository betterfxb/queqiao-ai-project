<script setup lang="ts">
/**
 * 系统设置 — 对齐 UI 设计稿的「五栏子菜单」结构：
 *   基础设置 / 业务设置 / 团队管理 / 系统 / 个人资料设置
 * 所有配置真实落库（PUT /api/settings）并由后端业务消费；团队管理走独立账号接口。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchSettings, updateSettings, type SettingItem, type SettingsGrouped } from '@/api'
import ProfileSection from './components/ProfileSection.vue'
import DynamicFields from './components/DynamicFields.vue'
import NotifySection from './components/NotifySection.vue'
import ThemeSection from './components/ThemeSection.vue'
import SecuritySection from './components/SecuritySection.vue'
import LogSection from './components/LogSection.vue'
import TeamSection from './components/TeamSection.vue'

const TABS = [
  { key: 'basic', label: '基础设置', icon: 'building' },
  { key: 'biz', label: '业务设置', icon: 'sliders' },
  { key: 'team', label: '团队管理', icon: 'users' },
  { key: 'commission', label: '分佣设置', icon: 'award' },
  { key: 'system', label: '系统', icon: 'cog' },
  { key: 'profile', label: '个人资料设置', icon: 'user' },
]
const tab = ref('basic')

const groups = ref<SettingsGrouped>({})
const model = reactive<Record<string, string>>({})
const original = reactive<Record<string, string>>({})
const loading = ref(false)

const group = (name: string): SettingItem[] => groups.value[name] ?? []

/** 业务设置聚合：业务规则 + AI 设置 */
const bizItems = computed<SettingItem[]>(() => [...group('业务规则'), ...group('AI 设置')])

const load = async () => {
  loading.value = true
  try {
    const data = await fetchSettings()
    groups.value = data ?? {}
    Object.keys(model).forEach((k) => delete model[k])
    Object.keys(original).forEach((k) => delete original[k])
    Object.values(data ?? {}).forEach((list) =>
      (list ?? []).forEach((it) => {
        model[it.id] = it.value ?? ''
        original[it.id] = it.value ?? ''
      }),
    )
    // 业务规则阈值：若后端尚未创建（数据库已初始化），补齐默认值以便在此编辑并保存
    for (const [k, v] of [['biz.churnDays', '14'], ['biz.renewalDays', '15']] as const) {
      if (!(k in model)) {
        model[k] = v
        original[k] = v
      }
    }
  } catch (e: any) {
    ElMessage.error('设置加载失败：' + (e?.message ?? '未知错误'))
  } finally {
    loading.value = false
  }
}
onMounted(load)

const changed = computed(() => Object.keys(model).filter((k) => model[k] !== original[k]))
const dirty = computed(() => changed.value.length > 0)

const saving = ref(false)
const saveSettings = async () => {
  if (!dirty.value) {
    ElMessage.info('没有需要保存的变更')
    return
  }
  saving.value = true
  try {
    const payload: Record<string, string> = {}
    changed.value.forEach((k) => (payload[k] = model[k]))
    const res = await updateSettings(payload)
    changed.value.forEach((k) => (original[k] = model[k]))
    ElMessage.success(`已保存 ${res?.count ?? Object.keys(payload).length} 项设置`)
    await load()
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.message ?? '未知错误'))
  } finally {
    saving.value = false
  }
}

const resetSettings = () => {
  Object.keys(original).forEach((k) => (model[k] = original[k]))
  ElMessage.info('已还原未保存的修改')
}
</script>

<template>
  <div class="settings-layout" v-loading="loading">
    <!-- 左侧：对齐设计稿的五栏子菜单 -->
    <aside class="settings-nav glass-card">
      <button
        v-for="t in TABS"
        :key="t.key"
        class="settings-nav-item"
        :class="{ active: tab === t.key }"
        @click="tab = t.key"
      >
        <app-icon :name="t.icon" :size="16" />
        <span>{{ t.label }}</span>
      </button>
    </aside>

    <!-- 右侧内容 -->
    <div class="settings-content">
      <div class="content-header">
        <div>
          <h1>系统设置</h1>
          <p>
            管理机构信息、业务规则、团队与通知安全策略
            <span v-if="dirty" class="dirty-tip">· {{ changed.length }} 项未保存</span>
          </p>
        </div>
        <div class="header-actions">
          <button class="btn btn-ghost" :disabled="!dirty || saving" @click="resetSettings">
            <app-icon name="rotate-ccw" :size="16" /> 还原
          </button>
          <button class="btn btn-gold" :disabled="saving" @click="saveSettings">
            <app-icon name="save" :size="16" /> {{ saving ? '保存中…' : '保存设置' }}
          </button>
        </div>
      </div>

      <!-- 基础设置 -->
      <section v-show="tab === 'basic'" class="glass-card setting-card">
        <section-header title="机构基础信息" />
        <DynamicFields :items="group('基础信息')" :model="model" />
      </section>

      <!-- 业务设置 -->
      <section v-show="tab === 'biz'" class="glass-card setting-card">
        <section-header title="业务规则与 AI 能力" />
        <p class="tab-hint">
          匹配度阈值、线索沉睡天数、流失/续费提醒天数等会实时驱动「智能匹配」「客户跟进」「智能运营」的预警逻辑；
          AI 开关关闭后全部 AI 调用自动降级为本地规则。
        </p>
        <DynamicFields :items="bizItems" :model="model" />
      </section>

      <!-- 团队管理 -->
      <section v-show="tab === 'team'">
        <TeamSection />
      </section>

      <!-- 分佣设置 -->
      <section v-show="tab === 'commission'" class="glass-card setting-card">
        <section-header title="红娘分佣比例" />
        <p class="tab-hint">
          成功撮合佣金比例、续费分成比例均以百分比填写，保存后实时驱动「佣金结算」的自动计提；
          牵线成功或续费支付完成时将按此比例生成红娘佣金记录。
        </p>
        <DynamicFields :items="group('分佣设置')" :model="model" />
      </section>

      <!-- 系统：通知 + 主题 + 日志 -->
      <template v-if="tab === 'system'">
        <NotifySection
          :notify-items="group('通知设置')"
          :ai-items="group('AI 设置')"
          :model="model"
        />
        <ThemeSection />
        <LogSection />
      </template>

      <!-- 个人资料设置：资料卡 + 安全 + 改密 -->
      <template v-if="tab === 'profile'">
        <ProfileSection />
        <SecuritySection :items="group('安全设置')" :model="model" />
      </template>
    </div>
  </div>
</template>

<style scoped>
.settings-layout {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}
.settings-nav {
  width: 200px;
  flex-shrink: 0;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  position: sticky;
  top: 0;
}
.settings-nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 14px;
  border: none;
  border-radius: var(--dt-radius-md);
  background: transparent;
  font-size: 0.875rem;
  color: var(--dt-ink-2);
  cursor: pointer;
  transition: all 0.16s ease;
  text-align: left;
}
.settings-nav-item:hover { background: var(--dt-muted); color: var(--dt-primary); }
.settings-nav-item.active {
  background: var(--dt-primary);
  color: #fff;
  font-weight: 600;
}

.settings-content { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 20px; }
.content-header {
  display: flex; align-items: flex-start; justify-content: space-between; gap: 16px;
}
.content-header h1 { font-size: 1.375rem; font-weight: 600; color: var(--dt-ink-1); margin: 0; }
.content-header p { margin: 4px 0 0; color: var(--dt-ink-3); font-size: 0.8125rem; }
.dirty-tip { color: var(--dt-warning); font-weight: 600; }
.header-actions { display: flex; gap: 10px; flex-shrink: 0; }

.setting-card { padding: 22px 24px; }
.setting-card :deep(.section-header) { margin-bottom: 18px; }
.tab-hint {
  margin: 0 0 18px;
  font-size: 0.7813rem;
  color: var(--dt-ink-3);
  background: var(--dt-card-solid);
  border: 1px solid var(--dt-border);
  border-radius: var(--dt-radius-md);
  padding: 12px 14px;
  line-height: 1.7;
}

@media (max-width: 1100px) {
  .settings-layout { flex-direction: column; }
  .settings-nav { width: 100%; flex-direction: row; flex-wrap: wrap; position: static; }
}
</style>
