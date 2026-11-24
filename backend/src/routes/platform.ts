import { Router } from 'express';
import { db, ensureProject, now } from '../db.js';
import { previewHub } from '../sse.js';
import { isFlagEnabled } from '../targeting.js';
import { parseEnvironment, type FlagRow } from '../types.js';

export const platformRouter: Router = Router({ mergeParams: true });

// ---------------------------------------------------------------------------
// Themes
// ---------------------------------------------------------------------------

platformRouter.get('/themes', (req, res) => {
  const { projectId } = req.params as { projectId: string };
  const environment = parseEnvironment(req.query.env);
  const rows = db
    .prepare('SELECT theme_id, definition, updated_at FROM themes WHERE project_id = ? AND environment = ?')
    .all(projectId, environment) as unknown as Array<{ theme_id: string; definition: string; updated_at: number }>;
  res.json({
    themes: rows.map((row) => ({
      id: row.theme_id,
      updatedAt: row.updated_at,
      definition: JSON.parse(row.definition),
    })),
  });
});

platformRouter.get('/themes/:themeId', (req, res) => {
  const { projectId, themeId } = req.params as { projectId: string; themeId: string };
  const environment = parseEnvironment(req.query.env);
  const row = db
    .prepare('SELECT definition FROM themes WHERE project_id = ? AND theme_id = ? AND environment = ?')
    .get(projectId, themeId, environment) as unknown as { definition: string } | undefined;

  if (!row) return res.status(404).json({ error: `No theme '${themeId}' on '${environment}'` });
  res.type('application/json').send(row.definition);
});

/** Saving a theme immediately restyles every connected device on that environment. */
platformRouter.put('/themes/:themeId', (req, res) => {
  const { projectId, themeId } = req.params as { projectId: string; themeId: string };
  const environment = parseEnvironment(req.body?.environment ?? req.query.env);
  const definition = req.body?.definition ?? req.body;

  if (!definition || typeof definition !== 'object') {
    return res.status(400).json({ error: 'Body must be a theme object' });
  }

  ensureProject(projectId);
  db.prepare(
    `INSERT INTO themes (project_id, theme_id, environment, definition, updated_at)
     VALUES (?, ?, ?, ?, ?)
     ON CONFLICT (project_id, theme_id, environment)
     DO UPDATE SET definition = excluded.definition, updated_at = excluded.updated_at`,
  ).run(projectId, themeId, environment, JSON.stringify(definition), now());

  const delivered = previewHub.broadcast(projectId, environment, 'theme.updated', {
    themeId,
    theme: definition,
  });
  res.json({ themeId, environment, devicesNotified: delivered });
});

// ---------------------------------------------------------------------------
// Feature flags
// ---------------------------------------------------------------------------

platformRouter.get('/flags', (req, res) => {
  const { projectId } = req.params as { projectId: string };
  const environment = parseEnvironment(req.query.env);
  const rows = db
    .prepare('SELECT * FROM flags WHERE project_id = ? AND environment = ? ORDER BY key')
    .all(projectId, environment) as unknown as FlagRow[];
  res.json({ flags: rows });
});

/** Evaluates every flag for a concrete user — the dashboard's "test targeting" tool. */
platformRouter.get('/flags/evaluate', (req, res) => {
  const { projectId } = req.params as { projectId: string };
  const environment = parseEnvironment(req.query.env);
  const context = {
    userId: req.query.userId ? String(req.query.userId) : undefined,
    country: req.query.country ? String(req.query.country) : undefined,
    appVersion: req.query.appVersion ? String(req.query.appVersion) : undefined,
    premium: String(req.query.premium ?? '') === 'true',
  };
  const rows = db
    .prepare('SELECT * FROM flags WHERE project_id = ? AND environment = ?')
    .all(projectId, environment) as unknown as FlagRow[];

  res.json({
    context,
    results: rows.map((row) => ({ key: row.key, enabled: isFlagEnabled(row, context) })),
  });
});

platformRouter.put('/flags/:key', (req, res) => {
  const { projectId, key } = req.params as { projectId: string; key: string };
  const environment = parseEnvironment(req.body?.environment ?? req.query.env);
  const {
    enabled = false,
    rolloutPercent = 100,
    countries = null,
    minAppVersion = null,
    premiumOnly = false,
    userIds = null,
  } = req.body ?? {};

  ensureProject(projectId);
  db.prepare(
    `INSERT INTO flags (project_id, key, environment, enabled, rollout_percent, countries,
                        min_app_version, premium_only, user_ids, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
     ON CONFLICT (project_id, key, environment) DO UPDATE SET
       enabled = excluded.enabled, rollout_percent = excluded.rollout_percent,
       countries = excluded.countries, min_app_version = excluded.min_app_version,
       premium_only = excluded.premium_only, user_ids = excluded.user_ids,
       updated_at = excluded.updated_at`,
  ).run(
    projectId,
    key,
    environment,
    enabled ? 1 : 0,
    Number(rolloutPercent),
    countries ? String(countries) : null,
    minAppVersion ? String(minAppVersion) : null,
    premiumOnly ? 1 : 0,
    userIds ? String(userIds) : null,
    now(),
  );

  previewHub.broadcast(projectId, environment, 'flags.updated', { key });
  res.json({ key, environment, enabled: Boolean(enabled) });
});

