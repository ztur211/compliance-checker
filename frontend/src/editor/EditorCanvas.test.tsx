import { render } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import EditorCanvas from './EditorCanvas'
import type { GeometryDoc } from '../api/types'

const doc: GeometryDoc = {
  schemaVersion: 1,
  spaces: [{ id: 's1', name: 'Office', occupancyType: 'WB',
    polygon: [{ x: 0, y: 0 }, { x: 10, y: 0 }, { x: 10, y: 10 }, { x: 0, y: 10 }] }],
  doors: [{ id: 'd1', fromSpaceId: 's1', toSpaceId: null,
    position: [{ x: 0, y: 4 }, { x: 0, y: 6 }], clearWidthMillimetres: 1200, exit: true }],
}

describe('EditorCanvas', () => {
  it('renders one polygon and one (exit) door line', () => {
    const { container } = render(<EditorCanvas doc={doc} draft={[]} onCanvasClick={vi.fn()} />)
    expect(container.querySelectorAll('polygon')).toHaveLength(1)
    const line = container.querySelector('line')
    expect(line).not.toBeNull()
    expect(line!.getAttribute('stroke')).toBe('#0b8043') // exit colour
  })
})
