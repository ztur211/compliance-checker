import { useEffect, useState } from 'react'
import { fetchHealth, type Health } from './api/health'

export default function App() {
  const [health, setHealth] = useState<Health | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchHealth()
      .then(setHealth)
      .catch((e) => setError(String(e)))
  }, [])

  return (
    <main>
      <h1>compliance-checker</h1>
      {error && <p role="alert">Backend unreachable: {error}</p>}
      {health ? (
        <p>
          Backend status: <strong>{health.status}</strong> ({health.engine})
        </p>
      ) : (
        !error && <p>Checking backend…</p>
      )}
    </main>
  )
}
