import { useMemo, useState, type DragEvent } from 'react';
import { NodePreview } from './preview/NodePreview';
import { resolveColor, resolveTheme } from './preview/theme';
import { interpolate } from './expressions';
import { useBuilder } from './store';
import type { Json, SduiNode } from './types';

const DEVICES = [
  { id: 'phone', label: 'Phone', width: 390, height: 780 },
  { id: 'compact', label: 'Compact', width: 340, height: 700 },
  { id: 'tablet', label: 'Tablet', width: 620, height: 820 },
] as const;

/**
 * The device canvas.
 *
 * It renders the working document with the project's real theme and the screen's
 * seeded state, so what a designer sees is what the phone will draw. Selection,
 * drag-in from the palette and drag-to-reorder all operate on the same tree the
 * JSON editor shows.
 */
export function Canvas() {
  const doc = useBuilder((s) => s.doc);
  const selected = useBuilder((s) => s.selected);
  const themes = useBuilder((s) => s.themes);
  const addNode = useBuilder((s) => s.addNode);
  const select = useBuilder((s) => s.select);

  const [deviceId, setDeviceId] = useState<(typeof DEVICES)[number]['id']>('phone');
  const [dark, setDark] = useState(false);

  const device = DEVICES.find((d) => d.id === deviceId)!;

  const themeSpec = useMemo(
    () => themes.find((t) => t.id === (doc?.theme ?? 'brand'))?.definition ?? themes[0]?.definition,
    [themes, doc?.theme],
  );
  const theme = useMemo(() => resolveTheme(themeSpec, dark), [themeSpec, dark]);

  /**
   * The preview scope mirrors the device's layering: screen state, then a small
   * sample of global/session values so `{{user.name}}` is not blank while editing.
   */
  const scope = useMemo<Record<string, Json>>(
    () => ({
      ...(SAMPLE_GLOBAL as Record<string, Json>),
      ...((doc?.state ?? {}) as Record<string, Json>),
      env: { environment: 'dev', platform: 'android', appVersion: '1.0.0' },
      args: { id: 't1', title: 'Sample item', amount: -1290 },
    }),
    [doc?.state],
  );

  if (!doc) {
    return (
      <div className="canvas-empty">
        <h2>No screen open</h2>
        <p>Pick a screen on the left, or create one, to start building.</p>
      </div>
    );
  }

  const background = resolveColor(doc.background, theme) ?? theme.scheme.background;

  const onRootDrop = (event: DragEvent) => {
    event.preventDefault();
    const payload = event.dataTransfer.getData('application/sdui');
    if (!payload) return;
    const parsed = JSON.parse(payload) as { template?: SduiNode };
    if (parsed.template) addNode([], doc.root.children?.length ?? 0, parsed.template);
  };

  return (
    <div className="canvas">
      <div className="canvas-toolbar">
        <div className="segmented">
          {DEVICES.map((d) => (
            <button
              key={d.id}
              className={d.id === deviceId ? 'active' : ''}
              onClick={() => setDeviceId(d.id)}
            >
              {d.label}
            </button>
          ))}
        </div>
        <div className="segmented">
          <button className={!dark ? 'active' : ''} onClick={() => setDark(false)}>Light</button>
          <button className={dark ? 'active' : ''} onClick={() => setDark(true)}>Dark</button>
        </div>
        <span className="canvas-hint">
          Drag from the palette · click to select · drag a node to move it
        </span>
      </div>

      <div className="canvas-stage" onClick={() => select(null)}>
        <div
          className="device"
          style={{ width: device.width, height: device.height, background }}
        >
          {doc.appBar && (
            <div
              className="device-appbar"
              style={{
                background: resolveColor(doc.appBar.background, theme) ?? background,
                color: theme.scheme.onSurface,
                justifyContent: doc.appBar.centered ? 'center' : 'flex-start',
              }}
            >
              {doc.appBar.showBack !== false && <span className="device-back">←</span>}
              <div>
                <div className="device-title">
                  {String(interpolate(doc.appBar.title ?? '', scope) ?? '')}
                </div>
                {doc.appBar.subtitle && (
                  <div className="device-subtitle">
                    {String(interpolate(doc.appBar.subtitle, scope) ?? '')}
                  </div>
                )}
              </div>
            </div>
          )}

          <div
            className="device-body"
            style={{ color: theme.scheme.onSurface, overflowY: doc.scrollable ? 'auto' : 'hidden' }}
            onDragOver={(event) => event.preventDefault()}
            onDrop={onRootDrop}
          >
            <NodePreview
              node={doc.root}
              path={[]}
              ctx={{ theme, scope, selected, interactive: false }}
            />
          </div>
        </div>
      </div>
    </div>
  );
}

/** Stand-in session values so bindings render meaningfully in the builder. */
const SAMPLE_GLOBAL = {
  user: {
    name: 'Ada Lovelace',
    firstName: 'Ada',
    email: 'ada@example.com',
    isPremium: true,
    avatar: 'https://i.pravatar.cc/160?img=47',
  },
  wallet: { balance: 42150.75, currency: 'INR' },
};
