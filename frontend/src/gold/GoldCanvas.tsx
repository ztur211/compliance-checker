import { useRef, useState } from 'react'
import type { Point } from '../api/types'
import type { GoldDoor, GoldRoom } from './goldPlan'

export type Mode = 'room' | 'door' | 'scale'

interface Props {
  imageHref: string
  /** Natural pixel size of the image. Every coordinate we emit is in this space. */
  widthPx: number
  heightPx: number
  rooms: GoldRoom[]
  doors: GoldDoor[]
  draft: Point[]
  scalePts: Point[]
  mode: Mode
  onAddPoint: (imagePoint: Point) => void
}

const MIN_ZOOM = 1
const MAX_ZOOM = 24

/**
 * The tracing surface. Zoom and pan are the whole point: a 2227px institutional plan rendered at
 * 900px wide puts a classroom wall inside a 2px band, and a gold file traced at that resolution is
 * ground truth that is wrong by several pixels everywhere. Wheel to zoom at the cursor, shift-drag
 * (or middle-drag) to pan.
 */
export default function GoldCanvas({
  imageHref, widthPx, heightPx, rooms, doors, draft, scalePts, mode, onAddPoint,
}: Props) {
  const svgRef = useRef<SVGSVGElement>(null)
  const [zoom, setZoom] = useState(1)
  const [origin, setOrigin] = useState<Point>({ x: 0, y: 0 })
  const panFrom = useRef<{ client: Point; origin: Point } | null>(null)

  const viewW = widthPx / zoom
  const viewH = heightPx / zoom

  function toImagePoint(clientX: number, clientY: number): Point | null {
    const rect = svgRef.current?.getBoundingClientRect()
    if (!rect || rect.width === 0 || rect.height === 0) return null
    return {
      x: origin.x + ((clientX - rect.left) / rect.width) * viewW,
      y: origin.y + ((clientY - rect.top) / rect.height) * viewH,
    }
  }

  function clampOrigin(o: Point, w: number, h: number): Point {
    return {
      x: Math.min(Math.max(o.x, 0), Math.max(0, widthPx - w)),
      y: Math.min(Math.max(o.y, 0), Math.max(0, heightPx - h)),
    }
  }

  function onWheel(e: React.WheelEvent<SVGSVGElement>) {
    const cursor = toImagePoint(e.clientX, e.clientY)
    if (!cursor) return
    const next = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, zoom * (e.deltaY < 0 ? 1.2 : 1 / 1.2)))
    if (next === zoom) return
    const nextW = widthPx / next
    const nextH = heightPx / next
    // Keep the point under the cursor fixed: the zoom should feel like it happens where you point,
    // not at the corner of the image.
    const fx = (cursor.x - origin.x) / viewW
    const fy = (cursor.y - origin.y) / viewH
    setZoom(next)
    setOrigin(clampOrigin({ x: cursor.x - fx * nextW, y: cursor.y - fy * nextH }, nextW, nextH))
  }

  function onPointerDown(e: React.PointerEvent<SVGSVGElement>) {
    if (e.shiftKey || e.button === 1) {
      panFrom.current = { client: { x: e.clientX, y: e.clientY }, origin }
      e.currentTarget.setPointerCapture(e.pointerId)
    }
  }

  function onPointerMove(e: React.PointerEvent<SVGSVGElement>) {
    const start = panFrom.current
    if (!start) return
    const rect = svgRef.current?.getBoundingClientRect()
    if (!rect) return
    const dx = ((e.clientX - start.client.x) / rect.width) * viewW
    const dy = ((e.clientY - start.client.y) / rect.height) * viewH
    setOrigin(clampOrigin({ x: start.origin.x - dx, y: start.origin.y - dy }, viewW, viewH))
  }

  function onPointerUp(e: React.PointerEvent<SVGSVGElement>) {
    if (panFrom.current) {
      panFrom.current = null
      e.currentTarget.releasePointerCapture(e.pointerId)
    }
  }

  function onClick(e: React.MouseEvent<SVGSVGElement>) {
    if (e.shiftKey) return // that was a pan, not a placement
    const p = toImagePoint(e.clientX, e.clientY)
    if (p) onAddPoint(p)
  }

  const pts = (points: Point[]) => points.map((p) => `${p.x},${p.y}`).join(' ')
  // Strokes are specified in image pixels, so they must shrink as we zoom in or a "1px" wall
  // outline becomes a 20px slab that hides the very edge you are trying to trace against.
  const w = (px: number) => px / zoom

  return (
    <svg
      ref={svgRef}
      role="img"
      aria-label="gold tracing canvas"
      viewBox={`${origin.x} ${origin.y} ${viewW} ${viewH}`}
      width={900}
      style={{
        border: '1px solid #ccc',
        maxWidth: '100%',
        touchAction: 'none',
        cursor: mode === 'scale' ? 'crosshair' : 'copy',
      }}
      onWheel={onWheel}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onClick={onClick}
    >
      <image href={imageHref} x={0} y={0} width={widthPx} height={heightPx} />

      {rooms.map((r, i) => (
        <g key={`${r.label}-${i}`}>
          <polygon points={pts(r.polygonPx)} fill="rgba(51,103,214,0.18)" stroke="#3367d6" strokeWidth={w(2)} />
          <text
            x={r.polygonPx.reduce((a, p) => a + p.x, 0) / r.polygonPx.length}
            y={r.polygonPx.reduce((a, p) => a + p.y, 0) / r.polygonPx.length}
            fontSize={w(14)}
            textAnchor="middle"
            fill="#1a2b5e"
            style={{ paintOrder: 'stroke', stroke: '#fff', strokeWidth: w(3) }}
          >
            {r.label}
          </text>
        </g>
      ))}

      {doors.map((d, i) => (
        <line
          key={i}
          x1={d.positionPx[0].x} y1={d.positionPx[0].y}
          x2={d.positionPx[1].x} y2={d.positionPx[1].y}
          stroke={d.exit ? '#0b8043' : '#ff6d00'} strokeWidth={w(4)} strokeLinecap="round"
        />
      ))}

      {draft.length > 0 && (
        <>
          <polyline points={pts(draft)} fill="none" stroke="#ff6d00" strokeWidth={w(2)} strokeDasharray={w(6)} />
          {draft.map((p, i) => (
            <circle key={i} cx={p.x} cy={p.y} r={w(4)} fill="#ff6d00" />
          ))}
        </>
      )}

      {scalePts.length > 0 && (
        <>
          <polyline points={pts(scalePts)} fill="none" stroke="#c5221f" strokeWidth={w(2)} />
          {scalePts.map((p, i) => (
            <circle key={i} cx={p.x} cy={p.y} r={w(4)} fill="#c5221f" />
          ))}
        </>
      )}
    </svg>
  )
}
