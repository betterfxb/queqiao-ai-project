<script setup lang="ts">
/**
 * 会员信息卡片 — 一键生成可分享的会员名片
 *  - 屏幕内：品牌化 DOM 卡片（左侧基本信息 / 右侧会员照片，对齐勃艮第+香槟金设计令牌）
 *  - 导出：零依赖 Canvas 绘制同款排版，生成高清 PNG（含照片、品牌头、水印）
 *  - 附加：一键复制资料文本
 * 照片与三级地址来自详情接口 fetchMember(id)（列表投影不含原图）。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { Member } from '@/api'
import { fetchMember } from '@/api'

const props = defineProps<{ memberId: string }>()
const visible = defineModel<boolean>({ required: true })

const loading = ref(false)
const full = ref<Member | null>(null)

/** 打开对话框即按 id 拉取完整档案（含照片 / 三级地址） */
watch(
  visible,
  async (open) => {
    if (!open) return
    loading.value = true
    try {
      const r: any = await fetchMember(props.memberId)
      full.value = r?.member ?? null
    } catch {
      full.value = null
      ElMessage.error('会员档案加载失败')
    } finally {
      loading.value = false
    }
  },
  { immediate: true },
)

const fmtDate = (t?: string | null) =>
  t ? new Date(t).toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }) : '—'

/** StatusPill 接受的语义色 */
type PillType = 'success' | 'warning' | 'error' | 'info' | 'active' | 'gold' | 'neutral'
const pillType = (s: Member['status']): PillType =>
  s === '已认证' ? 'success' : s === '待审核' ? 'warning' : s === '认证中' ? 'info' : s === '已暂停' ? 'neutral' : 'active'

const tags = computed(() => (full.value?.tags ? full.value.tags.split(',').map((s) => s.trim()).filter(Boolean) : []))

/** 左侧基本信息行（按需过滤空值） */
const infoRows = computed<{ label: string; value: string }[]>(() => {
  const m = full.value
  if (!m) return []
  const rows: { label: string; value: string }[] = []
  rows.push({ label: '性别 / 年龄', value: `${m.gender} · ${m.age}岁` })
  const loc = [m.province, m.city, m.district].filter(Boolean).join(' ')
  if (loc) rows.push({ label: '现居地', value: loc })
  if (m.hometown) rows.push({ label: '籍贯', value: m.hometown })
  if (m.phone) rows.push({ label: '手机号', value: m.phone })
  rows.push({ label: '学历 / 职业', value: [m.education, m.job].filter(Boolean).join(' · ') || '—' })
  if (m.height) rows.push({ label: '身高', value: `${m.height} cm` })
  if (m.income) rows.push({ label: '月收入', value: m.income })
  if (m.marriage) rows.push({ label: '婚姻状况', value: m.marriage })
  if (m.hasChild) rows.push({ label: '子女', value: m.hasChild })
  if (m.house) rows.push({ label: '房产', value: m.house })
  if (m.car) rows.push({ label: '购车', value: m.car })
  if (m.agent) rows.push({ label: '负责红娘', value: m.agent })
  rows.push({ label: '牵线 / 成功率', value: `${m.matchCount ?? 0} 次 / ${m.successRate ?? 0}%` })
  if (m.createdAt) rows.push({ label: '注册时间', value: fmtDate(m.createdAt) })
  return rows
})

/* ============================ 复制资料 ============================ */
async function copyProfile() {
  const m = full.value
  if (!m) return
  const lines = [
    `【鹊桥·红娘工作台 会员名片】`,
    `姓名：${m.name}（${m.gender} · ${m.age}岁 · ${m.level} · ${m.status}）`,
    ...infoRows.value.map((r) => `${r.label}：${r.value}`),
  ]
  if (m.bio) lines.push(`自我介绍：${m.bio}`)
  if (m.requirement) lines.push(`择偶要求：${m.requirement}`)
  if (tags.value.length) lines.push(`契合标签：${tags.value.join('、')}`)
  lines.push(`会员ID：${m.id}`)
  try {
    await navigator.clipboard.writeText(lines.join('\n'))
    ElMessage.success('会员资料已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败，请检查浏览器剪贴板权限')
  }
}

