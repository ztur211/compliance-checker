import type { GeometryDoc } from './types'

export interface ScaleGuess { metresPerPixel: number; source: string; confidence: number }

export interface ImportDraft {
  backdropPngBase64: string
  imageWidthPx: number
  imageHeightPx: number
  draftGeometryPx: GeometryDoc   // coordinates are IMAGE PIXELS until Confirm
  scaleGuess: ScaleGuess | null
  warnings: string[]
}

/** Upload a PDF/image; the browser sets the multipart boundary, so do NOT set Content-Type. */
export async function uploadImport(file: File): Promise<ImportDraft> {
  const form = new FormData()
  form.append('file', file)
  const res = await fetch('/api/imports', { method: 'POST', body: form })
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}: ${await res.text()}`)
  return res.json() as Promise<ImportDraft>
}
