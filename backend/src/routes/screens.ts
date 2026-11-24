import { Router } from 'express';
import { db, ensureProject, nextVersion, now } from '../db.js';
import { previewHub } from '../sse.js';
import { assignVariant, isFlagEnabled } from '../targeting.js';
import { parseEnvironment, type FlagRow, type ScreenEnvelope, type TargetingContext, type VariantSpec } from '../types.js';
import { createHash } from 'node:crypto';

export const screensRouter: Router = Router({ mergeParams: true });

interface VersionRow {
  id: number;
  version: string;
  definition: string;
  status: string;
  author: string | null;
  message: string | null;
  created_at: number;
}

function etagOf(definition: string): string {
  return createHash('sha1').update(definition).digest('hex').slice(0, 16);
}

function targetingFrom(query: Record<string, unknown>): TargetingContext {
  return {
    userId: query.userId ? String(query.userId) : undefined,
    country: query.country ? String(query.country) : undefined,
    appVersion: query.appVersion ? String(query.appVersion) : undefined,
    premium: String(query.premium ?? '') === 'true',
  };
}

function flagsFor(projectId: string, environment: string, context: TargetingContext): Record<string, boolean> {
  const rows = db
    .prepare('SELECT * FROM flags WHERE project_id = ? AND environment = ?')
    .all(projectId, environment) as unknown as FlagRow[];
  return Object.fromEntries(rows.map((row) => [row.key, isFlagEnabled(row, context)]));
}

// ---------------------------------------------------------------------------
// Authoring surface (dashboard)
// ---------------------------------------------------------------------------

/** Lists screens with their per-environment deployment state. */
screensRouter.get('/', (req, res) => {
  const { projectId } = req.params as { projectId: string };
  const screens = db
    .prepare('SELECT * FROM screens WHERE project_id = ? ORDER BY updated_at DESC')
    .all(projectId) as unknown as Array<Record<string, unknown>>;

  const deployments = db
    .prepare(
      `SELECT d.screen_id, d.environment, v.version, v.id AS version_id, d.deployed_at
       FROM deployments d JOIN versions v ON v.id = d.version_id
       WHERE d.project_id = ?`,
    )
    .all(projectId) as unknown as Array<Record<string, unknown>>;

  const latestDrafts = db
    .prepare(
      `SELECT screen_id, MAX(created_at) AS updated_at, COUNT(*) AS versions
       FROM versions WHERE project_id = ? GROUP BY screen_id`,
    )
    .all(projectId) as unknown as Array<Record<string, unknown>>;

  res.json({
    screens: screens.map((screen) => ({
      ...screen,
      versions: latestDrafts.find((d) => d.screen_id === screen.screen_id)?.versions ?? 0,
      deployments: deployments
        .filter((d) => d.screen_id === screen.screen_id)
        .map(({ environment, version, version_id, deployed_at }) => ({
          environment,
          version,
          versionId: version_id,
          deployedAt: deployed_at,
        })),
    })),
  });
});

/** Creates a screen with its first draft. */
screensRouter.post('/', (req, res) => {
  const { projectId } = req.params as { projectId: string };
  const { screenId, name, definition, author } = req.body ?? {};

  if (!screenId || typeof screenId !== 'string') {
    return res.status(400).json({ error: "Body must include a string 'screenId'" });
  }

  ensureProject(projectId);
  const timestamp = now();
  const seed = definition ?? {
    id: screenId,
    version: '1',
    title: name ?? screenId,
    root: { type: 'column', modifier: { padding: 16, width: 'match_parent' }, children: [] },
  };

  db.prepare(
    `INSERT OR REPLACE INTO screens (project_id, screen_id, name, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?)`,
  ).run(projectId, screenId, name ?? screenId, timestamp, timestamp);

  const version = nextVersion(projectId, screenId);
  const result = db
    .prepare(
      `INSERT INTO versions (project_id, screen_id, version, definition, status, author, message, created_at)
       VALUES (?, ?, ?, ?, 'draft', ?, 'Created', ?)`,
    )
    .run(projectId, screenId, version, JSON.stringify(seed), author ?? 'dashboard', timestamp);

  res.status(201).json({ screenId, versionId: Number(result.lastInsertRowid), version });
});

/** Saves a new draft version. Never mutates an existing row — history is append-only. */
screensRouter.put('/:screenId/draft', (req, res) => {
  const { projectId, screenId } = req.params as { projectId: string; screenId: string };
  const { definition, author, message } = req.body ?? {};

  if (!definition || typeof definition !== 'object') {
    return res.status(400).json({ error: "Body must include a 'definition' object" });
  }

  const timestamp = now();
  const version = nextVersion(projectId, screenId);
  const result = db
    .prepare(
      `INSERT INTO versions (project_id, screen_id, version, definition, status, author, message, created_at)
       VALUES (?, ?, ?, ?, 'draft', ?, ?, ?)`,
    )
    .run(
      projectId,
      screenId,
      version,
      JSON.stringify(definition),
      author ?? 'dashboard',
      message ?? 'Draft saved',
      timestamp,
    );

  db.prepare('UPDATE screens SET updated_at = ? WHERE project_id = ? AND screen_id = ?')
    .run(timestamp, projectId, screenId);

  res.json({ versionId: Number(result.lastInsertRowid), version, status: 'draft' });
});

