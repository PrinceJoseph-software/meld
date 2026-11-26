import { useEffect, useRef, useState } from 'react';
import {
  ACCESSORY_COUNT, ACCESSORY_NAMES, CHASSIS_COUNT, CHASSIS_NAMES, EYES_COUNT, EYES_NAMES,
  FRAME_COUNT, FRAME_NAMES, PALETTE_NAMES, PATTERN_COUNT, PATTERN_NAMES,
  fromPacked, randomSpec, toPacked, type AvatarSpec,
} from '../avatar';
import { BADGE_COLORS, getIdentity, saveIdentity, type Identity } from '../identity';
import { GeometricAvatar } from './GeometricAvatar';

type Mode = 'profile' | 'mask';
type Tab = 'chassis' | 'palette' | 'pattern' | 'eyes' | 'accessory' | 'frame';

const TABS: { id: Tab; label: string; title: string }[] = [
  { id: 'chassis', label: 'Shape', title: 'Geometric shape' },
  { id: 'palette', label: 'Palette', title: 'Colour palette' },
  { id: 'pattern', label: 'Pattern', title: 'Surface pattern' },
  { id: 'eyes', label: 'Eyes', title: 'Eye expression' },
  { id: 'accessory', label: 'Gear', title: 'Gear & accessories' },
  { id: 'frame', label: 'Frame', title: 'Border frame' },
];

const MASK_ALIASES = ['Agent 07', 'Phantom', 'Incognito', 'Specter', 'Cipher', 'Shadow', 'Ghost', 'Enigma', 'Mirage', 'Nomad'];

const OPTIONS: Record<Exclude<Tab, 'palette'>, { count: number; names: string[]; key: keyof AvatarSpec }> = {
  chassis: { count: CHASSIS_COUNT, names: CHASSIS_NAMES, key: 'chassis' },
  pattern: { count: PATTERN_COUNT, names: PATTERN_NAMES, key: 'pattern' },
  eyes: { count: EYES_COUNT, names: EYES_NAMES, key: 'eyes' },
  accessory: { count: ACCESSORY_COUNT, names: ACCESSORY_NAMES, key: 'accessory' },
  frame: { count: FRAME_COUNT, names: FRAME_NAMES, key: 'frame' },
};

