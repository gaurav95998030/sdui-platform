import express from 'express';
import cors from 'cors';
import { db, ensureProject } from './db.js';
import { screensRouter } from './routes/screens.js';
import { platformRouter } from './routes/platform.js';
import { demoRouter } from './routes/demo.js';
import { seedIfEmpty } from './seed.js';

const PORT = Number(process.env.PORT ?? 8787);

const app = express();

app.use(cors());
// Screen definitions are large trees; the default 100kb limit is not enough.
app.use(express.json({ limit: '8mb' }));

app.use((req, _res, next) => {
  if (!req.path.startsWith('/api/v1/projects') || !req.path.includes('/preview/stream')) {
    console.log(`${req.method} ${req.originalUrl}`);
  }
  next();
});

app.get('/health', (_req, res) => {
  const screens = db.prepare('SELECT COUNT(*) AS n FROM screens').get() as { n: number };
  res.json({ ok: true, screens: screens.n, uptimeSeconds: Math.round(process.uptime()) });
});

app.get('/api/v1/projects', (_req, res) => {
  const projects = db.prepare('SELECT id, name, created_at FROM projects ORDER BY name').all();
  res.json({ projects });
});

app.post('/api/v1/projects', (req, res) => {
  const { id, name } = req.body ?? {};
  if (!id) return res.status(400).json({ error: "Body must include an 'id'" });
  ensureProject(String(id), name ? String(name) : String(id));
  res.status(201).json({ id, name: name ?? id });
});

app.use('/api/v1/projects/:projectId/screens', screensRouter);
app.use('/api/v1/projects/:projectId', platformRouter);
app.use('/api/v1/demo', demoRouter);

app.use((_req, res) => res.status(404).json({ error: 'Not found' }));

app.use((error: Error, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
  console.error(error);
  res.status(500).json({ error: error.message });
});

seedIfEmpty();

app.listen(PORT, () => {
  console.log(`SDUI backend listening on http://localhost:${PORT}`);
  console.log(`  screens   GET  /api/v1/projects/demo/screens/home?env=dev`);
  console.log(`  preview   GET  /api/v1/projects/demo/preview/stream?env=dev`);
  console.log(`  emulator  http://10.0.2.2:${PORT}`);
});
