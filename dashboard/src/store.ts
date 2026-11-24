import { create } from 'zustand';
import { api } from './api';
import {
  duplicateNode,
  insertNode,
  moveNode,
  nodeAt,
  removeNode,
  updateNode,
  updateNodeSection,
} from './tree';
import type {
  Environment,
  FeatureFlag,
  NodePath,
  ScreenSummary,
  SduiNode,
  SduiScreen,
  ThemeSpec,
  VersionSummary,
} from './types';

export type Panel = 'inspector' | 'json' | 'versions' | 'theme' | 'flags' | 'analytics';

interface BuilderState {
  projectId: string;
  environment: Environment;

  screens: ScreenSummary[];
  screenId: string | null;
  doc: SduiScreen | null;
  selected: NodePath | null;
  dirty: boolean;
  panel: Panel;

  versions: VersionSummary[];
  flags: FeatureFlag[];
  themes: Array<{ id: string; definition: ThemeSpec }>;
  devices: number;
  status: string | null;
  error: string | null;

  /** Undo/redo hold whole documents; screens are small and this keeps edits atomic. */
  past: SduiScreen[];
  future: SduiScreen[];

  setEnvironment: (environment: Environment) => Promise<void>;
  loadScreens: () => Promise<void>;
  openScreen: (screenId: string) => Promise<void>;
  createScreen: (screenId: string, name: string) => Promise<void>;
  deleteScreen: (screenId: string) => Promise<void>;

  select: (path: NodePath | null) => void;
  setPanel: (panel: Panel) => void;

  patchNode: (path: NodePath, patch: Partial<SduiNode>) => void;
  patchSection: (
    path: NodePath,
    section: 'props' | 'modifier' | 'style' | 'animation',
    patch: Record<string, unknown>,
  ) => void;
  addNode: (parent: NodePath, index: number, node: SduiNode) => void;
  dropNode: (from: NodePath, toParent: NodePath, toIndex: number) => void;
  deleteNode: (path: NodePath) => void;
  duplicate: (path: NodePath) => void;
  replaceDoc: (doc: SduiScreen, markDirty?: boolean) => void;
  patchScreen: (patch: Partial<SduiScreen>) => void;

  undo: () => void;
  redo: () => void;

  save: (message?: string) => Promise<void>;
  publish: (environment?: Environment) => Promise<void>;
  rollback: (versionId?: number) => Promise<void>;
  loadVersions: () => Promise<void>;
  restoreVersion: (versionId: number) => Promise<void>;

  loadFlags: () => Promise<void>;
  saveFlag: (key: string, body: Record<string, unknown>) => Promise<void>;
  loadThemes: () => Promise<void>;
  saveTheme: (theme: ThemeSpec) => Promise<void>;
  refreshDevices: () => Promise<void>;

  pushPreview: () => Promise<void>;
}

const PREVIEW_DEBOUNCE_MS = 350;
let previewTimer: ReturnType<typeof setTimeout> | undefined;

