import { DatabaseSync } from 'node:sqlite';
import { mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

/**
 * Storage layer.
 *
 * SQLite via Node's built-in driver: no native module to compile, and the whole
 * registry is one portable file. The schema is deliberately Postgres-shaped
 * (surrogate keys, no JSON columns beyond payload blobs) so moving to a managed
 * database later is a driver swap rather than a redesign.
 */

const DB_PATH = resolve(process.env.SDUI_DB ?? 'data/sdui.db');
mkdirSync(dirname(DB_PATH), { recursive: true });

export const db = new DatabaseSync(DB_PATH);

db.exec('PRAGMA journal_mode = WAL');
db.exec('PRAGMA foreign_keys = ON');

db.exec(`
CREATE TABLE IF NOT EXISTS projects (
  id          TEXT PRIMARY KEY,
  name        TEXT NOT NULL,
  api_key     TEXT,
  created_at  INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS screens (
  project_id  TEXT NOT NULL,
  screen_id   TEXT NOT NULL,
  name        TEXT NOT NULL,
  description TEXT,
  created_at  INTEGER NOT NULL,
  updated_at  INTEGER NOT NULL,
  PRIMARY KEY (project_id, screen_id),
  FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

-- Every save creates an immutable row. Publishing points an environment at one of
-- them, which is what makes rollback a pointer move rather than a data migration.
CREATE TABLE IF NOT EXISTS versions (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  project_id  TEXT NOT NULL,
  screen_id   TEXT NOT NULL,
  version     TEXT NOT NULL,
  definition  TEXT NOT NULL,
  status      TEXT NOT NULL DEFAULT 'draft',
  author      TEXT,
  message     TEXT,
  created_at  INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_versions_screen
  ON versions (project_id, screen_id, created_at DESC);

CREATE TABLE IF NOT EXISTS deployments (
  project_id  TEXT NOT NULL,
  screen_id   TEXT NOT NULL,
  environment TEXT NOT NULL,
  version_id  INTEGER NOT NULL,
  deployed_at INTEGER NOT NULL,
  deployed_by TEXT,
  PRIMARY KEY (project_id, screen_id, environment),
  FOREIGN KEY (version_id) REFERENCES versions(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS themes (
  project_id  TEXT NOT NULL,
  theme_id    TEXT NOT NULL,
  environment TEXT NOT NULL DEFAULT 'prod',
  definition  TEXT NOT NULL,
  updated_at  INTEGER NOT NULL,
  PRIMARY KEY (project_id, theme_id, environment)
);

CREATE TABLE IF NOT EXISTS flags (
  project_id      TEXT NOT NULL,
  key             TEXT NOT NULL,
  environment     TEXT NOT NULL DEFAULT 'prod',
  enabled         INTEGER NOT NULL DEFAULT 0,
  rollout_percent INTEGER NOT NULL DEFAULT 100,
  countries       TEXT,
  min_app_version TEXT,
  premium_only    INTEGER NOT NULL DEFAULT 0,
  user_ids        TEXT,
  updated_at      INTEGER NOT NULL,
  PRIMARY KEY (project_id, key, environment)
);

CREATE TABLE IF NOT EXISTS experiments (
  project_id  TEXT NOT NULL,
  id          TEXT NOT NULL,
  screen_id   TEXT NOT NULL,
  environment TEXT NOT NULL DEFAULT 'prod',
  variants    TEXT NOT NULL,
  metric      TEXT,
  active      INTEGER NOT NULL DEFAULT 1,
  created_at  INTEGER NOT NULL,
  PRIMARY KEY (project_id, id, environment)
);

CREATE TABLE IF NOT EXISTS analytics_events (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  project_id TEXT NOT NULL,
  name       TEXT NOT NULL,
  properties TEXT,
  received_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_events_name ON analytics_events (project_id, name, received_at DESC);
`);

export type Row = Record<string, unknown>;

export const now = (): number => Date.now();

/** Next semantic-ish version for a screen: 1, 2, 3 … kept as text for flexibility. */
export function nextVersion(projectId: string, screenId: string): string {
  const row = db
    .prepare('SELECT COUNT(*) AS n FROM versions WHERE project_id = ? AND screen_id = ?')
    .get(projectId, screenId) as { n: number } | undefined;
  return String((row?.n ?? 0) + 1);
}

export function ensureProject(id: string, name = id): void {
  db.prepare(
    'INSERT OR IGNORE INTO projects (id, name, created_at) VALUES (?, ?, ?)',
  ).run(id, name, now());
}
