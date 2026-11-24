import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { db, ensureProject, now } from './db.js';
import { ENVIRONMENTS } from './types.js';

/**
 * Populates an empty registry so the backend, dashboard and app are useful the
 * moment they start.
 *
 * The fixtures in `seed-screens/` are copies of the Android app's offline fallback
 * bundle. They are read exactly once — on a database with no screens — and never
 * again: after the first publish the registry is authoritative. Point
 * `SDUI_SEED_DIR` at the app's `assets/sdui` when working across both checkouts.
 */
const ASSET_DIR = resolve(process.env.SDUI_SEED_DIR ?? 'seed-screens');
const PROJECT_ID = process.env.SDUI_SEED_PROJECT ?? 'demo';

const DEFAULT_THEME = {
  id: 'brand',
  name: 'Demo Brand',
  mode: 'system',
  light: {
    primary: '#4C5BD4', onPrimary: '#FFFFFF', primaryContainer: '#E1E3FF',
    onPrimaryContainer: '#0A1160', secondary: '#00A97F', background: '#F7F8FC',
    surface: '#FFFFFF', surfaceVariant: '#EDEFF7', onSurfaceVariant: '#5B6076',
    error: '#D93838',
  },
  dark: {
    primary: '#B7BDFF', onPrimary: '#141B6B', primaryContainer: '#2C348F',
    onPrimaryContainer: '#E1E3FF', secondary: '#5FE0BA', background: '#101116',
    surface: '#171922', surfaceVariant: '#2A2D3A', onSurfaceVariant: '#C4C7D6',
  },
  shapes: { small: 10, medium: 16, large: 22 },
  tokens: {
    brandGold: '#E9A23B', success: '#1FA971', danger: '#D93838', muted: 'onSurfaceVariant',
  },
};

export function seedIfEmpty(): void {
  const existing = db.prepare('SELECT COUNT(*) AS n FROM screens').get() as { n: number };
  if (existing.n > 0) return;

  ensureProject(PROJECT_ID, 'Demo Project');
  const timestamp = now();

  for (const environment of ENVIRONMENTS) {
    db.prepare(
      `INSERT OR REPLACE INTO themes (project_id, theme_id, environment, definition, updated_at)
       VALUES (?, 'brand', ?, ?, ?)`,
    ).run(PROJECT_ID, environment, JSON.stringify(DEFAULT_THEME), timestamp);
  }

  if (!existsSync(ASSET_DIR)) {
    console.warn(`Seed directory ${ASSET_DIR} not found; starting with an empty registry.`);
    return;
  }

  const files = readdirSync(ASSET_DIR).filter((f) => f.endsWith('.json'));
  for (const file of files) {
    const screenId = file.replace(/\.json$/, '');
    let definition: unknown;
    try {
      definition = JSON.parse(readFileSync(join(ASSET_DIR, file), 'utf8'));
    } catch (error) {
      console.warn(`Skipping ${file}: ${(error as Error).message}`);
      continue;
    }

    db.prepare(
      `INSERT OR REPLACE INTO screens (project_id, screen_id, name, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?)`,
    ).run(PROJECT_ID, screenId, titleOf(definition, screenId), timestamp, timestamp);

    const result = db
      .prepare(
        `INSERT INTO versions (project_id, screen_id, version, definition, status, author, message, created_at)
         VALUES (?, ?, '1', ?, 'published', 'seed', 'Imported from bundled assets', ?)`,
      )
      .run(PROJECT_ID, screenId, JSON.stringify(definition), timestamp);

    // Seeded content is live on every environment so the demo works immediately.
    for (const environment of ENVIRONMENTS) {
      db.prepare(
        `INSERT OR REPLACE INTO deployments
           (project_id, screen_id, environment, version_id, deployed_at, deployed_by)
         VALUES (?, ?, ?, ?, ?, 'seed')`,
      ).run(PROJECT_ID, screenId, environment, Number(result.lastInsertRowid), timestamp);
    }
  }

  db.prepare(
    `INSERT OR REPLACE INTO flags
       (project_id, key, environment, enabled, rollout_percent, countries,
        min_app_version, premium_only, user_ids, updated_at)
     VALUES (?, 'newHomeLayout', 'dev', 1, 50, NULL, '1.0.0', 0, NULL, ?)`,
  ).run(PROJECT_ID, timestamp);

  db.prepare(
    `INSERT OR REPLACE INTO flags
       (project_id, key, environment, enabled, rollout_percent, countries,
        min_app_version, premium_only, user_ids, updated_at)
     VALUES (?, 'premiumInsights', 'dev', 1, 100, NULL, NULL, 1, NULL, ?)`,
  ).run(PROJECT_ID, timestamp);

  console.log(`Seeded project '${PROJECT_ID}' with ${files.length} screens from ${ASSET_DIR}`);
}

function titleOf(definition: unknown, fallback: string): string {
  if (definition && typeof definition === 'object' && 'title' in definition) {
    const title = (definition as { title?: unknown }).title;
    if (typeof title === 'string' && title.length > 0) return title;
  }
  return fallback;
}

// Allow `npm run seed` to force a reseed of an existing database.
if (process.argv[1]?.endsWith('seed.ts')) {
  db.exec('DELETE FROM deployments; DELETE FROM versions; DELETE FROM screens;');
  seedIfEmpty();
  console.log('Reseed complete.');
}
