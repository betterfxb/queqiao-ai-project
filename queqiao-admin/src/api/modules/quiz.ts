import { api } from '../http'

/** 测评维度（两极标签） */
export interface QuizDimension {
  key: string
  name: string
  positive: string
  negative: string
}

/** 测评选项（携带对维度的正负贡献） */
export interface QuizOption {
  key: string
  label: string
  dimension: string
  score: number
}

/** 测评题目 */
export interface QuizQuestion {
  id: string
  text: string
  options: QuizOption[]
}

/** 测评模板 */
export interface QuizTemplate {
  id: string
  title: string
  description?: string
  dimensions: QuizDimension[]
  questions: QuizQuestion[]
  questionCount: number
  createdAt?: string
}

/** 单维度得分视图 */
export interface QuizScoreItem {
  key: string
  name: string
  score: number
  tag: string
}

/** 测评提交结果 */
export interface QuizResult {
  quizId: string
  templateId: string
  templateTitle: string
  scores: QuizScoreItem[]
  tags: string[]
  summary: string
  memberTags: string[]
}

/** 会员历史测评（可能未完成） */
export interface MemberQuizHistory {
  done: boolean
  quizId?: string
  templateId?: string
  templateTitle?: string
  tags?: string[]
  summary?: string
  scores?: QuizScoreItem[]
  createdAt?: string
}

/** 测评模板列表 */
export const fetchQuizTemplates = () =>
  api<QuizTemplate[]>('/quiz/templates')

/** 单个测评模板 */
export const fetchQuizTemplate = (id: string) =>
  api<QuizTemplate>('/quiz/templates/' + id)

/** 提交会员测评（管理员 / 红娘代会员作答） */
export const submitMemberQuiz = (
  memberId: string,
  payload: { templateId: string; answers: Record<string, string> },
) =>
  api<QuizResult>('/members/' + memberId + '/quiz', { method: 'POST', body: payload })

/** 会员最新测评结果 */
export const fetchMemberQuiz = (memberId: string) =>
  api<MemberQuizHistory>('/members/' + memberId + '/quiz')