/** Avatar Studio dialog: same layers, names and modes as the Android Settings studio. */
export function AvatarStudio({ open, onClose, onSaved }: { open: boolean; onClose: () => void; onSaved: (id: Identity) => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const start = getIdentity();
  const [mode, setMode] = useState<Mode>('profile');
  const [tab, setTab] = useState<Tab>('chassis');
  const [pulse, setPulse] = useState(0);
  const [std, setStd] = useState({ spec: fromPacked(start.avatarStyle), color: start.badgeColorIndex, name: start.displayName });
  const [mask, setMask] = useState({ spec: fromPacked(start.anonAvatarStyle), color: start.anonColorIndex, name: start.maskName });

  useEffect(() => {
    const d = dialog.current;
    if (!d) return;
    if (open && !d.open) {
      const id = getIdentity();
      setStd({ spec: fromPacked(id.avatarStyle), color: id.badgeColorIndex, name: id.displayName });
      setMask({ spec: fromPacked(id.anonAvatarStyle), color: id.anonColorIndex, name: id.maskName });
      d.showModal();
    } else if (!open && d.open) {
      d.close();
    }
  }, [open]);

  const active = mode === 'profile' ? std : mask;
  const setActive = (patch: Partial<typeof std>) => {
    (mode === 'profile' ? setStd : setMask)((s) => ({ ...s, ...patch }));
    setPulse((p) => p + 1);
  };

  const save = () => {
    saveIdentity({
      displayName: std.name,
      avatarStyle: toPacked(std.spec),
      badgeColorIndex: std.color,
      maskName: mask.name,
      anonAvatarStyle: toPacked(mask.spec),
      anonColorIndex: mask.color,
    });
    onSaved(getIdentity());
    onClose();
  };

  const title = TABS.find((t) => t.id === tab)!.title;

  return (
    <dialog ref={dialog} className="studio" onClose={onClose} aria-labelledby="studio-title">
      <div className="row between">
        <div>
          <div className="eyebrow" id="studio-title">{mode === 'profile' ? 'Avatar Studio' : 'Anonymous Mask'}</div>
          <div className="small muted">
            {mode === 'profile' ? 'Your public avatar and display name' : 'What others see in anonymous rooms'}
          </div>
        </div>
        <button className="btn small" onClick={save}>Done</button>
      </div>

      <div className="segmented" role="group" aria-label="Which identity" style={{ marginTop: 14 }}>
        <button aria-pressed={mode === 'profile'} onClick={() => { setMode('profile'); setPulse((p) => p + 1); }}>My Profile</button>
        <button aria-pressed={mode === 'mask'} onClick={() => { setMode('mask'); setPulse((p) => p + 1); }}>Anonymous Mask</button>
      </div>

      <div className="row" style={{ marginTop: 16, gap: 16 }}>
        <GeometricAvatar avatarStyleIndex={toPacked(active.spec)} colorIndex={active.color} size={84} reactionTrigger={pulse} pushBias={0} />
        <div className="grow stack">
          <input
            className="input"
            maxLength={mode === 'profile' ? 28 : 24}
            placeholder={mode === 'profile' ? 'Display name (e.g. Alex)' : 'Mask name'}
            value={active.name}
            onChange={(e) => (mode === 'profile' ? setStd : setMask)((s) => ({ ...s, name: e.target.value }))}
            aria-label={mode === 'profile' ? 'Display name' : 'Mask name'}
          />
          <div className="chips">
            <button
              className="btn small"
              onClick={() => setActive({
                spec: randomSpec(),
                color: Math.floor(Math.random() * BADGE_COLORS.length),
                ...(mode === 'mask' ? { name: MASK_ALIASES[Math.floor(Math.random() * MASK_ALIASES.length)] } : {}),
              })}
            >
              Random disguise
            </button>
            <button className="btn small ghost" onClick={() => setPulse((p) => p + 1)}>Preview</button>
          </div>
        </div>
      </div>

      <div className="tabs" role="tablist" aria-label="Avatar layers" style={{ marginTop: 16 }}>
        {TABS.map((t) => (
          <button key={t.id} role="tab" aria-selected={tab === t.id} onClick={() => setTab(t.id)}>{t.label}</button>
        ))}
      </div>
      <div className="small muted mono" style={{ margin: '12px 0 8px' }}>{title}</div>

      <div className="studio-grid" role="tabpanel">
        {tab === 'palette'
          ? BADGE_COLORS.map((_, i) => (
              <button key={i} className="studio-item" aria-pressed={active.color === i} onClick={() => setActive({ color: i })} aria-label={`${PALETTE_NAMES[i]} palette`}>
                <GeometricAvatar avatarStyleIndex={toPacked(active.spec)} colorIndex={i} size={40} />
                <span>{PALETTE_NAMES[i]}</span>
              </button>
            ))
          : Array.from({ length: OPTIONS[tab].count }, (_, i) => {
              const { key, names } = OPTIONS[tab];
              const spec = { ...active.spec, [key]: i };
              return (
                <button key={i} className="studio-item" aria-pressed={active.spec[key] === i} onClick={() => setActive({ spec })}>
                  <GeometricAvatar avatarStyleIndex={toPacked(spec)} colorIndex={active.color} size={40} />
                  <span>{names[i]}</span>
                </button>
              );
            })}
      </div>
      <p className="small muted" style={{ marginBottom: 0 }}>
        {mode === 'profile' ? 'Shown next to your votes in normal rooms.' : 'This badge disguises your identity on anonymous ballots.'}
      </p>
    </dialog>
  );
}
