import { useEffect, useState } from 'react'
import { createProject } from './api/projects'
import { createFloorPlan } from './api/floorplans'
import EditorPage from './editor/EditorPage'
import ReviewPage from './rules/ReviewPage'

export default function App() {
  const [floorPlanId, setFloorPlanId] = useState<string | null>(null)
  const [tab, setTab] = useState<'editor' | 'review'>('editor')

  useEffect(() => {
    createProject('Demo project')
      .then((p) => createFloorPlan(p.id, 'Level 1'))
      .then((fp) => setFloorPlanId(fp.id))
      .catch((e) => console.error(e))
  }, [])

  return (
    <>
      <header className="app-header">
        <h1>NZ Fire Egress Compliance Checker</h1>
        <span className="tag">NZBC C/AS2 means of escape · design-aid pre-check</span>
      </header>
      <nav className="tabs">
        <button aria-pressed={tab === 'editor'} onClick={() => setTab('editor')}>Editor</button>
        <button aria-pressed={tab === 'review'} onClick={() => setTab('review')}>Rule review</button>
      </nav>
      {tab === 'review' ? <ReviewPage /> : floorPlanId ? <EditorPage floorPlanId={floorPlanId} /> : <p style={{ padding: '0 20px' }}>Starting…</p>}
    </>
  )
}
