<script setup lang="ts">
/**
 * 智能录入与编辑详情页
 * 模式：无 id = 新建（可 AI 解析建档）；带 id = 编辑（自动回填）
 * 数据：POST/GET /api/members、POST /api/members/parse
 */
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { api, errMsg, type Member } from '@/api'

/* ---------- 状态栏 ---------- */
const statusBarHeight = ref(0)
try {
  statusBarHeight.value = uni.getWindowInfo().statusBarHeight ?? 0
} catch {
  statusBarHeight.value = 0
}

/* ---------- 编辑模式 ---------- */
const editId = ref('')
const isEdit = computed(() => !!editId.value)

/* ---------- 表单 ---------- */
const form = ref({
  name: '',
  age: undefined as number | undefined,
  gender: '男' as '男' | '女',
  phone: '',
  province: '',
  city: '',
  district: '',
  job: '',
  education: '',
  height: undefined as number | undefined,
  income: '',
  level: '',
  marriage: '',
  hasChild: '',
  house: '',
  car: '',
  bio: '',
  photo: '',
  requirement: '',
})
const customTags = ref<string[]>([])
const tagInput = ref('')

const addTag = () => {
  const t = tagInput.value.trim()
  if (t && !customTags.value.includes(t) && customTags.value.length < 10) {
    customTags.value.push(t)
  }
  tagInput.value = ''
}
const removeTag = (t: string) => {
  customTags.value = customTags.value.filter((x) => x !== t)
}

/**
 * 选项枚举：与后端校验白名单严格对齐，保证红娘端与采集页取值口径一致。
 * 学历/月收入后端 public/register 有白名单校验（VALID_EDUCATION / VALID_INCOME），
 * 取值不符会被拒绝，因此这两项必须逐字对齐；婚姻/子女/房产后端仅校验长度，
 * 给出常用值 + 允许自由输入，兼顾效率与灵活性。
 */
const EDUCATION_OPTS = ['高中及以下', '大专', '本科', '硕士', '博士']
const INCOME_OPTS = ['5k 以下', '5k-1w', '1w-2w', '2w-5w', '5w-10w', '10w 以上']
const LEVEL_OPTS = ['普通', '金卡', 'VIP', 'SVIP', '钻石VIP']
const MARRIAGE_OPTS = ['未婚', '离异无孩', '离异有孩', '丧偶']
const HAS_CHILD_OPTS = ['无', '有']
const HOUSE_OPTS = ['已购房', '无房', '与父母同住']

/** 快捷选项填充：再次点击已选中的值则取消（清空），方便纠错 */
const pick = (key: 'education' | 'income' | 'level' | 'marriage' | 'hasChild' | 'house', v: string) => {
  form.value[key] = form.value[key] === v ? '' : v
}

/* ---------- 照片 ---------- */
/** 压缩后长边上限（px）：兼顾清晰度与体积，800px 足够详情页与列表缩略图使用 */
const MAX_PHOTO_EDGE = 800

const onPickPhoto = () => {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    success: (res: any) => {
      const file = res.tempFiles?.[0]
      const path = res.tempFilePaths?.[0]
      compressToDataURL(file || path)
        .then((d) => {
          form.value.photo = d
        })
        .catch(() => uni.showToast({ title: '照片读取失败，请重试', icon: 'none' }))
    },
    fail: () => {
      /* 用户主动取消选图，不提示 */
    },
  })
}

/**
 * 把选中的图片压缩成 base64 dataURL。
 * 原图动辄数 MB，直接入库会拖垮接口与列表；这里按长边缩放到 800px 内并转 JPEG(0.8)，
 * 通常能压到 100KB 以内。非 H5 端拿不到 canvas 时直接抛错，由调用方提示，不静默存原图。
 */
