import { describe, it, expect } from 'vitest'
import { metresPerPixel, pxGeometryToMetres } from './importConvert'
import type { GeometryDoc } from '../api/types'

describe('importConvert', () => {
  it('computes metres per pixel from a known length', () => {
    expect(metresPerPixel({ x: 0, y: 0 }, { x: 200, y: 0 }, 10)).toBeCloseTo(0.05)
  })

  it('throws when calibration points coincide', () => {
    expect(() => metresPerPixel({ x: 5, y: 5 }, { x: 5, y: 5 }, 10)).toThrow()
  })

  it('scales pixel coords to metres but leaves door widths alone', () => {
    const px: GeometryDoc = {
      schemaVersion: 1,
      spaces: [{ id: 'room-1', name: 'Office', occupancyType: 'WB',
        polygon: [{ x: 0, y: 0 }, { x: 200, y: 0 }, { x: 200, y: 200 }, { x: 0, y: 200 }] }],
      doors: [{ id: 'door-1', fromSpaceId: 'room-1', toSpaceId: null,
        position: [{ x: 0, y: 80 }, { x: 0, y: 120 }], clearWidthMillimetres: 1200, exit: true }],
    }
    const m = pxGeometryToMetres(px, 0.05)
    expect(m.spaces[0].polygon[1]).toEqual({ x: 10, y: 0 })
    expect(m.doors[0].position[1]).toEqual({ x: 0, y: 6 })
    expect(m.doors[0].clearWidthMillimetres).toBe(1200) // mm, unchanged
  })
})
