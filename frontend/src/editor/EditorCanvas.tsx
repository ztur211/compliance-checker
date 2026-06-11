import type { GeometryDoc, Point } from '../api/types'

const SCALE = 20 // pixels per metre

interface Props {
  doc: GeometryDoc
  draft: Point[]                 // in-progress space polygon
  onCanvasClick: (worldPoint: Point) => void
}

export default function EditorCanvas({ doc, draft, onCanvasClick }: Props) {
  function handleClick(e: React.MouseEvent<SVGSVGElement>) {
    const rect = e.currentTarget.getBoundingClientRect()
    onCanvasClick({ x: (e.clientX - rect.left) / SCALE, y: (e.clientY - rect.top) / SCALE })
  }

  const poly = (pts: Point[]) => pts.map((p) => `${p.x * SCALE},${p.y * SCALE}`).join(' ')

  return (
    <svg
      width={800}
      height={600}
      role="img"
      aria-label="floor plan canvas"
      style={{ border: '1px solid #ccc', background: '#fafafa' }}
      onClick={handleClick}
    >
      {doc.spaces.map((s) => (
        <polygon key={s.id} points={poly(s.polygon)} fill="#e8f0fe" stroke="#3367d6" />
      ))}
      {doc.doors.map((d) => (
        <line
          key={d.id}
          x1={d.position[0].x * SCALE} y1={d.position[0].y * SCALE}
          x2={d.position[1].x * SCALE} y2={d.position[1].y * SCALE}
          stroke={d.exit ? '#0b8043' : '#999'} strokeWidth={4}
        />
      ))}
      {draft.length > 0 && (
        <polyline points={poly(draft)} fill="none" stroke="#ff6d00" strokeDasharray="4" />
      )}
    </svg>
  )
}
