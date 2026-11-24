import { useEffect, useMemo, useState } from 'react';
import { api } from '../api';
import { useBuilder } from '../store';
import { countNodes } from '../tree';
import type { SduiScreen, ThemeSpec } from '../types';

/** Raw JSON view. Edits apply live once the document parses. */
export function JsonPanel() {
  const doc = useBuilder((s) => s.doc);
  const replaceDoc = useBuilder((s) => s.replaceDoc);
  const [draft, setDraft] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const serialised = useMemo(() => JSON.stringify(doc ?? {}, null, 2), [doc]);

  if (!doc) return <div className="panel"><p className="muted pad">No screen open.</p></div>;

  return (
    <div className="panel json-panel">
      <div className="panel-header">
        <h3>Screen JSON</h3>
        <span className="muted">{countNodes(doc.root)} nodes</span>
      </div>
      {error && <div className="banner error">{error}</div>}
      <textarea
        className={`json-editor${error ? ' invalid' : ''}`}
        value={draft ?? serialised}
        spellCheck={false}
        onChange={(event) => {
          setDraft(event.target.value);
          try {
            const parsed = JSON.parse(event.target.value) as SduiScreen;
            if (!parsed.root) throw new Error("Missing required 'root' node");
            replaceDoc(parsed);
            setError(null);
          } catch (parseError) {
            setError((parseError as Error).message);
          }
        }}
        onBlur={() => setDraft(null)}
      />
      <div className="panel-footer">
        <button onClick={() => navigator.clipboard.writeText(serialised)}>Copy</button>
        <button
          onClick={() => {
            const blob = new Blob([serialised], { type: 'application/json' });
            const url = URL.createObjectURL(blob);
            const anchor = document.createElement('a');
            anchor.href = url;
            anchor.download = `${doc.id}.json`;
            anchor.click();
            URL.revokeObjectURL(url);
          }}
        >
          Download
        </button>
      </div>
    </div>
  );
}

/**
 * Version timeline.
 *
 * Every save is a row here, and every row can be loaded back into the canvas or
 * deployed to an environment — which is what makes "publish" a low-risk action.
 */
