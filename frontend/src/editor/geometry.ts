import type { GeometryDoc, Point, Space, Door } from '../api/types'

let counter = 0
export function nextId(prefix: string): string {
  counter += 1
  return `${prefix}${counter}`
}

export const emptyDoc = (): GeometryDoc => ({ schemaVersion: 1, spaces: [], doors: [] })

export function addSpace(doc: GeometryDoc, polygon: Point[], occupancyType = 'WB'): GeometryDoc {
  const id = nextId('s')
  const space: Space = { id, name: id, occupancyType, polygon }
  return { ...doc, spaces: [...doc.spaces, space] }
}

export function addDoor(
  doc: GeometryDoc,
  fromSpaceId: string,
  toSpaceId: string | null,
  position: Point[],
  clearWidthMillimetres = 1200,
  exit = false,
): GeometryDoc {
  const door: Door = { id: nextId('d'), fromSpaceId, toSpaceId, position, clearWidthMillimetres, exit }
  return { ...doc, doors: [...doc.doors, door] }
}

export function toggleExit(doc: GeometryDoc, doorId: string): GeometryDoc {
  return { ...doc, doors: doc.doors.map((d) => (d.id === doorId ? { ...d, exit: !d.exit } : d)) }
}