/** Full version history for the timeline in the dashboard. */
screensRouter.get('/:screenId/versions', (req, res) => {
  const { projectId, screenId } = req.params as { projectId: string; screenId: string };
  const rows = db
    .prepare(
      `SELECT id, version, status, author, message, created_at
       FROM versions WHERE project_id = ? AND screen_id = ? ORDER BY created_at DESC LIMIT 100`,
    )
    .all(projectId, screenId) as unknown as VersionRow[];

  const deployed = db
    .prepare('SELECT environment, version_id FROM deployments WHERE project_id = ? AND screen_id = ?')
    .all(projectId, screenId) as unknown as Array<{ environment: string; version_id: number }>;

  res.json({
    versions: rows.map((row) => ({
      ...row,
      deployedTo: deployed.filter((d) => d.version_id === row.id).map((d) => d.environment),
    })),
  });
});

/** Returns one specific version's definition (used by the editor and by preview). */
screensRouter.get('/:screenId/versions/:versionId', (req, res) => {
  const { projectId, screenId, versionId } = req.params as { projectId: string; screenId: string; versionId: string };
  const row = db
    .prepare('SELECT * FROM versions WHERE project_id = ? AND screen_id = ? AND id = ?')
    .get(projectId, screenId, Number(versionId)) as unknown as VersionRow | undefined;

  if (!row) return res.status(404).json({ error: 'No such version' });
  res.json({ ...row, definition: JSON.parse(row.definition) });
});

/**
 * Publishes a version to an environment and notifies connected devices.
 *
 * Publishing is a pointer write plus a broadcast — the definition itself is never
 * copied, so promoting dev -> prod and rolling back are the same operation.
 */
screensRouter.post('/:screenId/publish', (req, res) => {
  const { projectId, screenId } = req.params as { projectId: string; screenId: string };
  const environment = parseEnvironment(req.body?.environment ?? req.query.env);
  const { versionId, author } = req.body ?? {};

  const target = versionId
    ? (db
        .prepare('SELECT * FROM versions WHERE project_id = ? AND screen_id = ? AND id = ?')
        .get(projectId, screenId, Number(versionId)) as unknown as VersionRow | undefined)
    : (db
        .prepare(
          `SELECT * FROM versions WHERE project_id = ? AND screen_id = ?
           ORDER BY created_at DESC LIMIT 1`,
        )
        .get(projectId, screenId) as unknown as VersionRow | undefined);

  if (!target) return res.status(404).json({ error: 'Nothing to publish' });

  const timestamp = now();
  db.prepare(
    `INSERT INTO deployments (project_id, screen_id, environment, version_id, deployed_at, deployed_by)
     VALUES (?, ?, ?, ?, ?, ?)
     ON CONFLICT (project_id, screen_id, environment)
     DO UPDATE SET version_id = excluded.version_id,
                   deployed_at = excluded.deployed_at,
                   deployed_by = excluded.deployed_by`,
  ).run(projectId, screenId, environment, target.id, timestamp, author ?? 'dashboard');

  db.prepare("UPDATE versions SET status = 'published' WHERE id = ?").run(target.id);

  const definition = JSON.parse(target.definition);
  const delivered = previewHub.broadcast(projectId, environment, 'screen.published', {
    screenId,
    version: target.version,
    screen: definition,
  });

  res.json({
    screenId,
    environment,
    versionId: target.id,
    version: target.version,
    devicesNotified: delivered,
  });
});