export function VersionsPanel() {
  const versions = useBuilder((s) => s.versions);
  const environment = useBuilder((s) => s.environment);
  const publish = useBuilder((s) => s.publish);
  const rollback = useBuilder((s) => s.rollback);
  const restore = useBuilder((s) => s.restoreVersion);
  const loadVersions = useBuilder((s) => s.loadVersions);

  useEffect(() => {
    void loadVersions();
  }, [loadVersions]);

  return (
    <div className="panel">
      <div className="panel-header">
        <h3>Version history</h3>
        <button onClick={() => void loadVersions()}>Refresh</button>
      </div>
      <div className="list-scroll">
        {versions.length === 0 && <p className="muted pad">No versions yet — save the screen to create one.</p>}
        {versions.map((version) => (
          <div className="version-row" key={version.id}>
            <div>
              <div className="version-title">
                v{version.version}
                <span className={`chip ${version.status}`}>{version.status}</span>
                {version.deployedTo.map((env) => (
                  <span key={env} className="chip env">{env}</span>
                ))}
              </div>
              <div className="muted small">
                {new Date(version.created_at).toLocaleString()} · {version.author ?? 'unknown'}
                {version.message ? ` · ${version.message}` : ''}
              </div>
            </div>
            <div className="version-actions">
              <button onClick={() => void restore(version.id)}>Load</button>
              <button onClick={() => void publish(environment)}>Publish</button>
              <button onClick={() => void rollback(version.id)}>Deploy this</button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

/** Theme editor. Saving pushes the design system to every connected device. */
export function ThemePanel() {
  const themes = useBuilder((s) => s.themes);
  const loadThemes = useBuilder((s) => s.loadThemes);
  const saveTheme = useBuilder((s) => s.saveTheme);
  const [draft, setDraft] = useState<ThemeSpec | null>(null);

  useEffect(() => {
    void loadThemes();
  }, [loadThemes]);

  const active = draft ?? themes[0]?.definition;
  if (!active) return <div className="panel"><p className="muted pad">No themes yet.</p></div>;

  const setRole = (mode: 'light' | 'dark', role: string, value: string) =>
    setDraft({ ...active, [mode]: { ...(active[mode] ?? {}), [role]: value } });

  const ROLES = ['primary', 'onPrimary', 'primaryContainer', 'secondary', 'background', 'surface', 'surfaceVariant', 'onSurfaceVariant', 'error'];

  return (
    <div className="panel">
      <div className="panel-header">
        <h3>Theme · {active.id}</h3>
        <button className="primary" onClick={() => void saveTheme(active)}>Save &amp; push</button>
      </div>
      <div className="list-scroll">
        {(['light', 'dark'] as const).map((mode) => (
          <div key={mode} className="theme-group">
            <div className="theme-group-title">{mode}</div>
            {ROLES.map((role) => (
              <label className="field row" key={`${mode}-${role}`}>
                <span className="field-label">{role}</span>
                <div className="inline">
                  <input
                    value={active[mode]?.[role] ?? ''}
                    placeholder="#RRGGBB"
                    onChange={(event) => setRole(mode, role, event.target.value)}
                  />
                  <input
                    type="color"
                    value={(active[mode]?.[role] ?? '#000000').slice(0, 7)}
                    onChange={(event) => setRole(mode, role, event.target.value)}
                  />
                </div>
              </label>
            ))}
          </div>
        ))}

        <div className="theme-group">
          <div className="theme-group-title">Custom tokens</div>
          {Object.entries(active.tokens ?? {}).map(([token, value]) => (
            <label className="field row" key={token}>
              <span className="field-label">{token}</span>
              <input
                value={value}
                onChange={(event) =>
                  setDraft({ ...active, tokens: { ...(active.tokens ?? {}), [token]: event.target.value } })
                }
              />
            </label>
          ))}
        </div>
      </div>
    </div>
  );
}

/** Feature flags with the same targeting rules the backend evaluates. */
export function FlagsPanel() {
  const flags = useBuilder((s) => s.flags);
  const loadFlags = useBuilder((s) => s.loadFlags);
  const saveFlag = useBuilder((s) => s.saveFlag);
  const projectId = useBuilder((s) => s.projectId);
  const environment = useBuilder((s) => s.environment);

  const [newKey, setNewKey] = useState('');
  const [probe, setProbe] = useState({ userId: 'u_1024', country: 'IN', appVersion: '1.0.0', premium: 'true' });
  const [results, setResults] = useState<Array<{ key: string; enabled: boolean }>>([]);

  useEffect(() => {
    void loadFlags();
  }, [loadFlags]);

  return (
    <div className="panel">
      <div className="panel-header">
        <h3>Feature flags · {environment}</h3>
      </div>
      <div className="list-scroll">
        <div className="inline pad">
          <input placeholder="new flag key" value={newKey} onChange={(e) => setNewKey(e.target.value)} />
          <button
            onClick={() => {
              if (!newKey) return;
              void saveFlag(newKey, { enabled: true, rolloutPercent: 100 });
              setNewKey('');
            }}
          >
            Add
          </button>
        </div>

        {flags.map((flag) => (
          <div className="flag-card" key={flag.key}>
            <div className="flag-head">
              <strong>{flag.key}</strong>
              <label className="inline">
                <input
                  type="checkbox"
                  checked={Boolean(flag.enabled)}
                  onChange={(event) =>
                    void saveFlag(flag.key, {
                      enabled: event.target.checked,
                      rolloutPercent: flag.rollout_percent,
                      countries: flag.countries,
                      minAppVersion: flag.min_app_version,
                      premiumOnly: Boolean(flag.premium_only),
                      userIds: flag.user_ids,
                    })
                  }
                />
                enabled
              </label>
            </div>
            <label className="field row">
              <span className="field-label">rollout %</span>
              <input
                type="range"
                min={0}
                max={100}
                value={flag.rollout_percent}
                onChange={(event) =>
                  void saveFlag(flag.key, {
                    enabled: Boolean(flag.enabled),
                    rolloutPercent: Number(event.target.value),
                    countries: flag.countries,
                    minAppVersion: flag.min_app_version,
                    premiumOnly: Boolean(flag.premium_only),
                    userIds: flag.user_ids,
                  })
                }
              />
              <span className="muted">{flag.rollout_percent}%</span>
            </label>
            <div className="muted small">
              {flag.countries ? `countries: ${flag.countries} · ` : ''}
              {flag.min_app_version ? `min app: ${flag.min_app_version} · ` : ''}
              {flag.premium_only ? 'premium only' : 'all users'}
            </div>
          </div>
        ))}

        <div className="flag-card">
          <div className="flag-head"><strong>Test targeting</strong></div>
          {(['userId', 'country', 'appVersion', 'premium'] as const).map((key) => (
            <label className="field row" key={key}>
              <span className="field-label">{key}</span>
              <input value={probe[key]} onChange={(e) => setProbe({ ...probe, [key]: e.target.value })} />
            </label>
          ))}
          <button
            onClick={async () => {
              const response = await api.evaluateFlags(projectId, environment, probe);
              setResults(response.results);
            }}
          >
            Evaluate
          </button>
          {results.map((result) => (
            <div key={result.key} className="muted small">
              {result.key}: <strong>{result.enabled ? 'on' : 'off'}</strong>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

/** Live counters from the events the SDK emits automatically. */
export function AnalyticsPanel() {
  const projectId = useBuilder((s) => s.projectId);
  const [data, setData] = useState<{
    totals: Array<{ name: string; count: number }>;
    recent: Array<{ name: string; properties: Record<string, unknown>; received_at: number }>;
  } | null>(null);

  const refresh = useMemo(
    () => async () => setData(await api.analytics(projectId)),
    [projectId],
  );

  useEffect(() => {
    void refresh();
    const timer = setInterval(() => void refresh(), 5000);
    return () => clearInterval(timer);
  }, [refresh]);

  const max = Math.max(1, ...(data?.totals.map((t) => t.count) ?? [1]));

  return (
    <div className="panel">
      <div className="panel-header">
        <h3>Analytics</h3>
        <button onClick={() => void refresh()}>Refresh</button>
      </div>
      <div className="list-scroll">
        {(data?.totals.length ?? 0) === 0 && (
          <p className="muted pad">
            No events yet. Run the app against this backend and interact with a screen.
          </p>
        )}
        {data?.totals.map((total) => (
          <div className="metric" key={total.name}>
            <div className="metric-label">
              <span>{total.name}</span>
              <strong>{total.count}</strong>
            </div>
            <div className="metric-bar">
              <div style={{ width: `${(total.count / max) * 100}%` }} />
            </div>
          </div>
        ))}
        {data?.recent.length ? <div className="theme-group-title pad">Recent</div> : null}
        {data?.recent.slice(0, 15).map((event, index) => (
          <div className="muted small pad-tight" key={index}>
            {new Date(event.received_at).toLocaleTimeString()} · {event.name} ·{' '}
            {JSON.stringify(event.properties).slice(0, 90)}
          </div>
        ))}
      </div>
    </div>
  );
}
