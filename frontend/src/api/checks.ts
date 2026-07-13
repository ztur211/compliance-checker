import { api } from './projects'

export interface Violation {
  ruleId: string
  citation: string
  severity: string
  message: string
  spaceId: string | null
  pathNodeIds: string[]
}

export interface CheckResult {
  violations: Violation[]
  passed: unknown[]
  notEvaluated: unknown[]
  blocked: boolean
  blockMessage: string | null
}

export interface CheckRun {
  id: string
  floorPlanId: string
  status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'
  result: CheckResult | null
  error: string | null
}

export const startCheck = (floorPlanId: string) =>
  api.jsonFetch<{ runId: string }>(`/api/floorplans/${floorPlanId}/checks`, { method: 'POST' })

export const getCheck = (runId: string) => api.jsonFetch<CheckRun>(`/api/checks/${runId}`)

export async function pollCheck(runId: string, intervalMs = 600, timeoutMs = 30000): Promise<CheckRun> {
  const start = Date.now()
  while (true) {
    const run = await getCheck(runId)
    if (run.status === 'SUCCEEDED' || run.status === 'FAILED') return run
    if (Date.now() - start > timeoutMs) throw new Error('check timed out')
    await new Promise((r) => setTimeout(r, intervalMs))
  }
}
