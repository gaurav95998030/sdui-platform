import { useEffect, useState } from 'react';
import { Canvas } from './Canvas';
import { Inspector } from './panels/Inspector';
import { Palette } from './panels/Palette';
import { TreePanel } from './panels/TreePanel';
import { AnalyticsPanel, FlagsPanel, JsonPanel, ThemePanel, VersionsPanel } from './panels/SidePanels';
import { useBuilder, type Panel } from './store';
import type { Environment } from './types';

const ENVIRONMENTS: Environment[] = ['dev', 'qa', 'stage', 'prod'];

const PANELS: Array<{ id: Panel; label: string }> = [
  { id: 'inspector', label: 'Inspector' },
  { id: 'json', label: 'JSON' },
  { id: 'versions', label: 'Versions' },
  { id: 'theme', label: 'Theme' },
  { id: 'flags', label: 'Flags' },
  { id: 'analytics', label: 'Analytics' },
];

export function App() {
  const {
    screens, screenId, doc, dirty, panel, environment, devices, status, error,
    loadScreens, openScreen, createScreen, deleteScreen, setEnvironment,
    save, publish, setPanel, undo, redo, refreshDevices, loadThemes,
  } = useBuilder();

  const [showNew, setShowNew] = useState(false);

  useEffect(() => {
    void loadScreens();
    void loadThemes();
  }, [loadScreens, loadThemes]);

  // `?screen=home&env=dev` makes a builder session shareable: a reviewer opens the
  // exact screen and environment the author was looking at.
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const env = params.get('env') as Environment | null;
    const requested = params.get('screen');
    if (env && ENVIRONMENTS.includes(env) && env !== environment) {
      void setEnvironment(env);
    }
    if (requested && requested !== screenId) void openScreen(requested);
    // Intentionally runs once: later changes are driven by the effect below.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    if (screenId) params.set('screen', screenId);
    else params.delete('screen');
    params.set('env', environment);
    window.history.replaceState(null, '', `?${params.toString()}`);
  }, [screenId, environment]);

  // Poll the device count so the "N devices" badge reflects reality without the
  // dashboard needing its own socket.
  useEffect(() => {
    void refreshDevices();
    const timer = setInterval(() => void refreshDevices(), 4000);
    return () => clearInterval(timer);
  }, [refreshDevices, environment]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const meta = event.metaKey || event.ctrlKey;
      if (!meta) return;
      if (event.key === 's') {
        event.preventDefault();
        void save();
      } else if (event.key === 'z' && !event.shiftKey) {
        event.preventDefault();
        undo();
      } else if ((event.key === 'z' && event.shiftKey) || event.key === 'y') {
        event.preventDefault();
        redo();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [save, undo, redo]);

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark">SD</span>
          <div>
            <div className="brand-title">SDUI Studio</div>
            <div className="brand-sub">demo project</div>
          </div>
        </div>

        <div className="topbar-center">
          <div className="segmented">
            {ENVIRONMENTS.map((env) => (
              <button
                key={env}
                className={env === environment ? 'active' : ''}
                onClick={() => void setEnvironment(env)}
              >
                {env}
              </button>
            ))}
          </div>
          <span className={`devices${devices > 0 ? ' online' : ''}`}>
            ● {devices} device{devices === 1 ? '' : 's'} live
          </span>
        </div>

        <div className="topbar-actions">
          <button onClick={undo} title="Undo (⌘Z)">↶</button>
          <button onClick={redo} title="Redo (⇧⌘Z)">↷</button>
          <button disabled={!doc} onClick={() => void save()}>
            {dirty ? 'Save draft •' : 'Save draft'}
          </button>
          <button className="primary" disabled={!doc} onClick={() => void publish()}>
            Publish to {environment}
          </button>
        </div>
      </header>

      {(status || error) && (
        <div className={`banner ${error ? 'error' : 'ok'}`}>{error ?? status}</div>
      )}

      <div className="workspace">
        <aside className="rail">
          <div className="panel screens">
            <div className="panel-header">
              <h3>Screens</h3>
              <button onClick={() => setShowNew(true)}>+</button>
            </div>
            {showNew && (
              <NewScreenForm
                onCancel={() => setShowNew(false)}
                onCreate={(id, name) => {
                  void createScreen(id, name);
                  setShowNew(false);
                }}
              />
            )}
            <div className="list-scroll">
              {screens.map((screen) => (
                <div
                  key={screen.screen_id}
                  className={`screen-row${screen.screen_id === screenId ? ' active' : ''}`}
                  onClick={() => void openScreen(screen.screen_id)}
                >
                  <div>
                    <div className="screen-name">{screen.name}</div>
                    <div className="muted small">
                      {screen.screen_id} · {screen.versions} version{screen.versions === 1 ? '' : 's'}
                    </div>
                    <div className="chips">
                      {screen.deployments.map((deployment) => (
                        <span key={deployment.environment} className="chip env">
                          {deployment.environment} v{deployment.version}
                        </span>
                      ))}
                    </div>
                  </div>
                  <button
                    className="danger-link"
                    onClick={(event) => {
                      event.stopPropagation();
                      if (confirm(`Delete ${screen.screen_id}? This removes its history.`)) {
                        void deleteScreen(screen.screen_id);
                      }
                    }}
                  >
                    ✕
                  </button>
                </div>
              ))}
            </div>
          </div>
          <Palette />
        </aside>

        <main className="stage">
          <Canvas />
        </main>

        <aside className="right">
          <TreePanel />
          <div className="panel-switch">
            {PANELS.map((entry) => (
              <button
                key={entry.id}
                className={panel === entry.id ? 'active' : ''}
                onClick={() => setPanel(entry.id)}
              >
                {entry.label}
              </button>
            ))}
          </div>
          {panel === 'inspector' && <Inspector />}
          {panel === 'json' && <JsonPanel />}
          {panel === 'versions' && <VersionsPanel />}
          {panel === 'theme' && <ThemePanel />}
          {panel === 'flags' && <FlagsPanel />}
          {panel === 'analytics' && <AnalyticsPanel />}
        </aside>
      </div>
    </div>
  );
}

function NewScreenForm({
  onCreate,
  onCancel,
}: {
  onCreate: (id: string, name: string) => void;
  onCancel: () => void;
}) {
  const [id, setId] = useState('');
  const [name, setName] = useState('');
  return (
    <form
      className="new-screen"
      onSubmit={(event) => {
        event.preventDefault();
        const slug = id.trim().replace(/\s+/g, '-').toLowerCase();
        if (slug) onCreate(slug, name.trim() || slug);
      }}
    >
      <input autoFocus placeholder="screen id (e.g. checkout)" value={id} onChange={(e) => setId(e.target.value)} />
      <input placeholder="display name" value={name} onChange={(e) => setName(e.target.value)} />
      <div className="inline">
        <button type="submit" className="primary">Create</button>
        <button type="button" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  );
}
