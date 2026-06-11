import { useCallback, useState } from 'react'
import type { GeometryDoc, Point } from '../api/types'
import { addDoor, addSpace, emptyDoc, toggleExit } from './geometry'

export interface FloorPlanState {
  doc: GeometryDoc
  setDoc: (doc: GeometryDoc) => void
  commitSpace: (polygon: Point[], occupancyType?: string) => void
  commitDoor: (from: string, to: string | null, position: Point[], exit?: boolean) => void
  toggle: (doorId: string) => void
}

export function useFloorPlan(initial: GeometryDoc = emptyDoc()): FloorPlanState {
  const [doc, setDoc] = useState<GeometryDoc>(initial)

  const commitSpace = useCallback(
    (polygon: Point[], occupancyType = 'WB') => setDoc((d) => addSpace(d, polygon, occupancyType)),
    [],
  )
  const commitDoor = useCallback(
    (from: string, to: string | null, position: Point[], exit = false) =>
      setDoc((d) => addDoor(d, from, to, position, 1200, exit)),
    [],
  )
  const toggle = useCallback((doorId: string) => setDoc((d) => toggleExit(d, doorId)), [])

  return { doc, setDoc, commitSpace, commitDoor, toggle }
}