/* ============================ 导出 PNG（零依赖 Canvas） ============================ */
function roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
  if (typeof ctx.roundRect === 'function') {
    ctx.beginPath()
    ctx.roundRect(x, y, w, h, r)
    return
  }
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + w, y, x + w, y + h, r)
  ctx.arcTo(x + w, y + h, x, y + h, r)
  ctx.arcTo(x, y + h, x, y, r)
  ctx.arcTo(x, y, x + w, y, r)
  ctx.closePath()
}

function loadImage(src: string): Promise<HTMLImageElement | null> {
  return new Promise((resolve) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = () => resolve(null)
    img.src = src
  })
}

function wrapText(ctx: CanvasRenderingContext2D, text: string, maxWidth: number): string[] {
  const lines: string[] = []
  let line = ''
  for (const ch of text) {
    if (ctx.measureText(line + ch).width > maxWidth && line) {
      lines.push(line)
      line = ch
    } else {
      line += ch
    }
  }
  if (line) lines.push(line)
  return lines
}

async function exportImage() {
  const m = full.value
  if (!m) return
  const W = 960
  const H = 760
  const dpr = Math.min(window.devicePixelRatio || 1, 2) * 1.5 // 高清
  const cv = document.createElement('canvas')
  cv.width = W * dpr
  cv.height = H * dpr
  const ctx = cv.getContext('2d')!
  ctx.scale(dpr, dpr)

  const PRIMARY = '#7d2b3d'
  const PRIMARY_LIGHT = '#924560'
  const GOLD = '#a8863f'
  const GOLD_LIGHT = '#ddcaa0'
  const GOLD_DARK = '#856a2e'
  const INK1 = '#1c1917'
  const INK2 = '#44403c'
  const INK3 = '#78716c'
  const INK4 = '#a8a29e'
  const BG = '#f6f5f3'
  const BG_WARM = '#f1efec'

  // 卡片底（实白纸面 + 发丝描边 + 圆角）
  roundRect(ctx, 0, 0, W, H, 12)
  ctx.fillStyle = '#ffffff'
  ctx.fill()
  ctx.lineWidth = 1
  ctx.strokeStyle = 'rgba(28,25,23,0.14)'
  ctx.stroke()

  // 顶部品牌头（主色实底）
  ctx.save()
  roundRect(ctx, 0, 0, W, 116, 12)
  ctx.clip()
  ctx.fillStyle = PRIMARY
  ctx.fillRect(0, 0, W, 116)
  ctx.restore()
  // 头部黄铜细线
  ctx.fillStyle = GOLD
  ctx.fillRect(0, 113, W, 3)

  // 品牌文字
  ctx.textBaseline = 'alphabetic'
  ctx.fillStyle = GOLD_LIGHT
  ctx.font = '600 23px "PingFang SC", "Microsoft YaHei", sans-serif'
  ctx.fillText('鹊桥 · 红娘工作台', 44, 50)
  ctx.fillStyle = 'rgba(255,255,255,0.85)'
  ctx.font = '400 15px "PingFang SC", "Microsoft YaHei", sans-serif'
  ctx.fillText('会员信息卡 · Member Profile', 45, 80)
  // 右上方姓名
  ctx.textAlign = 'right'
  ctx.fillStyle = '#ffffff'
  ctx.font = '600 30px "PingFang SC", "Microsoft YaHei", sans-serif'
  ctx.fillText(m.name, W - 44, 56)
  // 等级/状态徽标
  ctx.font = '600 15px "PingFang SC", "Microsoft YaHei", sans-serif'
  ctx.fillStyle = GOLD_LIGHT
  ctx.fillText(`${m.level} · ${m.status}`, W - 44, 86)
  ctx.textAlign = 'left'

  // 照片框（中性底 + 发丝描边）
  const px = 620
  const py = 140
  const pw = 296
  const ph = 372
  roundRect(ctx, px - 6, py - 6, pw + 12, ph + 12, 10)
  ctx.fillStyle = 'rgba(28,25,23,0.04)'
  ctx.fill()
  roundRect(ctx, px, py, pw, ph, 8)
  ctx.save()
  ctx.clip()
  ctx.fillStyle = BG_WARM
  ctx.fillRect(px, py, pw, ph)
  const photo = m.photo ? await loadImage(m.photo) : null
  if (photo) {
    // cover 裁剪绘制
    const ratio = Math.max(pw / photo.width, ph / photo.height)
    const dw = photo.width * ratio
    const dh = photo.height * ratio
    ctx.drawImage(photo, px + (pw - dw) / 2, py + (ph - dh) / 2, dw, dh)
  } else {
    ctx.fillStyle = PRIMARY
    ctx.font = '600 120px "PingFang SC", "Microsoft YaHei", sans-serif'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(m.name.slice(0, 1), px + pw / 2, py + ph / 2)
    ctx.textAlign = 'left'
    ctx.textBaseline = 'alphabetic'
  }
  ctx.restore()
  roundRect(ctx, px, py, pw, ph, 8)
  ctx.lineWidth = 1
  ctx.strokeStyle = 'rgba(28,25,23,0.14)'
  ctx.stroke()
  // 照片角标
  ctx.fillStyle = GOLD_DARK
  ctx.font = '600 13px "PingFang SC", "Microsoft YaHei", sans-serif'
  ctx.fillText('会员照片', px + 12, py + 24)

  // 左侧基本信息
  let y = 150
  const labelX = 44
  const valueX = 168
  const maxRows = Math.min(infoRows.value.length, 11)
  ctx.font = '500 15px "PingFang SC", "Microsoft YaHei", sans-serif'
  for (let i = 0; i < maxRows; i++) {
    const r = infoRows.value[i]
    // 行底分隔
    if (i > 0) {
      ctx.strokeStyle = 'rgba(28,25,23,0.08)'
      ctx.lineWidth = 1
      ctx.beginPath()
      ctx.moveTo(labelX, y - 19)
      ctx.lineTo(560, y - 19)
      ctx.stroke()
    }
    ctx.fillStyle = INK3
    ctx.font = '500 14px "PingFang SC", "Microsoft YaHei", sans-serif'
    ctx.fillText(r.label, labelX, y)
    ctx.fillStyle = INK1
    ctx.font = '600 16px "PingFang SC", "Microsoft YaHei", sans-serif'
    const val = String(r.value)
    const vw = ctx.measureText(val).width
    ctx.fillText(val.length > 26 ? val.slice(0, 25) + '…' : val, valueX, y)
    y += 42
  }

  // 底部资料区（自我介绍 / 择偶要求）
  const by = Math.max(y + 8, 540)
  roundRect(ctx, 44, by, W - 88, H - by - 48, 8)
  ctx.fillStyle = 'rgba(28,25,23,0.03)'
  ctx.fill()
  let ty = by + 30
  ctx.fillStyle = PRIMARY
  ctx.font = '600 15px "PingFang SC", "Microsoft YaHei", sans-serif'
  if (m.bio) {
    ctx.fillText('自我介绍', 64, ty)
    ty += 22
    ctx.fillStyle = INK2
    ctx.font = '400 14px "PingFang SC", "Microsoft YaHei", sans-serif'
    for (const ln of wrapText(ctx, m.bio, W - 150)) {
      ctx.fillText(ln, 64, ty)
      ty += 20
    }
  }
  if (m.requirement) {
    ty += 6
    ctx.fillStyle = PRIMARY
    ctx.font = '600 15px "PingFang SC", "Microsoft YaHei", sans-serif'
    ctx.fillText('择偶要求', 64, ty)
    ty += 22
    ctx.fillStyle = INK2
    ctx.font = '400 14px "PingFang SC", "Microsoft YaHei", sans-serif'
    for (const ln of wrapText(ctx, m.requirement, W - 150)) {
      ctx.fillText(ln, 64, ty)
      ty += 20
    }
  }
  if (tags.value.length) {
    ty += 6
    ctx.fillStyle = PRIMARY
    ctx.font = '600 15px "PingFang SC", "Microsoft YaHei", sans-serif'
    ctx.fillText('契合标签', 64, ty)
    ty += 22
    ctx.fillStyle = GOLD_DARK
    ctx.font = '600 14px "PingFang SC", "Microsoft YaHei", sans-serif'
    ctx.fillText('# ' + tags.value.join('   # '), 64, ty)
  }

  // 页脚水印
  ctx.fillStyle = INK4
  ctx.font = '400 13px "PingFang SC", "Microsoft YaHei", sans-serif'
  ctx.fillText(`会员ID：${m.id}`, 44, H - 22)
  ctx.textAlign = 'right'
  ctx.fillText(`生成于 ${new Date().toLocaleDateString('zh-CN')}`, W - 44, H - 22)
  ctx.textAlign = 'left'

  cv.toBlob((blob) => {
    if (!blob) {
      ElMessage.error('图片生成失败')
      return
    }
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `会员名片_${m.name}_${m.id}.png`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    setTimeout(() => URL.revokeObjectURL(url), 2000)
    ElMessage.success('会员名片已导出 PNG')
  }, 'image/png')
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="null"
    width="760px"
    align-center
    class="mcard-dialog"
    :close-on-click-modal="false"
  >
    <div v-loading="loading" class="mcard-wrap">
      <div v-if="full" class="mcard" id="member-card">
        <!-- 品牌头 -->
        <header class="mcard-head">
          <div class="mcard-brand">
            <span class="mcard-brand-main">鹊桥 · 红娘工作台</span>
            <span class="mcard-brand-sub">会员信息卡 · Member Profile</span>
          </div>
          <div class="mcard-head-right">
            <div class="mcard-name">{{ full.name }}</div>
            <div class="mcard-meta">
              <LevelBadge :level="full.level" />
              <StatusPill :type="pillType(full.status)" :text="full.status" />
            </div>
          </div>
        </header>

        <div class="mcard-body">
          <!-- 左：基本信息 -->
          <section class="mcard-info">
            <div class="mcard-row" v-for="(r, i) in infoRows" :key="i">
              <span class="mcard-label">{{ r.label }}</span>
              <span class="mcard-value">{{ r.value }}</span>
            </div>
          </section>

          <!-- 右：会员照片 -->
          <aside class="mcard-photo">
            <div class="mcard-photo-inner">
              <img v-if="full.photo" :src="full.photo" alt="会员照片" class="mcard-photo-img" />
              <BaseAvatar
                v-else
                :name="full.name"
                :bg="full.avatarBg"
                :size="120"
                class="mcard-photo-fallback"
              />
              <span class="mcard-photo-tag">会员照片</span>
            </div>
          </aside>
        </div>

        <!-- 底部资料 -->
        <footer class="mcard-foot" v-if="full.bio || full.requirement || tags.length">
          <div class="mcard-foot-block" v-if="full.bio">
            <div class="mcard-foot-title">自我介绍</div>
            <div class="mcard-foot-text">{{ full.bio }}</div>
          </div>
          <div class="mcard-foot-block" v-if="full.requirement">
            <div class="mcard-foot-title">择偶要求</div>
            <div class="mcard-foot-text">{{ full.requirement }}</div>
          </div>
          <div class="mcard-foot-block" v-if="tags.length">
            <div class="mcard-foot-title">契合标签</div>
            <div class="mcard-tags">
              <span class="mcard-tag" v-for="t in tags" :key="t"># {{ t }}</span>
            </div>
          </div>
        </footer>
      </div>
    </div>

    <template #footer>
      <div class="mcard-actions">
        <span class="mcard-id">会员ID：{{ full?.id }}</span>
        <div class="mcard-actions-right">
          <button class="btn btn-outline" @click="copyProfile"><app-icon name="copy" :size="15" />复制资料</button>
          <button class="btn btn-primary" @click="exportImage"><app-icon name="download" :size="15" />下载图片</button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.mcard-wrap { min-height: 360px; }
.mcard {
  width: 100%;
  border-radius: var(--dt-radius-lg);
  background: var(--dt-card-solid);
  border: 1px solid var(--dt-border);
  overflow: hidden;
  box-shadow: var(--dt-shadow-lg);
}
/* 品牌头 */
.mcard-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 20px 24px;
  background: var(--dt-primary);
  color: #fff;
}
.mcard-brand { display: flex; flex-direction: column; gap: 4px; }
.mcard-brand-main { font-size: 1.25rem; font-weight: 600; color: var(--dt-gold-lighter); }
.mcard-brand-sub { font-size: 0.75rem; color: rgba(255,255,255,0.82); }
.mcard-head-right { text-align: right; }
.mcard-name { font-size: 1.75rem; font-weight: 600; line-height: 1.1; }
.mcard-meta { display: flex; gap: 8px; justify-content: flex-end; margin-top: 8px; }

