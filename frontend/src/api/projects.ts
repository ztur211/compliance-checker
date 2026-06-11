import type { Project } from './types'

async function jsonFetch<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  })
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}: ${await res.text()}`)
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T // void endpoints (e.g. approve/reject) return an empty body
}

export const api = { jsonFetch }

export const listProjects = () => jsonFetch<Project[]>('/api/projects')
export const createProject = (name: string) =>
  jsonFetch<Project>('/api/projects', { method: 'POST', body: JSON.stringify({ name }) })
