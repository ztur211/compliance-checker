import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import GoldCanvas from './GoldCanvas'

/**
 * The property that matters: a click must be reported in the IMAGE's pixel space, not the SVG's
 * rendered size. Get this wrong and every gold file is silently scaled by the ratio between the
 * two, which no test of the export format would ever catch.
 */
function renderCanvas(onAddPoint: (p: { x: number; y: number }) => void) {
  const view = render(
    <GoldCanvas
      imageHref="data:,"
      widthPx={2000}
      heightPx={1000}
      rooms={[]}
      doors={[]}
      draft={[]}
      scalePts={[]}
      mode="room"
      onAddPoint={onAddPoint}
    />,
  )
  const svg = screen.getByRole('img', { name: 'gold tracing canvas' })
  // jsdom has no layout, so the SVG reports a zero-size rect unless we supply one. Render it at
  // 500x250: a quarter of the image's natural size, so a bad implementation returns 1/4 the answer.
  svg.getBoundingClientRect = () =>
    ({ left: 0, top: 0, width: 500, height: 250, right: 500, bottom: 250, x: 0, y: 0, toJSON: () => ({}) }) as DOMRect
  return { view, svg }
}

describe('GoldCanvas', () => {
  it('reports clicks in image pixels, not rendered pixels', () => {
    const onAddPoint = vi.fn()
    const { svg } = renderCanvas(onAddPoint)

    fireEvent.click(svg, { clientX: 250, clientY: 125 }) // dead centre of the rendered box

    expect(onAddPoint).toHaveBeenCalledWith({ x: 1000, y: 500 }) // centre of the 2000x1000 image
  })

  it('ignores a shift-click, which is a pan and not a point placement', () => {
    const onAddPoint = vi.fn()
    const { svg } = renderCanvas(onAddPoint)

    fireEvent.click(svg, { clientX: 250, clientY: 125, shiftKey: true })

    expect(onAddPoint).not.toHaveBeenCalled()
  })

  it('starts showing the whole image', () => {
    const onAddPoint = vi.fn()
    const { svg } = renderCanvas(onAddPoint)

    expect(svg.getAttribute('viewBox')).toBe('0 0 2000 1000')
  })
})