/** Rollback = publish an older version. Recorded as a new deployment for auditability. */
screensRouter.post('/:screenId/rollback', (req, res) => {
  const { projectId, screenId } = req.params as { projectId: string; screenId: string };
  const environment = parseEnvironment(req.body?.environment ?? req.query.env);
  const { versionId } = req.body ?? {};

  let target: VersionRow | undefined;
  if (versionId) {
    target = db
      .prepare('SELECT * FROM versions WHERE project_id = ? AND screen_id = ? AND id = ?')
      .get(projectId, screenId, Number(versionId)) as unknown as VersionRow | undefined;
  } else {
    // No explicit target: step back to the newest published version that is not
    // the one currently deployed.
    const current = db
      .prepare('SELECT version_id FROM deployments WHERE project_id = ? AND screen_id = ? AND environment = ?')
      .get(projectId, screenId, environment) as unknown as { version_id: number } | undefined;
    target = db
      .prepare(
        `SELECT * FROM versions
         WHERE project_id = ? AND screen_id = ? AND id != ? AND status = 'published'
         ORDER BY created_at DESC LIMIT 1`,
      )
      .get(projectId, screenId, current?.version_id ?? -1) as unknown as VersionRow | undefined;
  }

  if (!target) {
    // Distinguish "you named a version that does not exist" from "there is nothing
    // older to fall back to" — they need different fixes.
    return res.status(404).json({
      error: versionId
        ? `Version ${versionId} does not belong to screen '${screenId}'`
        : 'No earlier published version to roll back to',
    });
  }

  const timestamp = now();
  db.prepare(
    `INSERT INTO deployments (project_id, screen_id, environment, version_id, deployed_at, deployed_by)
     VALUES (?, ?, ?, ?, ?, 'rollback')
     ON CONFLICT (project_id, screen_id, environment)
     DO UPDATE SET version_id = excluded.version_id, deployed_at = excluded.deployed_at,
                   deployed_by = 'rollback'`,
  ).run(projectId, screenId, environment, target.id, timestamp);

  previewHub.broadcast(projectId, environment, 'screen.published', {
    screenId,
    version: target.version,
    screen: JSON.parse(target.definition),
  });

  res.json({ screenId, environment, rolledBackTo: target.version, versionId: target.id });
});

screensRouter.delete('/:screenId', (req, res) => {
  const { projectId, screenId } = req.params as { projectId: string; screenId: string };
  db.prepare('DELETE FROM screens WHERE project_id = ? AND screen_id = ?').run(projectId, screenId);
  db.prepare('DELETE FROM versions WHERE project_id = ? AND screen_id = ?').run(projectId, screenId);
  res.status(204).end();
});

// ---------------------------------------------------------------------------
// Delivery surface (Android SDK)
// ---------------------------------------------------------------------------

/**
 * Serves the screen a device should render right now.
 *
 * Resolution order: an explicit `?version=` (dashboard preview) beats an active
 * A/B assignment, which beats the environment's published deployment. Flags are
 * evaluated per request and travel in the envelope so the client never needs a
 * second round trip to know what it may show.
 */
screensRouter.get('/:screenId', (req, res) => {
  const { projectId, screenId } = req.params as { projectId: string; screenId: string };
  const environment = parseEnvironment(req.query.env);
  const context = targetingFrom(req.query as Record<string, unknown>);

  let row: VersionRow | undefined;
  let experiment: ScreenEnvelope['experiment'];

  if (req.query.version) {
    row = db
      .prepare(
        `SELECT * FROM versions WHERE project_id = ? AND screen_id = ? AND (version = ? OR id = ?)
         ORDER BY created_at DESC LIMIT 1`,
      )
      .get(projectId, screenId, String(req.query.version), Number(req.query.version) || -1) as
      | unknown as VersionRow | undefined;
  }

  if (!row) {
    const active = db
      .prepare(
        `SELECT id, variants FROM experiments
         WHERE project_id = ? AND screen_id = ? AND environment = ? AND active = 1 LIMIT 1`,
      )
      .get(projectId, screenId, environment) as unknown as
      | { id: string; variants: string }
      | undefined;

    if (active) {
      const variants = JSON.parse(active.variants) as VariantSpec[];
      const chosen = assignVariant(active.id, variants, context);
      if (chosen) {
        row = db.prepare('SELECT * FROM versions WHERE id = ?').get(chosen.versionId) as
          | unknown as VersionRow | undefined;
        if (row) experiment = { experimentId: active.id, variant: chosen.name };
      }
    }
  }

  if (!row) {
    row = db
      .prepare(
        `SELECT v.* FROM deployments d JOIN versions v ON v.id = d.version_id
         WHERE d.project_id = ? AND d.screen_id = ? AND d.environment = ?`,
      )
      .get(projectId, screenId, environment) as unknown as VersionRow | undefined;
  }

  // Nothing published yet on this environment: fall back to the newest draft so
  // that `dev` is usable the moment a screen is created.
  if (!row && environment === 'dev') {
    row = db
      .prepare(
        `SELECT * FROM versions WHERE project_id = ? AND screen_id = ?
         ORDER BY created_at DESC LIMIT 1`,
      )
      .get(projectId, screenId) as unknown as VersionRow | undefined;
  }

  if (!row) {
    return res.status(404).json({ error: `No screen '${screenId}' published on '${environment}'` });
  }

  const etag = etagOf(row.definition);
  if (req.headers['if-none-match'] === etag) return res.status(304).end();

  const envelope: ScreenEnvelope = {
    screen: JSON.parse(row.definition),
    etag,
    ttlSeconds: environment === 'prod' ? 900 : 30,
    experiment,
    flags: flagsFor(projectId, environment, context),
  };

  res.set('ETag', etag);
  res.json(envelope);
});
