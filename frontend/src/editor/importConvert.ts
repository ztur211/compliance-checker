import type { GeometryDoc, Point } from '../api/types'

/** Real-world metres per image pixel, from two points and the known real length between them. */
export function metresPerPixel(a: Point, b: Point, knownMetres: number): number {
  const px = Math.hypot(a.x - b.x, a.y - b.y)
  if (px === 0) throw new Error('calibration points must differ')
  return knownMetres / px
}

/** Scale a pixel-space GeometryDoc into metres. Door clear widths (mm) are left untouched. */
export function pxGeometryToMetres(geo: GeometryDoc, mpp: number): GeometryDoc {
  const s = (p: Point): Point => ({ x: p.x * mpp, y: p.y * mpp })
  return {
    schemaVersion: geo.schemaVersion,
    spaces: geo.spaces.map((sp) => ({ ...sp, polygon: sp.polygon.map(s) })),
    doors: geo.doors.map((d) => ({ ...d, position: d.position.map(s) })),
  }
}