const compressToDataURL = (src: any): Promise<string> =>
  new Promise((resolve, reject) => {
    if (typeof document === 'undefined') return reject(new Error('当前平台不支持图片压缩'))
    const img: any = new (window as any).Image()
    img.onload = () => {
      const scale = Math.min(1, MAX_PHOTO_EDGE / Math.max(img.width || 1, img.height || 1))
      const w = Math.max(1, Math.round((img.width || 1) * scale))
      const h = Math.max(1, Math.round((img.height || 1) * scale))
      const canvas = document.createElement('canvas')
      canvas.width = w
      canvas.height = h
      const ctx = canvas.getContext('2d')
      if (!ctx) return reject(new Error('canvas 不可用'))
      ctx.drawImage(img, 0, 0, w, h)
      resolve(canvas.toDataURL('image/jpeg', 0.8))
    }
    img.onerror = () => reject(new Error('图片解码失败'))
    // H5 端 tempFiles[0] 是 File 对象，需先读成 dataURL；App/小程序端是临时路径字符串
    if (src && typeof src !== 'string' && typeof (window as any).FileReader === 'function') {
      const fr = new (window as any).FileReader()
      fr.onload = () => {
        img.src = fr.result
      }
      fr.onerror = () => reject(new Error('图片读取失败'))
      fr.readAsDataURL(src)
    } else if (typeof src === 'string' && src) {
      img.src = src
    } else {
      reject(new Error('不支持的图片源'))
    }
  })

const onRemovePhoto = () => {
  form.value.photo = ''
}

/* ---------- AI 文本解析状态（需在 onLoad 之前声明，供 ai=1 自动弹窗引用） ---------- */
const aiFields = ref<{ key: string; label: string; value: string; adopted: boolean }[]>([])
const panelOpen = ref(false)
const parseModal = ref(false)
const parseText = ref('')
const parsing = ref(false)

/* ---------- 编辑回填 ---------- */
const backfill = (m: Member) => {
  form.value.name = m.name || ''
  form.value.age = m.age
  form.value.gender = m.gender === '女' ? '女' : '男'
  form.value.phone = m.phone || ''
  form.value.province = m.province || ''
  form.value.city = m.city || ''
  form.value.district = m.district || ''
  form.value.job = m.job || ''
  form.value.education = m.education || ''
  form.value.height = m.height
  form.value.income = m.income || ''
  form.value.level = m.level || ''
  form.value.marriage = m.marriage || ''
  form.value.hasChild = m.hasChild || ''
  form.value.house = m.house || ''
  form.value.car = m.car || ''
  form.value.bio = m.bio || ''
  form.value.photo = m.photo || ''
  form.value.requirement = m.requirement || ''
  customTags.value = (m.tags ? m.tags.split(/[,，、]/).filter(Boolean) : []).slice(0, 10)
}

onLoad(async (q) => {
  const id = String(q?.id || '')
  // ai=1：从工作台「AI 智能建档」进入，自动弹出解析框
  if (!id && String(q?.ai || '') === '1') {
    parseModal.value = true
  }
  if (id) {
    editId.value = id
    uni.setNavigationBarTitle({ title: '编辑会员资料' })
    try {
      const r = await api.memberDetail(id)
      backfill(r.member)
    } catch (e: any) {
      uni.showToast({ title: errMsg(e), icon: 'none' })
    }
  }
})

const FIELD_LABELS: Record<string, string> = {
  name: '姓名', age: '年龄', gender: '性别', phone: '手机号',
  province: '省份', city: '城市', district: '区县',
  job: '职业', education: '学历', height: '身高',
  income: '月收入', level: '等级', tags: '标签',
  marriage: '婚姻', hasChild: '子女', house: '房产', car: '座驾', bio: '自我介绍',
}

/** 安全解析整数：'32岁' -> 32，'三十二'/'32.5' 提取不到有效数字 -> undefined（防 NaN 入库） */
const parseNumberSafe = (v: any): number | undefined => {
  if (v === null || v === undefined || v === '') return undefined
  const n = typeof v === 'number' ? v : parseInt(String(v).replace(/\D/g, ''), 10)
  return Number.isFinite(n) ? n : undefined
}
/** 性别归一：'男性'/'男' -> '男'，'女性'/'女' -> '女'，其余 -> undefined（防类型漂移） */
const normalizeGender = (v: any): '男' | '女' | undefined => {
  const s = String(v || '').replace(/性$/, '')
  return s === '男' || s === '女' ? s : undefined
}