/* 主体 */
.mcard-body { display: flex; gap: 24px; padding: 20px 24px 8px; align-items: stretch; }
.mcard-info { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.mcard-row {
  display: flex; gap: 12px; align-items: baseline;
  padding: 8px 0; border-bottom: 1px solid var(--dt-border);
  font-size: 0.875rem;
}
.mcard-row:last-child { border-bottom: none; }
.mcard-label { flex: 0 0 96px; color: var(--dt-ink-3); }
.mcard-value { flex: 1; color: var(--dt-ink-1); font-weight: 600; word-break: break-all; }

/* 照片 */
.mcard-photo { flex: 0 0 240px; }
.mcard-photo-inner {
  position: relative; width: 240px; height: 300px;
  border-radius: var(--dt-radius-lg);
  overflow: hidden;
  background: var(--dt-bg-warm);
  border: 1px solid var(--dt-border);
  display: flex; align-items: center; justify-content: center;
}
.mcard-photo-img { width: 100%; height: 100%; object-fit: cover; }
.mcard-photo-fallback { opacity: 0.9; background: #8d847c !important; }
.mcard-photo-tag {
  position: absolute; left: 10px; top: 10px;
  font-size: 0.75rem; font-weight: 500; color: var(--dt-ink-2);
  background: var(--dt-card-solid); padding: 2px 8px; border-radius: var(--dt-radius-sm);
  border: 1px solid var(--dt-border);
}

/* 底部资料 */
.mcard-foot {
  margin: 8px 24px 20px; padding: 16px;
  background: var(--dt-primary-soft); border-radius: var(--dt-radius-lg);
  display: flex; flex-direction: column; gap: 12px;
}
.mcard-foot-title { font-size: 0.8125rem; font-weight: 600; color: var(--dt-primary); margin-bottom: 4px; }
.mcard-foot-text { font-size: 0.8125rem; color: var(--dt-ink-2); line-height: 1.6; }
.mcard-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.mcard-tag {
  font-size: 0.75rem; font-weight: 500; color: var(--dt-ink-2);
  padding: 3px 10px; border-radius: var(--dt-radius-sm);
  background: var(--dt-muted); border: 1px solid var(--dt-border);
}

/* 操作栏 */
.mcard-actions { display: flex; align-items: center; justify-content: space-between; width: 100%; }
.mcard-id { font-size: 0.8125rem; color: var(--dt-ink-4); }
.mcard-actions-right { display: flex; gap: 10px; }
</style>
