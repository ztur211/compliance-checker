// Exports the newest Playwright recording to a portfolio MP4 and a README GIF.
// Usage: node scripts/export-demo.mjs    (run from the frontend/ directory; needs ffmpeg on PATH)
import { execFileSync } from 'node:child_process'
import { readdirSync, statSync, mkdirSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const frontend = join(dirname(fileURLToPath(import.meta.url)), '..')
const artifacts = join(frontend, 'demo-artifacts')
const docs = join(frontend, '..', 'docs')

function newestWebm(dir) {
  let best = null
  for (const name of readdirSync(dir)) {
    const p = join(dir, name)
    const s = statSync(p)
    if (s.isDirectory()) { const c = newestWebm(p); if (c && (!best || c.mtime > best.mtime)) best = c }
    else if (name.endsWith('.webm') && (!best || s.mtimeMs > best.mtime)) best = { path: p, mtime: s.mtimeMs }
  }
  return best
}

const src = newestWebm(artifacts)
if (!src) { console.error('No .webm under demo-artifacts/ — run `npm run demo:record` first.'); process.exit(1) }
mkdirSync(docs, { recursive: true })
const mp4 = join(frontend, 'portfolio.mp4')
const palette = join(artifacts, 'palette.png')
const gif = join(docs, 'demo.gif')
const ff = (args) => execFileSync('ffmpeg', ['-y', ...args], { stdio: 'inherit' })

console.log('Source:', src.path)
ff(['-i', src.path, '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '20', '-movflags', '+faststart', mp4])
ff(['-i', src.path, '-vf', 'fps=12,scale=900:-1:flags=lanczos,palettegen', palette])
ff(['-i', src.path, '-i', palette, '-lavfi', 'fps=12,scale=900:-1:flags=lanczos,paletteuse', gif])
console.log('\nWrote:\n  ' + mp4 + '  (portfolio)\n  ' + gif + '  (README)')
console.log('If demo.gif > 5 MB, lower fps (e.g. fps=10) or scale (e.g. scale=720) and re-run.')