const doParse = async () => {
  if (!parseText.value.trim() || parsing.value) return
  parsing.value = true
  try {
    const res = await api.aiParse(parseText.value.trim())
    const f = res.fields || {}
    const adopted = new Set<string>()
    // —— 逐字段回填前先做归一/校验，只回填成功解析的字段 ——
    if (f.name) { form.value.name = f.name; adopted.add('name') }
    const age = parseNumberSafe(f.age)
    if (age !== undefined) { form.value.age = age; adopted.add('age') }
    const gender = normalizeGender(f.gender)
    if (gender) { form.value.gender = gender; adopted.add('gender') }
    if (f.phone) { form.value.phone = String(f.phone); adopted.add('phone') }
    if (f.city) { form.value.city = f.city; adopted.add('city') }
    if (f.job) { form.value.job = f.job; adopted.add('job') }
    if (f.education) { form.value.education = f.education; adopted.add('education') }
    const height = parseNumberSafe(f.height)
    if (height !== undefined) { form.value.height = height; adopted.add('height') }
    if (f.income) { form.value.income = f.income; adopted.add('income') }
    if (f.level) { form.value.level = f.level; adopted.add('level') }
    if (Array.isArray(f.tags) && f.tags.length) {
      customTags.value = [...new Set([...customTags.value, ...f.tags.map(String)])].slice(0, 10)
      adopted.add('tags')
    }
    aiFields.value = Object.entries(f)
      .filter(([, v]) => v !== null && v !== undefined && v !== '')
      .map(([k, v]) => ({
        key: k,
        label: FIELD_LABELS[k] || k,
        value: Array.isArray(v) ? v.join('、') : String(v),
        adopted: adopted.has(k),
      }))
    parseModal.value = false
    panelOpen.value = true
    uni.showToast({ title: `AI 识别 ${aiFields.value.length} 项信息`, icon: 'none' })
  } catch (e: any) {
    uni.showToast({ title: errMsg(e), icon: 'none' })
  } finally {
    parsing.value = false
  }
}

/* ---------- 底部操作 ---------- */
const saving = ref(false)
const onCancel = () => uni.navigateBack()

/** 空串归一为 undefined：后端 copyNonNull 只覆盖非空字段，传 '' 会误清已有资料 */
const blankToUndef = (v: string) => (v && v.trim() ? v.trim() : undefined)

const buildPayload = () => {
  const f = form.value
  return {
    name: f.name,
    age: f.age,
    gender: f.gender,
    phone: f.phone,
    province: blankToUndef(f.province),
    city: blankToUndef(f.city),
    district: blankToUndef(f.district),
    job: blankToUndef(f.job),
    education: blankToUndef(f.education),
    height: f.height,
    income: blankToUndef(f.income),
    level: blankToUndef(f.level),
    marriage: blankToUndef(f.marriage),
    hasChild: blankToUndef(f.hasChild),
    house: blankToUndef(f.house),
    car: blankToUndef(f.car),
    bio: blankToUndef(f.bio),
    photo: blankToUndef(f.photo),
    requirement: blankToUndef(f.requirement),
    tags: customTags.value.join(','),
  }
}

