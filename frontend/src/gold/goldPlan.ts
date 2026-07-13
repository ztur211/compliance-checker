import type { Point } from '../api/types'

/**
 * Ground truth for one plan image. Mirrors GoldPlan.java exactly: a field renamed here is a gold
 * file that parses into nulls and silently scores nothing.
 *
 * All coordinates are IMAGE PIXELS of the committed fixture, so they must be taken against the
 * image's natural size, not against whatever size the SVG happens to be rendered at.
 */
export interface GoldPlan {
  rooms: GoldRoom[]
  doors: GoldDoor[]
  /** null means "the plan has no resolvable scale, and the extractor is expected to report none". */
  scaleMetresPerPixel: number | null
  /** false skips the scale dimension when scoring, for a plan whose scale you have not established. */
  scoreScale: boolean
}

export interface GoldRoom {
  label: string
  polygonPx: Point[]
}

export interface GoldDoor {
  positionPx: Point[]
  exit: boolean
}

/** Pixel coordinates are whole pixels. Rounding here keeps the exported file readable and diffable. */
const round = (p: Point): Point => ({ x: Math.round(p.x), y: Math.round(p.y) })

export function buildGold(
  rooms: GoldRoom[],
  doors: GoldDoor[],
  scaleMetresPerPixel: number | null,
  scoreScale: boolean,
): GoldPlan {
  return {
    rooms: rooms.map((r) => ({ label: r.label, polygonPx: r.polygonPx.map(round) })),
    doors: doors.map((d) => ({ positionPx: d.positionPx.map(round), exit: d.exit })),
    scaleMetresPerPixel,
    scoreScale,
  }
}

/**
 * Metres per pixel from two clicked points a known real distance apart.
 * Returns null rather than Infinity/NaN when the two points coincide, so a mis-click cannot
 * produce a scale that looks like a number and poisons every area on the plan.
 */
export function metresPerPixel(a: Point, b: Point, knownMetres: number): number | null {
  const px = Math.hypot(a.x - b.x, a.y - b.y)
  if (px <= 0 || !(knownMetres > 0)) return null
  const m = knownMetres / px
  return Number.isFinite(m) && m > 0 ? m : null
}

/** foo.png -> foo.gold.json, matching the sibling-file convention ExtractionScorer relies on. */
export function goldFileName(imageFileName: string): string {
  const base = imageFileName.replace(/\.[^./\\]+$/, '')
  return `${base}.gold.json`
}
