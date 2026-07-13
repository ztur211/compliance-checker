import { useEffect, useState } from 'react'
import { createProject } from './api/projects'
import { createFloorPlan } from './api/floorplans'
import EditorPage from './editor/EditorPage'
import GoldPage from './gold/GoldPage'
import ReviewPage from './rules/ReviewPage'

export default function App() {
  const [floorPlanId, setFloorPlanId] = useState<string | null>(null)
  const [tab, setTab] = useState<'editor' | 'review' | 'gold'>('editor')

  useEffect(() => {
    createProject('Demo project')
      .then((p) => createFloorPlan(p.id, 'Level 1'))
      .then((fp) => setFloorPlanId(fp.id))
      .catch((e) => console.error(e))
  }, [])

  return (
    <>
      <nav style={{ display: 'flex', gap: 8, padding: 8 }}>
        <button aria-pressed={tab === 'editor'} onClick={() => setTab('editor')}>Editor</button>
        <button aria-pressed={tab === 'review'} onClick={() => setTab('review')}>Rule review</button>
        <button aria-pressed={tab === 'gold'} onClick={() => setTab('gold')}>Gold authoring</button>
      </nav>
      {tab === 'review' ? <ReviewPage />
        : tab === 'gold' ? <GoldPage />
        : floorPlanId ? <EditorPage floorPlanId={floorPlanId} />
        : <p>Starting…</p>}
    </>
  )
}
