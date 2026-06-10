import { render, screen } from '@testing-library/react'
import { vi, describe, it, expect, beforeEach } from 'vitest'
import App from './App'

describe('App', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('shows backend status from /api/health', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({ status: 'ok', engine: 'compliance-engine 0.1.0' }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    render(<App />)

    expect(await screen.findByText(/Backend status:/)).toBeInTheDocument()
    expect(screen.getByText('ok')).toBeInTheDocument()
    expect(screen.getByText(/compliance-engine 0\.1\.0/)).toBeInTheDocument()
  })
})
