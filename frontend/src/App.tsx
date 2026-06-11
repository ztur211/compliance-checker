import { useEffect, useState } from 'react'
import { createProject } from './api/projects'
import { createFloorPlan } from './api/floorplans'
import EditorPage from './editor/EditorPage'

export default function App() {
  const [floorPlanId, setFloorPlanId] = useState<string | null>(null)

  useEffect(() => {
    // Bootstrap a project + floor plan for the demo. (Project/plan pickers come later.)
    createProject('Demo project')
      .then((p) => createFloorPlan(p.id, 'Level 1'))
      .then((fp) => setFloorPlanId(fp.id))
      .catch((e) => console.error(e))
  }, [])

  if (!floorPlanId) return <p>Starting…</p>
  return <EditorPage floorPlanId={floorPlanId} />
}
