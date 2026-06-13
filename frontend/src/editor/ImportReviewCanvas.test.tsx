import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import ImportReviewCanvas from './ImportReviewCanvas'
import type { ImportDraft, ScaleGuess } from '../api/imports'

const makeDraft = (scaleGuess: ScaleGuess | null): ImportDraft => ({
  backdropPngBase64: 'AAAA',
  imageWidthPx: 200,
  imageHeightPx: 100,
  draftGeometryPx: {
    schemaVersion: 1,
    spaces: [{ id: 'room-1', name: 'Office', occupancyType: 'WB',
      polygon: [{ x: 0, y: 0 }, { x: 100, y: 0 }, { x: 100, y: 100 }, { x: 0, y: 100 }] }],
    doors: [{ id: 'door-1', fromSpaceId: 'room-1', toSpaceId: null,
      position: [{ x: 0, y: 40 }, { x: 0, y: 60 }], clearWidthMillimetres: 1200, exit: true }],
  },
  scaleGuess,
  warnings: [],
})

describe('ImportReviewCanvas', () => {
  it('renders the backdrop and draft; Confirm disabled with no scale', () => {
    render(<ImportReviewCanvas draft={makeDraft(null)} onConfirm={vi.fn()} onCancel={vi.fn()} />)
    expect(document.querySelector('image')).not.toBeNull()
    expect(document.querySelectorAll('polygon')).toHaveLength(1)
    expect(screen.getByRole('button', { name: /confirm/i })).toBeDisabled()
  })

  it('confirms geometry in metres when a scale is known', () => {
    const onConfirm = vi.fn()
    render(<ImportReviewCanvas draft={makeDraft({ metresPerPixel: 0.05, source: 'scale-bar', confidence: 0.8 })}
                               onConfirm={onConfirm} onCancel={vi.fn()} />)
    fireEvent.click(screen.getByRole('button', { name: /confirm/i }))
    expect(onConfirm).toHaveBeenCalledTimes(1)
    expect(onConfirm.mock.calls[0][0].spaces[0].polygon[1]).toEqual({ x: 5, y: 0 }) // 100px * 0.05
  })
})
