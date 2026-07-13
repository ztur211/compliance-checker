import { useState } from 'react'
import type { Point } from '../api/types'
import GoldCanvas, { type Mode } from './GoldCanvas'
import { buildGold, goldFileName, metresPerPixel, type GoldDoor, type GoldRoom } from './goldPlan'

interface Loaded {
  href: string
  name: string
  widthPx: number
  heightPx: number
}

/**
 * Authors a `<image>.gold.json` ground-truth file by tracing a plan image.
 *
 * This is a labelling tool, not a model. The point of a gold file is that a HUMAN decided what is
 * in the drawing, independently of the extractor being scored: ground truth produced by the same
 * model under test measures self-agreement, not accuracy. So nothing here guesses, autocompletes,
 * or pre-fills a room for you. That is the feature.
 */
export default function GoldPage() {
  const [image, setImage] = useState<Loaded | null>(null)
  const [mode, setMode] = useState<Mode>('room')
  const [rooms, setRooms] = useState<GoldRoom[]>([])
  const [doors, setDoors] = useState<GoldDoor[]>([])
  const [draft, setDraft] = useState<Point[]>([])
  const [label, setLabel] = useState('')
  const [scalePts, setScalePts] = useState<Point[]>([])
  const [knownMetres, setKnownMetres] = useState(10)
  const [mpp, setMpp] = useState<number | null>(null)
  const [scoreScale, setScoreScale] = useState(true)

  function onFile(file: File | undefined) {
    if (!file) return
    const href = URL.createObjectURL(file)
    const probe = new Image()
    probe.onload = () => {
      // Natural size, not rendered size: gold coordinates are pixels of the committed fixture.
      setImage({ href, name: file.name, widthPx: probe.naturalWidth, heightPx: probe.naturalHeight })
      setRooms([]); setDoors([]); setDraft([]); setScalePts([]); setMpp(null)
    }
    probe.src = href
  }

  function addPoint(p: Point) {
    if (mode === 'scale') {
      setScalePts((prev) => (prev.length >= 2 ? [p] : [...prev, p]))
      return
    }
    if (mode === 'door') {
      const next = [...draft, p]
      if (next.length === 2) {
        setDoors((d) => [...d, { positionPx: next, exit: false }])
        setDraft([])
      } else {
        setDraft(next)
      }
      return
    }
    setDraft((prev) => [...prev, p])
  }

  function finishRoom() {
    if (draft.length < 3 || !label.trim()) return
    setRooms((r) => [...r, { label: label.trim(), polygonPx: draft }])
    setDraft([])
    setLabel('')
  }

  function applyScale() {
    if (scalePts.length !== 2) return
    setMpp(metresPerPixel(scalePts[0], scalePts[1], knownMetres))
  }

  function download() {
    if (!image) return
    const gold = buildGold(rooms, doors, mpp, scoreScale)
    const blob = new Blob([JSON.stringify(gold, null, 2) + '\n'], { type: 'application/json' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = goldFileName(image.name)
    a.click()
    URL.revokeObjectURL(a.href)
  }

  if (!image) {
    return (
      <section style={{ padding: 12 }}>
        <h2>Gold authoring</h2>
        <p>
          Trace a plan image to author its <code>*.gold.json</code> ground truth, then drop the file
          next to the image in <code>app/src/test/resources/import-gold/</code>.
        </p>
        <input type="file" accept="image/*" onChange={(e) => onFile(e.target.files?.[0])} />
      </section>
    )
  }

  const canFinishRoom = draft.length >= 3 && label.trim().length > 0

  return (
    <section style={{ padding: 12 }}>
      <h2>Gold authoring — {image.name}</h2>
      <p style={{ fontSize: 12, color: '#555' }}>
        {image.widthPx}×{image.heightPx} px · wheel to zoom · shift-drag to pan · click to place points
      </p>

      <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
        {(['room', 'door', 'scale'] as Mode[]).map((m) => (
          <button key={m} aria-pressed={mode === m} onClick={() => { setMode(m); setDraft([]) }}>
            {m}
          </button>
        ))}
        <button onClick={() => setDraft((d) => d.slice(0, -1))} disabled={draft.length === 0}>
          Undo point
        </button>
      </div>

      <GoldCanvas
        imageHref={image.href}
        widthPx={image.widthPx}
        heightPx={image.heightPx}
        rooms={rooms}
        doors={doors}
        draft={draft}
        scalePts={scalePts}
        mode={mode}
        onAddPoint={addPoint}
      />

      {mode === 'room' && (
        <fieldset>
          <legend>Room ({draft.length} points)</legend>
          <label>
            Label as printed on the plan{' '}
            <input value={label} onChange={(e) => setLabel(e.target.value)} placeholder="CLASS ROOM 101" />
          </label>{' '}
          <button onClick={finishRoom} disabled={!canFinishRoom}>Finish room</button>
          {draft.length > 0 && draft.length < 3 && <span> — need at least 3 points</span>}
        </fieldset>
      )}

      {mode === 'scale' && (
        <fieldset>
          <legend>Scale</legend>
          <p style={{ margin: '4px 0' }}>
            {mpp == null
              ? 'Not set. Export as null if the plan genuinely has no scale — that is a real expectation the extractor is scored against, not a gap.'
              : `${mpp.toFixed(5)} m/px`}
          </p>
          <label>
            Known length (m){' '}
            <input type="number" min={0.1} step={0.1} value={knownMetres} style={{ width: 80 }}
                   onChange={(e) => setKnownMetres(Number(e.target.value))} />
          </label>{' '}
          <button onClick={applyScale} disabled={scalePts.length !== 2}>Apply</button>{' '}
          <button onClick={() => { setMpp(null); setScalePts([]) }}>Clear</button>
          <p style={{ margin: '4px 0' }}>
            <label>
              <input type="checkbox" checked={scoreScale} onChange={(e) => setScoreScale(e.target.checked)} />{' '}
              score the scale dimension
            </label>
          </p>
        </fieldset>
      )}

      <fieldset>
        <legend>Traced ({rooms.length} rooms, {doors.length} doors)</legend>
        {rooms.map((r, i) => (
          <div key={i}>
            {r.label} ({r.polygonPx.length} pts){' '}
            <button onClick={() => setRooms((rs) => rs.filter((_, j) => j !== i))}>remove</button>
          </div>
        ))}
        {doors.map((d, i) => (
          <div key={i}>
            door {i + 1}{' '}
            <label>
              <input type="checkbox" checked={d.exit}
                     onChange={(e) =>
                       setDoors((ds) => ds.map((x, j) => (j === i ? { ...x, exit: e.target.checked } : x)))} />{' '}
              exit
            </label>{' '}
            <button onClick={() => setDoors((ds) => ds.filter((_, j) => j !== i))}>remove</button>
          </div>
        ))}
      </fieldset>

      <button onClick={download} disabled={rooms.length === 0 && doors.length === 0 && mpp == null}>
        Download {goldFileName(image.name)}
      </button>
    </section>
  )
}
