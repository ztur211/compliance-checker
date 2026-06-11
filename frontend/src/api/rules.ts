import { api } from './projects'

export interface RuleDraft {
  id: string
  citation: string
  parameter: string
  comparator: string
  threshold: number
  status: string
  sourceQuote: string | null
  confidence: number | null
}

export const listDrafts = () => api.jsonFetch<RuleDraft[]>('/api/admin/rules')
export const approve = (id: string) => api.jsonFetch<void>(`/api/admin/rules/${id}/approve`, { method: 'POST' })
export const reject = (id: string) => api.jsonFetch<void>(`/api/admin/rules/${id}/reject`, { method: 'POST' })
