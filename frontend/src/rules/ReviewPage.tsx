import { useEffect, useState } from 'react'
import { listDrafts, approve, reject, type RuleDraft } from '../api/rules'

export default function ReviewPage() {
  const [drafts, setDrafts] = useState<RuleDraft[]>([])
  const refresh = () => listDrafts().then(setDrafts).catch(() => setDrafts([]))
  useEffect(() => { refresh() }, [])

  async function act(id: string, fn: (id: string) => Promise<void>) {
    await fn(id)
    await refresh()
  }

  return (
    <main>
      <h1>Rule review</h1>
      {drafts.length === 0 && <p>No draft rules to review.</p>}
      {drafts.map((d) => (
        <div key={d.id} style={{ border: '1px solid #ddd', padding: 8, marginBottom: 8 }}>
          <strong>{d.parameter} {d.comparator} {d.threshold}</strong> — <em>{d.citation}</em>
          {d.confidence != null && <span> · confidence {d.confidence.toFixed(2)}</span>}
          <blockquote style={{ color: '#555' }}>“{d.sourceQuote}”</blockquote>
          <button onClick={() => act(d.id, approve)}>Approve</button>{' '}
          <button onClick={() => act(d.id, reject)}>Reject</button>
        </div>
      ))}
    </main>
  )
}