export const useBuilder = create<BuilderState>((set, get) => {
  /**
   * Applies a tree edit: pushes the previous document onto the undo stack, marks
   * the draft dirty, and schedules a live-preview push so connected devices track
   * the canvas without waiting for a save.
   */
  const edit = (transform: (root: SduiNode) => SduiNode) => {
    const { doc, past } = get();
    if (!doc) return;
    const root = transform(doc.root);
    if (root === doc.root) return;
    set({
      doc: { ...doc, root },
      past: [...past.slice(-49), doc],
      future: [],
      dirty: true,
    });
    schedulePreview();
  };

  const schedulePreview = () => {
    clearTimeout(previewTimer);
    previewTimer = setTimeout(() => void get().pushPreview(), PREVIEW_DEBOUNCE_MS);
  };

  const withError = async (label: string, run: () => Promise<void>) => {
    try {
      set({ error: null });
      await run();
    } catch (error) {
      set({ error: `${label}: ${(error as Error).message}` });
    }
  };

  const flash = (status: string) => {
    set({ status });
    setTimeout(() => {
      if (get().status === status) set({ status: null });
    }, 2500);
  };

  return {
    projectId: 'demo',
    environment: 'dev',
    screens: [],
    screenId: null,
    doc: null,
    selected: null,
    dirty: false,
    panel: 'inspector',
    versions: [],
    flags: [],
    themes: [],
    devices: 0,
    status: null,
    error: null,
    past: [],
    future: [],

    setEnvironment: async (environment) => {
      set({ environment });
      await get().loadScreens();
      await get().loadFlags();
      await get().loadThemes();
    },

    loadScreens: () =>
      withError('Loading screens', async () => {
        const { screens } = await api.screens(get().projectId);
        set({ screens });
      }),

    openScreen: (screenId) =>
      withError(`Opening ${screenId}`, async () => {
        const { screen } = await api.screen(get().projectId, screenId, get().environment);
        set({
          screenId,
          doc: screen,
          selected: [],
          dirty: false,
          past: [],
          future: [],
          versions: [],
        });
        await get().loadVersions();
      }),

    createScreen: (screenId, name) =>
      withError('Creating screen', async () => {
        await api.createScreen(get().projectId, screenId, name);
        await get().loadScreens();
        await get().openScreen(screenId);
        flash(`Created ${screenId}`);
      }),

    deleteScreen: (screenId) =>
      withError('Deleting screen', async () => {
        await api.deleteScreen(get().projectId, screenId);
        if (get().screenId === screenId) set({ screenId: null, doc: null, selected: null });
        await get().loadScreens();
      }),

    select: (path) => set({ selected: path }),
    setPanel: (panel) => set({ panel }),

    patchNode: (path, patch) => edit((root) => updateNode(root, path, patch)),
    patchSection: (path, section, patch) =>
      edit((root) => updateNodeSection(root, path, section, patch)),

    addNode: (parent, index, node) => {
      edit((root) => insertNode(root, parent, index, node));
      set({ selected: [...parent, index] });
    },

    dropNode: (from, toParent, toIndex) => edit((root) => moveNode(root, from, toParent, toIndex)),

    deleteNode: (path) => {
      if (path.length === 0) return;
      edit((root) => removeNode(root, path));
      set({ selected: path.slice(0, -1) });
    },

    duplicate: (path) => edit((root) => duplicateNode(root, path)),

    replaceDoc: (doc, markDirty = true) => {
      const previous = get().doc;
      set({
        doc,
        dirty: markDirty,
        past: previous ? [...get().past.slice(-49), previous] : get().past,
        future: [],
      });
      schedulePreview();
    },

    patchScreen: (patch) => {
      const doc = get().doc;
      if (!doc) return;
      get().replaceDoc({ ...doc, ...patch });
    },

    undo: () => {
      const { past, doc, future } = get();
      const previous = past[past.length - 1];
      if (!previous || !doc) return;
      set({ doc: previous, past: past.slice(0, -1), future: [doc, ...future], dirty: true });
      schedulePreview();
    },

    redo: () => {
      const { future, doc, past } = get();
      const next = future[0];
      if (!next || !doc) return;
      set({ doc: next, future: future.slice(1), past: [...past, doc], dirty: true });
      schedulePreview();
    },

    save: (message) =>
      withError('Saving draft', async () => {
        const { projectId, screenId, doc } = get();
        if (!screenId || !doc) return;
        const result = await api.saveDraft(projectId, screenId, doc, message);
        set({ dirty: false });
        flash(`Saved as version ${result.version}`);
        await get().loadVersions();
        await get().loadScreens();
      }),

    publish: (environment) =>
      withError('Publishing', async () => {
        const { projectId, screenId, doc, dirty } = get();
        if (!screenId || !doc) return;
        // Publishing an unsaved canvas would ship something with no history entry,
        // so the draft is committed first and that exact version is deployed.
        if (dirty) await get().save('Autosaved before publish');
        const target = environment ?? get().environment;
        const result = await api.publish(projectId, screenId, target);
        flash(`Published v${result.version} to ${target} · ${result.devicesNotified} device(s)`);
        await get().loadVersions();
        await get().loadScreens();
      }),

    rollback: (versionId) =>
      withError('Rolling back', async () => {
        const { projectId, screenId, environment } = get();
        if (!screenId) return;
        const result = await api.rollback(projectId, screenId, environment, versionId);
        flash(`Rolled back to v${result.rolledBackTo}`);
        await get().loadVersions();
        await get().openScreen(screenId);
      }),

    loadVersions: () =>
      withError('Loading history', async () => {
        const { projectId, screenId } = get();
        if (!screenId) return;
        const { versions } = await api.versions(projectId, screenId);
        set({ versions });
      }),

    restoreVersion: (versionId) =>
      withError('Restoring version', async () => {
        const { projectId, screenId } = get();
        if (!screenId) return;
        const { definition } = await api.version(projectId, screenId, versionId);
        get().replaceDoc(definition);
        flash('Loaded into the canvas — save to keep it');
      }),

    loadFlags: () =>
      withError('Loading flags', async () => {
        const { flags } = await api.flags(get().projectId, get().environment);
        set({ flags });
      }),

    saveFlag: (key, body) =>
      withError('Saving flag', async () => {
        await api.saveFlag(get().projectId, key, body as never, get().environment);
        await get().loadFlags();
        flash(`Flag '${key}' updated`);
      }),

    loadThemes: () =>
      withError('Loading themes', async () => {
        const { themes } = await api.themes(get().projectId, get().environment);
        set({ themes });
      }),

    saveTheme: (theme) =>
      withError('Saving theme', async () => {
        const result = await api.saveTheme(get().projectId, theme.id, theme, get().environment);
        await get().loadThemes();
        flash(`Theme pushed to ${result.devicesNotified} device(s)`);
      }),

    refreshDevices: () =>
      withError('Checking devices', async () => {
        const stats = await api.devices(get().projectId);
        set({ devices: stats.byProject[`${get().projectId}/${get().environment}`] ?? 0 });
      }),

    pushPreview: async () => {
      const { projectId, screenId, doc, environment } = get();
      if (!screenId || !doc) return;
      try {
        const result = await api.pushPreview(projectId, screenId, doc, environment);
        set({ devices: result.devicesNotified });
      } catch {
        // Preview is best-effort: a disconnected device must never block editing.
      }
    },
  };
});

export const useSelectedNode = (): SduiNode | undefined => {
  const doc = useBuilder((s) => s.doc);
  const selected = useBuilder((s) => s.selected);
  if (!doc || !selected) return undefined;
  return nodeAt(doc.root, selected);
};
