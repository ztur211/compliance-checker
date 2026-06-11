export type Mode = 'space' | 'exitDoor'

interface Props {
  mode: Mode
  onMode: (m: Mode) => void
  onFinishSpace: () => void
  onSave: () => void
  saving: boolean
}

export default function Toolbar({ mode, onMode, onFinishSpace, onSave, saving }: Props) {
  return (
    <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
      <button aria-pressed={mode === 'space'} onClick={() => onMode('space')}>Draw space</button>
      <button aria-pressed={mode === 'exitDoor'} onClick={() => onMode('exitDoor')}>Add exit door</button>
      <button onClick={onFinishSpace} disabled={mode !== 'space'}>Finish space</button>
      <button onClick={onSave} disabled={saving}>{saving ? 'Saving…' : 'Save'}</button>
    </div>
  )
}
