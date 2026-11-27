import { useEffect, useRef, useState } from 'react';
import {
  ACCESSORY_COUNT, ACCESSORY_NAMES, CHASSIS_COUNT, CHASSIS_NAMES, EYES_COUNT, EYES_NAMES,
  FRAME_COUNT, FRAME_NAMES, PALETTE_NAMES, PATTERN_COUNT, PATTERN_NAMES,
  fromPacked, randomSpec, toPacked, type AvatarSpec,
} from '../avatar';
import { BADGE_COLORS, getIdentity, saveIdentity, type Identity } from '../identity';
import { GeometricAvatar } from './GeometricAvatar';
import { t } from '../i18n';

type Mode = 'profile' | 'mask';
type Tab = 'chassis' | 'palette' | 'pattern' | 'eyes' | 'accessory' | 'frame';

// label/title are i18n keys
const TABS: { id: Tab; label: string; title: string }[] = [
  { id: 'chassis', label: 'studio.tabShape', title: 'studio.tabShapeTitle' },
  { id: 'palette', label: 'studio.tabPalette', title: 'studio.tabPaletteTitle' },
  { id: 'pattern', label: 'studio.tabPattern', title: 'studio.tabPatternTitle' },
  { id: 'eyes', label: 'studio.tabEyes', title: 'studio.tabEyesTitle' },
  { id: 'accessory', label: 'studio.tabGear', title: 'studio.tabGearTitle' },
  { id: 'frame', label: 'studio.tabFrame', title: 'studio.tabFrameTitle' },
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

  const title = t(TABS.find((tb) => tb.id === tab)!.title);

  return (
    <dialog ref={dialog} className="studio" onClose={onClose} aria-labelledby="studio-title">
      <div className="row between">
        <div>
          <div className="eyebrow" id="studio-title">{mode === 'profile' ? t('studio.profileTitle') : t('studio.maskTitle')}</div>
          <div className="small muted">
            {mode === 'profile' ? t('studio.profileSub') : t('studio.maskSub')}
          </div>
        </div>
        <button className="btn small" onClick={save}>{t('common.done')}</button>
      </div>

      <div className="segmented" role="group" aria-label={t('studio.which')} style={{ marginTop: 14 }}>
        <button aria-pressed={mode === 'profile'} onClick={() => { setMode('profile'); setPulse((p) => p + 1); }}>{t('studio.myProfile')}</button>
        <button aria-pressed={mode === 'mask'} onClick={() => { setMode('mask'); setPulse((p) => p + 1); }}>{t('studio.maskTitle')}</button>
      </div>

      <div className="row" style={{ marginTop: 16, gap: 16 }}>
        <GeometricAvatar avatarStyleIndex={toPacked(active.spec)} colorIndex={active.color} size={84} reactionTrigger={pulse} pushBias={0} />
        <div className="grow stack">
          <input
            className="input"
            maxLength={mode === 'profile' ? 28 : 24}
            placeholder={mode === 'profile' ? t('studio.displayNamePh') : t('studio.maskName')}
            value={active.name}
            onChange={(e) => (mode === 'profile' ? setStd : setMask)((s) => ({ ...s, name: e.target.value }))}
            aria-label={mode === 'profile' ? t('studio.displayName') : t('studio.maskName')}
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
              {t('studio.random')}
            </button>
            <button className="btn small ghost" onClick={() => setPulse((p) => p + 1)}>{t('common.preview')}</button>
          </div>
        </div>
      </div>

      <div className="tabs" role="tablist" aria-label={t('studio.layers')} style={{ marginTop: 16 }}>
        {TABS.map((tb) => (
          <button key={tb.id} role="tab" aria-selected={tab === tb.id} onClick={() => setTab(tb.id)}>{t(tb.label)}</button>
        ))}
      </div>
      <div className="small muted mono" style={{ margin: '12px 0 8px' }}>{title}</div>

      <div className="studio-grid" role="tabpanel">
        {tab === 'palette'
          ? BADGE_COLORS.map((_, i) => (
              <button key={i} className="studio-item" aria-pressed={active.color === i} onClick={() => setActive({ color: i })} aria-label={t('studio.paletteAria', { name: PALETTE_NAMES[i] })}>
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
        {mode === 'profile' ? t('studio.profileNote') : t('studio.maskNote')}
      </p>
    </dialog>
  );
}
