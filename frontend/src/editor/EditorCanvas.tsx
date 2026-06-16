import type { GeometryDoc, Point } from '../api/types'

const SCALE = 20 // pixels per metre

interface Props {
  doc: GeometryDoc
  draft: Point[]
  onCanvasClick: (worldPoint: Point) => void
  violationSpaceIds?: string[]
  pathNodeIds?: string[]    // space ids along a highlighted egress path
}

export default function EditorCanvas({
  doc, draft, onCanvasClick, violationSpaceIds = [], pathNodeIds = [],
}: Props) {
  function handleClick(e: React.MouseEvent<SVGSVGElement>) {
    const rect = e.currentTarget.getBoundingClientRect()
    onCanvasClick({ x: (e.clientX - rect.left) / SCALE, y: (e.clientY - rect.top) / SCALE })
  }
  const poly = (pts: Point[]) => pts.map((p) => `${p.x * SCALE},${p.y * SCALE}`).join(' ')
  const centroid = (pts: Point[]): Point => ({
    x: pts.reduce((a, p) => a + p.x, 0) / pts.length,
    y: pts.reduce((a, p) => a + p.y, 0) / pts.length,
  })
  const byId = Object.fromEntries(doc.spaces.map((s) => [s.id, s]))

  return (
    <svg width={800} height={600} role="img" aria-label="floor plan canvas"
         style={{ border: '1px solid #ccc', background: '#fafafa' }} onClick={handleClick}>
      {doc.spaces.map((s) => {
        const bad = violationSpaceIds.includes(s.id)
        return (
          <polygon key={s.id} points={poly(s.polygon)}
                   fill={bad ? '#fbd5d0' : '#e8f0fe'}
                   stroke={bad ? '#c5221f' : '#3367d6'} strokeWidth={bad ? 3 : 1.5} />
        )
      })}
      {doc.doors.map((d) => (
        <line key={d.id}
              x1={d.position[0].x * SCALE} y1={d.position[0].y * SCALE}
              x2={d.position[1].x * SCALE} y2={d.position[1].y * SCALE}
              stroke={d.exit ? '#0b8043' : '#999'} strokeWidth={4} />
      ))}
      {pathNodeIds.filter((id) => byId[id]).length > 1 && (
        <polyline fill="none" stroke="#c5221f" strokeWidth={3} strokeDasharray="7 5"
                  points={poly(pathNodeIds.filter((id) => byId[id]).map((id) => centroid(byId[id].polygon)))} />
      )}
      {draft.length > 0 && (
        <polyline points={poly(draft)} fill="none" stroke="#ff6d00" strokeDasharray="4" />
      )}
    </svg>
  )
}
