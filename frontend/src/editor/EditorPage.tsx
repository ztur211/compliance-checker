import { useEffect, useState } from 'react'
import type { Point } from '../api/types'
import { getFloorPlan, saveFloorPlan } from '../api/floorplans'
import { startCheck, pollCheck, type CheckResult } from '../api/checks'
import { useFloorPlan } from './useFloorPlan'
import EditorCanvas from './EditorCanvas'
import Toolbar, { type Mode } from './Toolbar'
import ResultsPanel from './ResultsPanel'
import { SAMPLES } from './samples'

interface Props { floorPlanId: string }

export default function EditorPage({ floorPlanId }: Props) {
  const fp = useFloorPlan()
  const [mode, setMode] = useState<Mode>('space')
  const [draft, setDraft] = useState<Point[]>([])
  const [name, setName] = useState('Level 1')
  const [saving, setSaving] = useState(false)
  const [checking, setChecking] = useState(false)
  const [result, setResult] = useState<CheckResult | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getFloorPlan(floorPlanId).then((loaded) => { fp.setDoc(loaded.geometry); setName(loaded.name) })
      .catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [floorPlanId])

  function onCanvasClick(p: Point) {
    if (mode === 'space') setDraft((d) => [...d, p])
    else if (mode === 'exitDoor' && fp.doc.spaces.length > 0) {
      const space = fp.doc.spaces[fp.doc.spaces.length - 1]
      fp.commitDoor(space.id, null, [{ x: p.x, y: p.y - 0.5 }, { x: p.x, y: p.y + 0.5 }], true)
    }
  }
  function finishSpace() { if (draft.length >= 3) fp.commitSpace(draft); setDraft([]) }

  async function save() {
    setSaving(true)
    try {
      await saveFloorPlan(floorPlanId, { name, riskGroup: 'WB', sprinklered: true, escapeHeightMetres: 3, geometry: fp.doc })
    } finally { setSaving(false) }
  }

  async function runCheck() {
    setChecking(true); setResult(null); setError(null)
    try {
      await save()
      const { runId } = await startCheck(floorPlanId)
      const run = await pollCheck(runId)
      if (run.status === 'FAILED') setError(run.error ?? 'Check failed.')
      else setResult(run.result)
    } catch (e) {
      setError(String(e))   // network error or poll timeout
    } finally { setChecking(false) }
  }

  const violationSpaceIds = (result?.violations ?? []).map((v) => v.spaceId).filter((x): x is string => !!x)
  const pathNodeIds = result?.violations.find((v) => v.pathNodeIds.length > 0)?.pathNodeIds ?? []

  return (
    <main>
      <h1>compliance-checker — editor</h1>
      <label>
        Load sample:{' '}
        <select
          defaultValue=""
          onChange={(e) => {
            const s = SAMPLES.find((x) => x.name === e.target.value)
            if (s) fp.setDoc(s.doc)
          }}
        >
          <option value="" disabled>Choose…</option>
          {SAMPLES.map((s) => <option key={s.name} value={s.name}>{s.name}</option>)}
        </select>
      </label>
      <label>Plan name <input value={name} onChange={(e) => setName(e.target.value)} /></label>
      <Toolbar mode={mode} onMode={setMode} onFinishSpace={finishSpace} onSave={save} saving={saving} />
      <button onClick={runCheck} disabled={checking}>{checking ? 'Checking…' : 'Check compliance'}</button>
      <EditorCanvas doc={fp.doc} draft={draft} onCanvasClick={onCanvasClick}
                    violationSpaceIds={violationSpaceIds} pathNodeIds={pathNodeIds} />
      {error && <p role="alert" style={{ color: '#c5221f' }}>⚠️ {error}</p>}
      <ResultsPanel result={result} />
      <p>Spaces: {fp.doc.spaces.length} · Doors: {fp.doc.doors.length}</p>
    </main>
  )
}
