import { renderHook, act } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { useFloorPlan } from './useFloorPlan'

describe('useFloorPlan', () => {
  it('adds a space from drawn points', () => {
    const { result } = renderHook(() => useFloorPlan())
    act(() => result.current.commitSpace([
      { x: 0, y: 0 }, { x: 10, y: 0 }, { x: 10, y: 10 }, { x: 0, y: 10 },
    ]))
    expect(result.current.doc.spaces).toHaveLength(1)
    expect(result.current.doc.spaces[0].polygon).toHaveLength(4)
  })

  it('adds an exit door and toggles it', () => {
    const { result } = renderHook(() => useFloorPlan())
    act(() => result.current.commitSpace([
      { x: 0, y: 0 }, { x: 10, y: 0 }, { x: 10, y: 10 }, { x: 0, y: 10 },
    ]))
    const spaceId = result.current.doc.spaces[0].id
    act(() => result.current.commitDoor(spaceId, null, [{ x: 0, y: 4 }, { x: 0, y: 6 }], true))
    expect(result.current.doc.doors[0].exit).toBe(true)
    const doorId = result.current.doc.doors[0].id
    act(() => result.current.toggle(doorId))
    expect(result.current.doc.doors[0].exit).toBe(false)
  })
})
