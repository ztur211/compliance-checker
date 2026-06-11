import { useEffect, useState } from 'react'
import type { Point } from '../api/types'
import { getFloorPlan, saveFloorPlan } from '../api/floorplans'
import { useFloorPlan } from './useFloorPlan'
import EditorCanvas from './EditorCanvas'
import Toolbar, { type Mode } from './Toolbar'

interface Props { floorPlanId: string }

export default function EditorPage({ floorPlanId }: Props) {
  const fp = useFloorPlan()
  const [mode, setMode] = useState<Mode>('space')
  const [draft, setDraft] = useState<Point[]>([])
  const [name, setName] = useState('Level 1')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    getFloorPlan(floorPlanId).then((loaded) => {
      fp.setDoc(loaded.geometry)
      setName(loaded.name)
    }).catch(() => { /* new/unsaved plan */ })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [floorPlanId])

  function onCanvasClick(p: Point) {
    if (mode === 'space') {
      setDraft((d) => [...d, p])
    } else if (mode === 'exitDoor' && fp.doc.spaces.length > 0) {
      // attach exit door to the most-recent space at the clicked point (0.5 m segment)
      const space = fp.doc.spaces[fp.doc.spaces.length - 1]
      fp.commitDoor(space.id, null, [{ x: p.x, y: p.y - 0.5 }, { x: p.x, y: p.y + 0.5 }], true)
    }
  }

  function finishSpace() {
    if (draft.length >= 3) fp.commitSpace(draft)
    setDraft([])
  }

  async function save() {
    setSaving(true)
    try {
      await saveFloorPlan(floorPlanId, {
        name, riskGroup: 'WB', sprinklered: true, escapeHeightMetres: 3, geometry: fp.doc,
      })
    } finally {
      setSaving(false)
    }
  }

  return (
    <main>
      <h1>compliance-checker — editor</h1>
      <label>Plan name <input value={name} onChange={(e) => setName(e.target.value)} /></label>
      <Toolbar mode={mode} onMode={setMode} onFinishSpace={finishSpace} onSave={save} saving={saving} />
      <EditorCanvas doc={fp.doc} draft={draft} onCanvasClick={onCanvasClick} />
      <p>Spaces: {fp.doc.spaces.length} · Doors: {fp.doc.doors.length}</p>
    </main>
  )
}
