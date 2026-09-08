/** 会员档案编辑页内部数据模型 */

export interface MemberForm {
  name: string
  gender: string
  age: number
  birth: string
  height: number
  education: string
  job: string
  income: string
  city: string
  hometown: string
  phone: string
  marriage: string
  hasChild: string
  house: string
  car: string
  /** 择偶：年龄范围 */
  ageRange: [number, number]
  /** 择偶：身高范围 */
  heightRange: [number, number]
  eduReq: string
  cityReq: string
  otherReq: string
  /** 服务信息 */
  level: string
  agent: string
  status: string
  expire: string
  bio: string
}

/** 档案变更时间轴条目 */
export interface TimelineEntry {
  text: string
  detail: string
  /** 操作来源：agent 红娘 / system 系统 / member 会员自助 */
  who: string
  time: string
  from: string
  to: string
}

/** 空表单（新增会员时使用） */
export const emptyForm = (): MemberForm => ({
  name: '', gender: '男', age: 28, birth: '', height: 170,
  education: '本科', job: '', income: '30-50万', city: '', hometown: '', phone: '',
  marriage: '未婚', hasChild: '无', house: '暂无', car: '暂无',
  ageRange: [24, 35], heightRange: [160, 180],
  eduReq: '本科及以上', cityReq: '', otherReq: '',
  level: '普通', agent: '', status: '待审核', expire: '', bio: '',
})

/** 择偶要求在后端以 requirement 文本存储，此处做结构化 ⇄ 文本互转 */
export const packRequirement = (f: MemberForm) =>
  [
    `年龄 ${f.ageRange[0]}-${f.ageRange[1]}岁`,
    `身高 ${f.heightRange[0]}-${f.heightRange[1]}cm`,
    `学历 ${f.eduReq}`,
    f.cityReq ? `城市 ${f.cityReq}` : '',
    f.otherReq ? `其他：${f.otherReq}` : '',
  ]
    .filter(Boolean)
    .join('；')

export const unpackRequirement = (text: string | undefined, f: MemberForm) => {
  if (!text) return
  const age = text.match(/年龄\s*(\d+)\s*-\s*(\d+)/)
  if (age) f.ageRange = [Number(age[1]), Number(age[2])]
  const h = text.match(/身高\s*(\d+)\s*-\s*(\d+)/)
  if (h) f.heightRange = [Number(h[1]), Number(h[2])]
  const edu = text.match(/学历\s*([^；;]+)/)
  if (edu) f.eduReq = edu[1].trim()
  const city = text.match(/城市\s*([^；;]+)/)
  if (city) f.cityReq = city[1].trim()
  const other = text.match(/其他[：:]\s*([^；;]+)/)
  f.otherReq = other ? other[1].trim() : (age || h || edu || city ? '' : text)
}