platformRouter.delete('/flags/:key', (req, res) => {
  const { projectId, key } = req.params as { projectId: string; key: string };
  const environment = parseEnvironment(req.query.env);
  db.prepare('DELETE FROM flags WHERE project_id = ? AND key = ? AND environment = ?')
    .run(projectId, key, environment);
  res.status(204).end();
});

// ---------------------------------------------------------------------------
// Experiments (A/B)
// ---------------------------------------------------------------------------

platformRouter.get('/experiments', (req, res) => {
  const { projectId } = req.params as { projectId: string };
  const environment = parseEnvironment(req.query.env);
  const rows = db
    .prepare('SELECT * FROM experiments WHERE project_id = ? AND environment = ?')
    .all(projectId, environment) as unknown as Array<Record<string, unknown>>;
  res.json({
    experiments: rows.map((row) => ({ ...row, variants: JSON.parse(String(row.variants)) })),
  });
});

platformRouter.put('/experiments/:id', (req, res) => {
  const { projectId, id } = req.params as { projectId: string; id: string };
  const environment = parseEnvironment(req.body?.environment ?? req.query.env);
  const { screenId, variants, metric, active = true } = req.body ?? {};

  if (!screenId || !Array.isArray(variants) || variants.length < 2) {
    return res.status(400).json({ error: "Provide 'screenId' and at least two variants" });
  }

  ensureProject(projectId);
  db.prepare(
    `INSERT INTO experiments (project_id, id, screen_id, environment, variants, metric, active, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?)
     ON CONFLICT (project_id, id, environment) DO UPDATE SET
       screen_id = excluded.screen_id, variants = excluded.variants,
       metric = excluded.metric, active = excluded.active`,
  ).run(
    projectId,
    id,
    screenId,
    environment,
    JSON.stringify(variants),
    metric ?? null,
    active ? 1 : 0,
    now(),
  );

  res.json({ id, screenId, environment, variants });
});

/** Per-variant event counts — the raw numbers behind the experiment dashboard. */
platformRouter.get('/experiments/:id/metrics', (req, res) => {
  const { projectId, id } = req.params as { projectId: string; id: string };
  const rows = db
    .prepare(
      `SELECT name, COUNT(*) AS count FROM analytics_events
       WHERE project_id = ? AND properties LIKE ?
       GROUP BY name ORDER BY count DESC`,
    )
    .all(projectId, `%"${id}"%`) as unknown as Array<{ name: string; count: number }>;
  res.json({ experimentId: id, events: rows });
});

// ---------------------------------------------------------------------------
// Analytics ingest
// ---------------------------------------------------------------------------

platformRouter.post('/analytics', (req, res) => {
  const { projectId } = req.params as { projectId: string };
  const events = Array.isArray(req.body?.events) ? req.body.events : [req.body];
  const insert = db.prepare(
    'INSERT INTO analytics_events (project_id, name, properties, received_at) VALUES (?, ?, ?, ?)',
  );
  let stored = 0;
  for (const event of events) {
    if (!event?.name) continue;
    insert.run(projectId, String(event.name), JSON.stringify(event.properties ?? {}), now());
    stored++;
  }
  res.status(202).json({ stored });
});

platformRouter.get('/analytics/summary', (req, res) => {
  const { projectId } = req.params as { projectId: string };
  const since = Number(req.query.since ?? 0);
  const rows = db
    .prepare(
      `SELECT name, COUNT(*) AS count FROM analytics_events
       WHERE project_id = ? AND received_at >= ?
       GROUP BY name ORDER BY count DESC LIMIT 50`,
    )
    .all(projectId, since) as unknown as Array<{ name: string; count: number }>;

  const recent = db
    .prepare(
      `SELECT name, properties, received_at FROM analytics_events
       WHERE project_id = ? ORDER BY received_at DESC LIMIT 25`,
    )
    .all(projectId) as unknown as Array<Record<string, unknown>>;

  res.json({
    totals: rows,
    recent: recent.map((row) => ({ ...row, properties: JSON.parse(String(row.properties ?? '{}')) })),
  });
});

// ---------------------------------------------------------------------------
// Live preview transport
// ---------------------------------------------------------------------------

/** SSE stream the Android SDK subscribes to. */
platformRouter.get('/preview/stream', (req, res) => {
  const { projectId } = req.params as { projectId: string };
  const environment = parseEnvironment(req.query.env);
  const device = req.query.device ? String(req.query.device) : undefined;

  res.status(200).set({
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache, no-transform',
    Connection: 'keep-alive',
    'X-Accel-Buffering': 'no',
  });
  res.flushHeaders?.();

  const unsubscribe = previewHub.subscribe(projectId, environment, device, res);
  req.on('close', unsubscribe);
});

/**
 * Pushes an unsaved draft straight to devices.
 *
 * This is what makes the builder feel live: the canvas sends the working tree on
 * every edit (debounced), the phone re-renders, and nothing is committed to
 * history until the author actually saves.
 */
platformRouter.post('/preview/push', (req, res) => {
  const { projectId } = req.params as { projectId: string };
  const environment = parseEnvironment(req.body?.environment ?? req.query.env);
  const { screenId, definition, device } = req.body ?? {};

  if (!screenId || !definition) {
    return res.status(400).json({ error: "Body must include 'screenId' and 'definition'" });
  }

  const delivered = previewHub.broadcast(
    projectId,
    environment,
    'screen.published',
    { screenId, version: 'preview', screen: definition },
    device,
  );
  res.json({ screenId, environment, devicesNotified: delivered });
});

platformRouter.get('/preview/devices', (_req, res) => {
  res.json(previewHub.stats());
});
