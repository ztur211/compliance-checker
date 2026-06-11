import type { GeometryDoc } from '../api/types'

export interface Sample { name: string; doc: GeometryDoc }

const office = (id: string, x0: number, x1: number) => ({
  id, name: id, occupancyType: 'WB',
  polygon: [{ x: x0, y: 0 }, { x: x1, y: 0 }, { x: x1, y: 10 }, { x: x0, y: 10 }],
})

const room = (id: string, name: string, x0: number, y0: number, x1: number, y1: number) => ({
  id, name, occupancyType: 'WB',
  polygon: [{ x: x0, y: y0 }, { x: x1, y: y0 }, { x: x1, y: y1 }, { x: x0, y: y1 }],
})

export const SAMPLES: Sample[] = [
  {
    name: 'Compliant (2 exits)',
    doc: {
      schemaVersion: 1,
      spaces: [office('s1', 0, 10)],
      doors: [
        { id: 'e1', fromSpaceId: 's1', toSpaceId: null, position: [{ x: 0, y: 4 }, { x: 0, y: 6 }], clearWidthMillimetres: 1200, exit: true },
        { id: 'e2', fromSpaceId: 's1', toSpaceId: null, position: [{ x: 10, y: 4 }, { x: 10, y: 6 }], clearWidthMillimetres: 1200, exit: true },
      ],
    },
  },
  {
    name: 'Single exit (too few escape routes)',
    doc: {
      schemaVersion: 1,
      spaces: [office('s1', 0, 10)],
      doors: [
        { id: 'e1', fromSpaceId: 's1', toSpaceId: null, position: [{ x: 0, y: 4 }, { x: 0, y: 6 }], clearWidthMillimetres: 1200, exit: true },
      ],
    },
  },
  {
    // A back wing (Store <-> Plant) that is internally connected by a door but has no exit and no
    // route to s1's exits -> genuine "no means of escape" violation. (A door-less room would be
    // "not evaluated / incomplete model" under the §11 rules, not a violation.)
    name: 'Unreachable wing (no means of escape)',
    doc: {
      schemaVersion: 1,
      spaces: [
        office('s1', 0, 10),
        room('s3', 'Store', 15, 15, 25, 25),
        room('s4', 'Plant', 25, 15, 35, 25),
      ],
      doors: [
        { id: 'e1', fromSpaceId: 's1', toSpaceId: null, position: [{ x: 0, y: 4 }, { x: 0, y: 6 }], clearWidthMillimetres: 1200, exit: true },
        { id: 'e2', fromSpaceId: 's1', toSpaceId: null, position: [{ x: 10, y: 4 }, { x: 10, y: 6 }], clearWidthMillimetres: 1200, exit: true },
        { id: 'd34', fromSpaceId: 's3', toSpaceId: 's4', position: [{ x: 25, y: 19 }, { x: 25, y: 21 }], clearWidthMillimetres: 900, exit: false },
      ],
    },
  },
]
