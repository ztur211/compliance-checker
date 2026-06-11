import { api } from './projects'
import type { FloorPlan, SaveFloorPlanRequest } from './types'

export const listFloorPlans = (projectId: string) =>
  api.jsonFetch<FloorPlan[]>(`/api/projects/${projectId}/floorplans`)

export const createFloorPlan = (projectId: string, name: string) =>
  api.jsonFetch<FloorPlan>(`/api/projects/${projectId}/floorplans`, {
    method: 'POST',
    body: JSON.stringify({ name }),
  })

export const getFloorPlan = (id: string) => api.jsonFetch<FloorPlan>(`/api/floorplans/${id}`)

export const saveFloorPlan = (id: string, req: SaveFloorPlanRequest) =>
  api.jsonFetch<FloorPlan>(`/api/floorplans/${id}`, { method: 'PUT', body: JSON.stringify(req) })
