import { describe, expect, it } from 'vitest'
import { buildGold, goldFileName, metresPerPixel } from './goldPlan'

describe('buildGold', () => {
  it('emits exactly the field names GoldPlan.java deserializes', () => {
    const gold = buildGold(
      [{ label: 'CLASS ROOM 101', polygonPx: [{ x: 1.4, y: 2.6 }, { x: 3, y: 2 }, { x: 3, y: 5 }] }],
      [{ positionPx: [{ x: 1, y: 1 }, { x: 2, y: 1 }], exit: true }],
      0.027,
      true,
    )
    expect(gold).toEqual({
      rooms: [{ label: 'CLASS ROOM 101', polygonPx: [{ x: 1, y: 3 }, { x: 3, y: 2 }, { x: 3, y: 5 }] }],
      doors: [{ positionPx: [{ x: 1, y: 1 }, { x: 2, y: 1 }], exit: true }],
      scaleMetresPerPixel: 0.027,
      scoreScale: true,
    })
  })

  it('keeps a null scale as null, which is a real expectation and not a missing value', () => {
    expect(buildGold([], [], null, false).scaleMetresPerPixel).toBeNull()
  })
})

describe('metresPerPixel', () => {
  it('divides the known length by the pixel distance', () => {
    expect(metresPerPixel({ x: 0, y: 0 }, { x: 100, y: 0 }, 10)).toBeCloseTo(0.1, 10)
  })

  it('returns null for two identical points rather than Infinity', () => {
    // A double-click at one spot would otherwise produce an infinite scale and silently poison
    // every area on the plan.
    expect(metresPerPixel({ x: 5, y: 5 }, { x: 5, y: 5 }, 10)).toBeNull()
  })

  it('returns null for a non-positive known length', () => {
    expect(metresPerPixel({ x: 0, y: 0 }, { x: 10, y: 0 }, 0)).toBeNull()
    expect(metresPerPixel({ x: 0, y: 0 }, { x: 10, y: 0 }, -3)).toBeNull()
  })
})

describe('goldFileName', () => {
  it('follows the sibling-file convention the scorer relies on', () => {
    expect(goldFileName('schenley-high-school-1916.png')).toBe('schenley-high-school-1916.gold.json')
    expect(goldFileName('wealthy-home-sample.jpg')).toBe('wealthy-home-sample.gold.json')
  })

  it('does not mangle a dotted name', () => {
    expect(goldFileName('plan.v2.final.png')).toBe('plan.v2.final.gold.json')
  })
})
