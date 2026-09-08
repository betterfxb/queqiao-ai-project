/** 时间友好化：ISO 时间串 → “x分钟前 / x小时前 / x天前” */
export function timeAgo(iso?: string): string {
  if (!iso) return ''
  const t = new Date(iso.replace(' ', 'T'))
  if (isNaN(t.getTime())) return iso
  const diff = Date.now() - t.getTime()
  const min = Math.floor(diff / 60000)
  if (min < 1) return '刚刚'
  if (min < 60) return `${min}分钟前`
  const h = Math.floor(min / 60)
  if (h < 24) return `${h}小时前`
  const d = Math.floor(h / 24)
  if (d < 30) return `${d}天前`
  return `${Math.floor(d / 30)}个月前`
}

/** 当前时间 → yyyy-MM-dd HH:mm */
export function nowStamp(): string {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}
