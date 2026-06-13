import { useState } from 'react'
import type { GeometryDoc, Point } from '../api/types'
import type { ImportDraft } from '../api/imports'
import { metresPerPixel, pxGeometryToMetres } from './importConvert'

interface Props {
  draft: ImportDraft
  onConfirm: (geometryMetres: GeometryDoc) => void
  onCancel: () => void
}

export default function ImportReviewCanvas({ draft, onConfirm, onCancel }: Props) {
  const [geo, setGeo] = useState<GeometryDoc>(draft.draftGeometryPx)
  const [mpp, setMpp] = useState<number | null>(draft.scaleGuess?.metresPerPixel ?? null)
  const [calPts, setCalPts] = useState<Point[]>([])
  const [knownMetres, setKnownMetres] = useState(1)

  const W = draft.imageWidthPx
  const H = draft.imageHeightPx
  const poly = (pts: Point[]) => pts.map((p) => `${p.x},${p.y}`).join(' ')

  function onSvgClick(e: React.MouseEvent<SVGSVGElement>) {
    const rect = e.currentTarget.getBoundingClientRect()
    const x = ((e.clientX - rect.left) / rect.width) * W
    const y = ((e.clientY - rect.top) / rect.height) * H
    setCalPts((p) => (p.length >= 2 ? [{ x, y }] : [...p, { x, y }]))
  }
  function applyCalibration() {
    if (calPts.length === 2) setMpp(metresPerPixel(calPts[0], calPts[1], knownMetres))
  }
  function setOccupancy(id: string, occ: string) {
    setGeo((g) => ({ ...g, spaces: g.spaces.map((s) => (s.id === id ? { ...s, occupancyType: occ } : s)) }))
  }
  function setDoor(id: string, patch: Partial<{ exit: boolean; clearWidthMillimetres: number }>) {
    setGeo((g) => ({ ...g, doors: g.doors.map((d) => (d.id === id ? { ...d, ...patch } : d)) }))
  }
  function confirm() { if (mpp != null) onConfirm(pxGeometryToMetres(geo, mpp)) }

  return (
    <section style={{ border: '2px solid #3367d6', padding: 8, marginBottom: 12 }}>
      <h2>Review imported plan</h2>
      {draft.warnings.map((w, i) => <p key={i} style={{ color: '#b06000' }}>⚠️ {w}</p>)}
      <svg viewBox={`0 0 ${W} ${H}`} width={800} style={{ border: '1px solid #ccc', maxWidth: '100%' }}
           onClick={onSvgClick}>
        <image href={`data:image/png;base64,${draft.backdropPngBase64}`} x={0} y={0} width={W} height={H} />
        {geo.spaces.map((s) => (
          <polygon key={s.id} points={poly(s.polygon)} fill="rgba(51,103,214,0.15)" stroke="#3367d6" />
        ))}
        {geo.doors.map((d) => (
          <line key={d.id} x1={d.position[0].x} y1={d.position[0].y} x2={d.position[1].x} y2={d.position[1].y}
                stroke={d.exit ? '#0b8043' : '#999'} strokeWidth={3} />
        ))}
        {calPts.length > 0 && (
          <polyline points={poly(calPts)} fill="none" stroke="#ff6d00" strokeWidth={2} strokeDasharray="4" />
        )}
      </svg>

      <fieldset>
        <legend>Scale</legend>
        <p>{mpp == null ? 'Not set — calibrate before checking.' : `${mpp.toFixed(4)} m / pixel`}</p>
        <p style={{ fontSize: 12 }}>Click two points of a known length on the plan, enter its real length, then Apply.</p>
        <label>Known length (m){' '}
          <input type="number" value={knownMetres} min={0} step={0.1}
                 onChange={(e) => setKnownMetres(Number(e.target.value))} style={{ width: 72 }} />
        </label>{' '}
        <button onClick={applyCalibration} disabled={calPts.length !== 2}>Apply calibration</button>
      </fieldset>

      <fieldset>
        <legend>Rooms</legend>
        {geo.spaces.map((s) => (
          <div key={s.id}>
            {s.name}{' '}
            <select value={s.occupancyType} onChange={(e) => setOccupancy(s.id, e.target.value)}>
              <option value="">use…</option>
              <option value="WB">WB — working/business</option>
              <option value="CA">CA — crowd activity</option>
            </select>
          </div>
        ))}
      </fieldset>

      <fieldset>
        <legend>Doors</legend>
        {geo.doors.map((d) => (
          <div key={d.id}>
            {d.id}{' '}
            <label><input type="checkbox" checked={d.exit}
                          onChange={(e) => setDoor(d.id, { exit: e.target.checked })} /> exit</label>{' '}
            <label>width (mm){' '}
              <input type="number" value={d.clearWidthMillimetres} min={0} step={50}
                     onChange={(e) => setDoor(d.id, { clearWidthMillimetres: Number(e.target.value) })}
                     style={{ width: 80 }} />
            </label>
          </div>
        ))}
      </fieldset>

      <button onClick={confirm} disabled={mpp == null}>Confirm &amp; load into editor</button>{' '}
      <button onClick={onCancel}>Cancel</button>
    </section>
  )
}
