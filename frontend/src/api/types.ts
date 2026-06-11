export interface Point { x: number; y: number }

export interface Space {
  id: string
  name: string
  occupancyType: string
  polygon: Point[]
}

export interface Door {
  id: string
  fromSpaceId: string
  toSpaceId: string | null
  position: Point[]            // exactly 2 points
  clearWidthMillimetres: number
  exit: boolean
}

export interface GeometryDoc {
  schemaVersion: number
  spaces: Space[]
  doors: Door[]
}

export interface Project { id: string; name: string }

export interface FloorPlan {
  id: string
  projectId: string
  name: string
  riskGroup: string | null
  sprinklered: boolean | null
  escapeHeightMetres: number | null
  geometry: GeometryDoc
}

export interface SaveFloorPlanRequest {
  name: string
  riskGroup: string | null
  sprinklered: boolean | null
  escapeHeightMetres: number | null
  geometry: GeometryDoc
}