const onSave = async () => {
  if (saving.value) return
  const f = form.value
  if (!f.name.trim()) {
    uni.showToast({ title: '请填写姓名', icon: 'none' })
    return
  }
  // 保存前校验：年龄合理范围、手机号 11 位（不静默入库脏数据）
  if (f.age !== undefined && (f.age < 1 || f.age > 120)) {
    uni.showToast({ title: '年龄需在 1-120 之间', icon: 'none' })
    return
  }
  const phoneDigits = f.phone.replace(/\D/g, '')
  if (phoneDigits && phoneDigits.length !== 11) {
    uni.showToast({ title: '手机号需为 11 位', icon: 'none' })
    return
  }
  saving.value = true
  try {
    const payload = buildPayload()
    if (isEdit.value) {
      await api.updateMember(editId.value, payload)
    } else {
      await api.createMember(payload)
    }
    uni.showToast({ title: '已保存', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 500)
  } catch (e: any) {
    uni.showToast({ title: errMsg(e), icon: 'none' })
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <view class="page">
    <aurora-bg />
    <loading-red-line v-if="saving" :top="statusBarHeight + 'px'" />
    <view :style="{ height: statusBarHeight + 'px' }" />

    <view class="glass-bar nav-bar">
      <view class="nav-back flex-center" hover-class="hover-opacity" @tap="onCancel">
        <app-icon name="back-ink" size="44rpx" />
      </view>
      <text class="nav-title">{{ isEdit ? '编辑会员资料' : '智能录入与编辑' }}</text>
      <view style="width: 64rpx" />
    </view>

    <!-- AI 解析确认条：仅在确实识别出字段后才出现（未解析时不误导用户） -->
    <view v-if="aiFields.length" class="ai-bar flex-center" hover-class="hover-scale" @tap="panelOpen = true">
      <app-icon name="edit-gold" size="28rpx" />
      <text class="ai-bar-text">AI已识别 {{ aiFields.length }} 项信息，请确认</text>
      <app-icon name="chevron-right" size="26rpx" />
    </view>
    <view class="paste-entry flex-center" hover-class="hover-opacity" @tap="parseModal = true">
      <app-icon name="sparkles-gold" size="26rpx" />
      <text class="paste-text">粘贴文本 / 语音转写，AI 自动填表</text>
    </view>

    <view class="form-body">
      <!-- 分组1：基本信息 -->
      <glass-card strong padding="36rpx" shadow="normal" class="group-card">
        <view class="section-title group-title">
          <text class="section-name">基本信息</text>
        </view>

        <view class="field-row">
          <text class="field-label">姓名</text>
          <input v-model="form.name" class="field-input" placeholder="请输入姓名" placeholder-class="ph" />
        </view>

        <view class="field-row">
          <text class="field-label">年龄</text>
          <input v-model="form.age" type="number" class="field-input" placeholder="请输入年龄" placeholder-class="ph" />
        </view>

        <view class="field-row">
          <text class="field-label">性别</text>
          <view class="gender-switch">
            <view class="gender-item flex-center" :class="{ 'gender-active': form.gender === '男' }" @tap="form.gender = '男'">男</view>
            <view class="gender-item flex-center" :class="{ 'gender-active': form.gender === '女' }" @tap="form.gender = '女'">女</view>
          </view>
        </view>

        <view class="field-row">
          <text class="field-label">手机号</text>
          <input v-model="form.phone" type="text" inputmode="numeric" class="field-input" placeholder="请输入手机号" placeholder-class="ph" />
        </view>

        <view class="field-row">
          <text class="field-label">省份</text>
          <input v-model="form.province" class="field-input" placeholder="如 广东" placeholder-class="ph" />
        </view>

        <view class="field-row">
          <text class="field-label">城市</text>
          <input v-model="form.city" class="field-input" placeholder="如 深圳" placeholder-class="ph" />
        </view>

        <view class="field-row">
          <text class="field-label">区县</text>
          <input v-model="form.district" class="field-input" placeholder="如 南山区" placeholder-class="ph" />
        </view>

        <view class="field-row">
          <text class="field-label">职业</text>
          <input v-model="form.job" class="field-input" placeholder="如 高级架构师" placeholder-class="ph" />
        </view>

        <view class="field-row">
          <text class="field-label">学历</text>
          <view class="flex" style="gap: 12rpx; flex-wrap: wrap">
            <view
              v-for="e in EDUCATION_OPTS"
              :key="e"
              class="sel-pill flex-center"
              :class="{ 'sel-active': form.education === e }"
              @tap="pick('education', e)"
            >{{ e }}</view>
          </view>
        </view>

        <view class="field-row">
          <text class="field-label">身高(cm)</text>
          <input v-model="form.height" type="number" class="field-input" placeholder="如 178" placeholder-class="ph" />
        </view>

        <view class="field-row">
          <text class="field-label">月收入</text>
          <view class="flex" style="gap: 12rpx; flex-wrap: wrap">
            <view
              v-for="i in INCOME_OPTS"
              :key="i"
              class="sel-pill flex-center"
              :class="{ 'sel-active': form.income === i }"
              @tap="pick('income', i)"
            >{{ i }}</view>
          </view>
        </view>

        <view class="field-row" style="border-bottom: none">
          <text class="field-label">等级</text>
          <view class="flex" style="gap: 12rpx; flex-wrap: wrap">
            <view
              v-for="l in LEVEL_OPTS"
              :key="l"
              class="sel-pill flex-center"
              :class="{ 'sel-active': form.level === l }"
              @tap="pick('level', l)"
            >{{ l }}</view>
          </view>
        </view>
      </glass-card>

      <!-- 分组：详细资料（对齐采集页收集的结构化维度，红娘可在本页补全或修正） -->
      <glass-card strong padding="36rpx" shadow="normal" class="group-card">
        <view class="section-title group-title">
          <text class="section-name">详细资料</text>
        </view>

        <view class="field-row">
          <text class="field-label">婚姻状况</text>
          <view class="flex" style="gap: 12rpx; flex-wrap: wrap">
            <view
              v-for="o in MARRIAGE_OPTS"
              :key="o"
              class="sel-pill flex-center"
              :class="{ 'sel-active': form.marriage === o }"
              @tap="pick('marriage', o)"
            >{{ o }}</view>
          </view>
        </view>

        <view class="field-row">
          <text class="field-label">子女</text>
          <view class="flex" style="gap: 12rpx; flex-wrap: wrap">
            <view
              v-for="o in HAS_CHILD_OPTS"
              :key="o"
              class="sel-pill flex-center"
              :class="{ 'sel-active': form.hasChild === o }"
              @tap="pick('hasChild', o)"
            >{{ o }}</view>
          </view>
        </view>

        <view class="field-row">
          <text class="field-label">房产</text>
          <view class="flex" style="gap: 12rpx; flex-wrap: wrap">
            <view
              v-for="o in HOUSE_OPTS"
              :key="o"
              class="sel-pill flex-center"
              :class="{ 'sel-active': form.house === o }"
              @tap="pick('house', o)"
            >{{ o }}</view>
          </view>
        </view>

        <view class="field-row" style="border-bottom: none">
          <text class="field-label">座驾</text>
          <input v-model="form.car" class="field-input" placeholder="如 已购车（选填）" placeholder-class="ph" />
        </view>
      </glass-card>

      <!-- 分组：自我介绍（采集页 bio / 给红娘的留言） -->
      <glass-card strong padding="36rpx" shadow="normal" class="group-card">
        <view class="section-title group-title">
          <text class="section-name">自我介绍</text>
        </view>
        <textarea
          v-model="form.bio"
          class="req-textarea"
          placeholder="会员的自我介绍或给红娘的留言…"
          placeholder-class="ph"
          :maxlength="500"
        />
      </glass-card>

      <!-- 分组：本人照片（采集页上传；红娘可补传或替换） -->
      <glass-card strong padding="36rpx" shadow="normal" class="group-card">
        <view class="section-title group-title">
          <text class="section-name">本人照片</text>
        </view>
        <view class="photo-box">
          <view v-if="form.photo" class="photo-preview">
            <image :src="form.photo" class="photo-img" mode="aspectFill" />
            <view class="photo-del flex-center" hover-class="hover-opacity" @tap="onRemovePhoto">
              <app-icon name="close-ink" size="24rpx" />
            </view>
          </view>
          <view v-else class="photo-add flex-center" hover-class="hover-opacity" @tap="onPickPhoto">
            <text class="photo-plus">＋</text>
            <text class="photo-tip">上传照片，让配对更直观</text>
          </view>
        </view>
        <view v-if="form.photo" class="photo-actions">
          <view class="photo-btn flex-center" hover-class="hover-opacity" @tap="onPickPhoto">重新选择</view>
          <view class="photo-btn photo-btn-del flex-center" hover-class="hover-opacity" @tap="onRemovePhoto">移除照片</view>
        </view>
      </glass-card>

      <!-- 分组2：择偶要求 -->
      <glass-card strong padding="36rpx" shadow="normal" class="group-card">
        <view class="section-title group-title">
          <text class="section-name">择偶要求</text>
        </view>
        <textarea
          v-model="form.requirement"
          class="req-textarea"
          placeholder="如：希望对方性格温和、有稳定事业，年龄相仿，重视家庭…"
          placeholder-class="ph"
          :maxlength="500"
        />
      </glass-card>

      <!-- 分组3：自定义标签 -->
      <glass-card strong padding="36rpx" shadow="normal" class="group-card">
        <view class="section-title group-title">
          <text class="section-name">自定义标签</text>
        </view>
        <view class="flex flex-wrap tag-cloud">
          <view v-for="t in customTags" :key="t" class="custom-tag flex items-center">
            <text class="custom-tag-text">{{ t }}</text>
            <view class="tag-close flex-center" hover-class="hover-opacity" @tap="removeTag(t)">
              <app-icon name="close-ink" size="20rpx" />
            </view>
          </view>
          <input
            v-model="tagInput"
            class="tag-input"
            placeholder="输入后回车生成标签"
            placeholder-class="ph"
            confirm-type="done"
            @confirm="addTag"
          />
        </view>
      </glass-card>

      <view style="height: 220rpx" />
    </view>

    <!-- 底部操作栏 -->
    <view class="bottom-bar">
      <view class="btn-cancel flex-center" hover-class="hover-opacity" @tap="onCancel">取消</view>
      <view class="btn-save flex-center" :class="{ 'is-saving': saving }" hover-class="hover-scale" @tap="onSave">
        <text v-if="!saving">{{ isEdit ? '保存修改' : '确认录入' }}</text>
        <text v-else>保存中…</text>
      </view>
    </view>

    <!-- AI 解析侧滑面板 -->
    <view v-if="panelOpen" class="panel-mask" @tap="panelOpen = false" />
    <view class="side-panel" :class="{ 'panel-open': panelOpen }">
      <view class="panel-head">
        <text class="panel-title">AI 识别结果</text>
        <text class="panel-sub">请确认以下 {{ aiFields.length }} 项信息</text>
      </view>
      <view class="panel-list">
        <view v-for="f in aiFields" :key="f.key" class="panel-item">
          <text class="panel-label">{{ f.label }}</text>
          <text class="panel-value">{{ f.value }}</text>
          <text class="panel-adopt" :class="f.adopted ? '' : 'panel-skip'">{{ f.adopted ? '已采用' : '未采用' }}</text>
        </view>
      </view>
      <view class="panel-confirm flex-center" hover-class="hover-scale" @tap="panelOpen = false">
        确认无误
      </view>
    </view>

    <!-- 粘贴文本解析弹窗 -->
    <view v-if="parseModal" class="panel-mask" @tap="parseModal = false" />
    <view class="parse-modal" :class="{ 'panel-open': parseModal }">
      <text class="parse-title">AI 智能建档</text>
      <text class="parse-sub">粘贴会员介绍文本，AI 将自动抽取姓名、年龄、职业等信息</text>
      <textarea
        v-model="parseText"
        class="parse-textarea"
        placeholder="例如：张伟，32岁，男，上海工程师，硕士学历，身高178cm，喜欢摄影和徒步，手机号13812341234"
        placeholder-class="ph"
        :maxlength="500"
      />
      <view class="parse-btn flex-center" :class="{ 'is-saving': parsing }" hover-class="hover-scale" @tap="doParse">
        {{ parsing ? 'AI 解析中…' : '开始解析' }}
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

.nav-bar {
  position: relative;
  z-index: 2;
  width: 100%;
  padding: 16rpx 24rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-sizing: border-box;
}
.nav-back {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
}
.nav-title {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--q-ink-1);
}

.ai-bar {
  position: relative;
  z-index: 2;
  margin: 24rpx 40rpx 8rpx;
  padding: 20rpx 32rpx;
  border-radius: 999rpx;
  gap: 12rpx;
  background: var(--q-gold-soft);
  border: 2rpx solid rgba(212, 175, 55, 0.3);
  backdrop-filter: var(--q-glass-blur);
  -webkit-backdrop-filter: var(--q-glass-blur);
}
.ai-bar-text {
  font-size: 26rpx;
  font-weight: 600;
  color: var(--q-gold-dark);
}

.paste-entry {
  position: relative;
  z-index: 2;
  margin: 16rpx 40rpx 0;
  gap: 10rpx;
  padding: 16rpx;
}
.paste-text {
  font-size: 24rpx;
  color: var(--q-gold-dark);
  font-weight: 500;
}

.form-body {
  position: relative;
  z-index: 2;
  padding: 24rpx 40rpx 0;
}
.group-card {
  margin-bottom: 32rpx;
}
.group-title {
  margin-bottom: 16rpx;
}
.section-name {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--q-ink-1);
}

.field-row {
  display: flex;
  align-items: center;
  padding: 28rpx 0;
  border-bottom: 1px solid rgba(139, 37, 74, 0.06);
}
.field-label {
  width: 150rpx;
  flex-shrink: 0;
  font-size: 28rpx;
  color: var(--q-ink-2);
  font-weight: 500;
}
.field-input {
  flex: 1;
  font-size: 30rpx;
  color: var(--q-ink-1);
  background: var(--q-glass-bg-light);
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 24rpx;
  padding: 16rpx 24rpx;
}
.ph {
  color: var(--q-ink-4);
  font-size: 28rpx;
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

.gender-switch {
  display: flex;
  background: rgba(139, 37, 74, 0.06);
  border-radius: 24rpx;
  padding: 6rpx;
  gap: 6rpx;
}
.gender-item {
  width: 120rpx;
  height: 64rpx;
  border-radius: 20rpx;
  font-size: 28rpx;
  color: var(--q-ink-3);
  font-weight: 500;
  transition: all 0.2s ease;
}
.gender-active {
  background: var(--q-primary);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 6rpx 16rpx rgba(139, 37, 74, 0.25);
}

.req-textarea {
  width: 100%;
  height: 180rpx;
  box-sizing: border-box;
  font-size: 28rpx;
  color: var(--q-ink-1);
  background: var(--q-glass-bg-light);
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 24rpx;
  padding: 20rpx 24rpx;
  line-height: 1.6;
}

.tag-cloud {
  gap: 16rpx;
  align-items: center;
}
.custom-tag {
  gap: 8rpx;
  padding: 12rpx 20rpx;
  border-radius: 999rpx;
  background: var(--q-primary-soft);
  border: 2rpx solid rgba(139, 37, 74, 0.15);
}
.custom-tag-text {
  font-size: 26rpx;
  color: var(--q-primary);
  font-weight: 600;
}
.tag-close {
  width: 32rpx;
  height: 32rpx;
  border-radius: 50%;
  background: rgba(139, 37, 74, 0.12);
}
.tag-input {
  flex: 1;
  min-width: 220rpx;
  font-size: 28rpx;
  color: var(--q-ink-1);
  background: var(--q-glass-bg-light);
  border: 1px dashed rgba(139, 37, 74, 0.25);
  border-radius: 24rpx;
  padding: 14rpx 24rpx;
}

.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  gap: 24rpx;
  padding: 24rpx 40rpx calc(24rpx + env(safe-area-inset-bottom));
  background: rgba(253, 251, 247, 0.82);
  backdrop-filter: var(--q-glass-blur);
  -webkit-backdrop-filter: var(--q-glass-blur);
  border-top: 1px solid rgba(139, 37, 74, 0.08);
  z-index: 90;
  box-shadow: 0 -12rpx 40rpx -16rpx rgba(120, 70, 80, 0.12);
}
.btn-cancel {
  flex: 1;
  height: 96rpx;
  border-radius: 24rpx;
  background: rgba(150, 128, 137, 0.12);
  color: var(--q-ink-3);
  font-size: 30rpx;
  font-weight: 600;
}
.btn-save {
  flex: 2;
  height: 96rpx;
  border-radius: 24rpx;
  background: var(--q-gold-gradient);
  color: #fff;
  font-size: 30rpx;
  font-weight: 700;
  box-shadow: 0 12rpx 28rpx rgba(168, 134, 30, 0.35);
  text-shadow: 0 2rpx 6rpx rgba(120, 90, 10, 0.3);
}
.btn-save.is-saving {
  opacity: 0.7;
}

.panel-mask {
  position: fixed;
  inset: 0;
  background: rgba(46, 34, 42, 0.35);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  z-index: 98;
}
.side-panel {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  width: 560rpx;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: var(--q-glass-blur);
  -webkit-backdrop-filter: var(--q-glass-blur);
  border-left: 1px solid var(--q-glass-border);
  box-shadow: -20rpx 0 60rpx rgba(120, 70, 80, 0.15);
  transform: translateX(100%);
  transition: transform 0.3s cubic-bezier(0.22, 1, 0.36, 1);
  z-index: 99;
  padding: 48rpx 36rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
}
.side-panel.panel-open {
  transform: translateX(0);
}
.panel-head {
  margin-bottom: 32rpx;
}
.panel-title {
  display: block;
  font-size: 36rpx;
  font-weight: 700;
  color: var(--q-ink-1);
  margin-bottom: 8rpx;
}
.panel-sub {
  font-size: 24rpx;
  color: var(--q-ink-3);
}
.panel-list {
  flex: 1;
}
.panel-item {
  display: flex;
  align-items: center;
  padding: 28rpx 0;
  border-bottom: 1px solid rgba(139, 37, 74, 0.06);
}
.panel-label {
  width: 120rpx;
  font-size: 28rpx;
  color: var(--q-ink-3);
}
.panel-value {
  flex: 1;
  font-size: 30rpx;
  font-weight: 600;
  color: var(--q-ink-1);
}
.panel-adopt {
  font-size: 22rpx;
  color: var(--q-success);
  background: var(--q-success-soft);
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
}
.panel-skip {
  color: var(--q-ink-4);
  background: rgba(150, 128, 137, 0.12);
}
.panel-confirm {
  height: 96rpx;
  border-radius: 24rpx;
  background: var(--q-gold-gradient);
  color: #fff;
  font-size: 30rpx;
  font-weight: 700;
  box-shadow: 0 12rpx 28rpx rgba(168, 134, 30, 0.35);
}

.parse-modal {
  position: fixed;
  left: 40rpx;
  right: 40rpx;
  bottom: 0;
  border-radius: 48rpx 48rpx 0 0;
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: var(--q-glass-blur);
  -webkit-backdrop-filter: var(--q-glass-blur);
  border: 1px solid var(--q-glass-border);
  box-shadow: 0 -20rpx 60rpx rgba(120, 70, 80, 0.15);
  transform: translateY(110%);
  transition: transform 0.3s cubic-bezier(0.22, 1, 0.36, 1);
  z-index: 99;
  padding: 40rpx 36rpx calc(40rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}
.parse-modal.panel-open {
  transform: translateY(0);
}
.parse-title {
  display: block;
  font-size: 34rpx;
  font-weight: 700;
  color: var(--q-ink-1);
}
.parse-sub {
  display: block;
  font-size: 24rpx;
  color: var(--q-ink-3);
  margin: 10rpx 0 24rpx;
}
.parse-textarea {
  width: 100%;
  height: 220rpx;
  box-sizing: border-box;
  font-size: 28rpx;
  color: var(--q-ink-1);
  background: var(--q-glass-bg-light);
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 24rpx;
  padding: 20rpx 24rpx;
}
.parse-btn {
  margin-top: 24rpx;
  height: 96rpx;
  border-radius: 24rpx;
  background: var(--q-gold-gradient);
  color: #fff;
  font-size: 30rpx;
  font-weight: 700;
  box-shadow: 0 12rpx 28rpx rgba(168, 134, 30, 0.35);
}
.parse-btn.is-saving {
  opacity: 0.7;
}

/* ---------- 本人照片区（对齐采集页上传，红娘可补传/替换/移除） ---------- */
.photo-box {
  width: 100%;
  display: flex;
  justify-content: center;
}
.photo-preview {
  position: relative;
  width: 240rpx;
  height: 240rpx;
  border-radius: 32rpx;
  overflow: hidden;
  background: var(--q-glass-bg-light);
  box-shadow: 0 8rpx 24rpx rgba(120, 70, 80, 0.12);
}
.photo-img {
  width: 100%;
  height: 100%;
}
.photo-del {
  position: absolute;
  top: 12rpx;
  right: 12rpx;
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  background: rgba(46, 34, 42, 0.55);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  z-index: 2;
}
.photo-add {
  width: 240rpx;
  height: 240rpx;
  border-radius: 32rpx;
  flex-direction: column;
  gap: 12rpx;
  background: var(--q-glass-bg-light);
  border: 2rpx dashed rgba(139, 37, 74, 0.3);
}
.photo-plus {
  font-size: 64rpx;
  line-height: 1;
  color: var(--q-primary);
  font-weight: 300;
}
.photo-tip {
  font-size: 22rpx;
  color: var(--q-ink-3);
}
.photo-actions {
  display: flex;
  gap: 20rpx;
  margin-top: 20rpx;
  justify-content: center;
}
.photo-btn {
  padding: 16rpx 36rpx;
  border-radius: 999rpx;
  background: var(--q-gold-soft);
  border: 1px solid rgba(212, 175, 55, 0.3);
  font-size: 26rpx;
  color: var(--q-gold-dark);
  font-weight: 600;
}
.photo-btn-del {
  background: rgba(150, 128, 137, 0.12);
  border-color: rgba(150, 128, 137, 0.2);
  color: var(--q-ink-3);
}

.hover-scale {
  transform: scale(1.03);
  transition: transform 0.15s ease;
}
.hover-opacity {
  opacity: 0.6;
}
</style>
