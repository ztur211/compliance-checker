import type { CheckResult } from '../api/checks'

export default function ResultsPanel({ result }: { result: CheckResult | null }) {
  if (!result) return null
  if (result.blocked) return <p role="alert">⚠️ {result.blockMessage}</p>
  if (result.violations.length === 0) return <p>✅ No violations found.</p>
  return (
    <ul>
      {result.violations.map((v, i) => (
        <li key={i} style={{ color: '#c5221f' }}>❌ {v.message}</li>
      ))}
    </ul>
  )
}
