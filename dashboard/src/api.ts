import type {
  Environment,
  FeatureFlag,
  JsonObject,
  ScreenSummary,
  SduiScreen,
  ThemeSpec,
  VersionSummary,
} from './types';

const BASE = '/api/v1';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  });
  if (!response.ok) {
    const detail = await response.text().catch(() => '');
    throw new Error(`${response.status} ${response.statusText}${detail ? ` — ${detail}` : ''}`);
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

/**
 * Typed client for the SDUI backend.
 *
 * Every authoring call is explicit about the environment it targets — publishing
 * to the wrong one is the single most costly mistake in a server-driven setup, so
 * the parameter is never implicit.
 */
export const api = {
  projects: () => request<{ projects: Array<{ id: string; name: string }> }>('/projects'),

  screens: (projectId: string) =>
    request<{ screens: ScreenSummary[] }>(`/projects/${projectId}/screens`),

  screen: (projectId: string, screenId: string, env: Environment, version?: string) =>
    request<{ screen: SduiScreen; etag?: string }>(
      `/projects/${projectId}/screens/${screenId}?env=${env}${version ? `&version=${version}` : ''}`,
    ),

  createScreen: (projectId: string, screenId: string, name: string) =>
    request<{ screenId: string; versionId: number }>(`/projects/${projectId}/screens`, {
      method: 'POST',
      body: JSON.stringify({ screenId, name }),
    }),

  deleteScreen: (projectId: string, screenId: string) =>
    request<void>(`/projects/${projectId}/screens/${screenId}`, { method: 'DELETE' }),

  saveDraft: (projectId: string, screenId: string, definition: SduiScreen, message?: string) =>
    request<{ versionId: number; version: string }>(
      `/projects/${projectId}/screens/${screenId}/draft`,
      { method: 'PUT', body: JSON.stringify({ definition, message }) },
    ),

  publish: (projectId: string, screenId: string, environment: Environment, versionId?: number) =>
    request<{ version: string; devicesNotified: number }>(
      `/projects/${projectId}/screens/${screenId}/publish`,
      { method: 'POST', body: JSON.stringify({ environment, versionId }) },
    ),

  rollback: (projectId: string, screenId: string, environment: Environment, versionId?: number) =>
    request<{ rolledBackTo: string }>(`/projects/${projectId}/screens/${screenId}/rollback`, {
      method: 'POST',
      body: JSON.stringify({ environment, versionId }),
    }),

  versions: (projectId: string, screenId: string) =>
    request<{ versions: VersionSummary[] }>(`/projects/${projectId}/screens/${screenId}/versions`),

  version: (projectId: string, screenId: string, versionId: number) =>
    request<{ definition: SduiScreen }>(
      `/projects/${projectId}/screens/${screenId}/versions/${versionId}`,
    ),

  /** Pushes the unsaved working tree to connected devices. */
  pushPreview: (projectId: string, screenId: string, definition: SduiScreen, environment: Environment) =>
    request<{ devicesNotified: number }>(`/projects/${projectId}/preview/push`, {
      method: 'POST',
      body: JSON.stringify({ screenId, definition, environment }),
    }),

  devices: (projectId: string) =>
    request<{ total: number; byProject: Record<string, number> }>(
      `/projects/${projectId}/preview/devices`,
    ),

  themes: (projectId: string, env: Environment) =>
    request<{ themes: Array<{ id: string; definition: ThemeSpec }> }>(
      `/projects/${projectId}/themes?env=${env}`,
    ),

  saveTheme: (projectId: string, themeId: string, definition: ThemeSpec, environment: Environment) =>
    request<{ devicesNotified: number }>(`/projects/${projectId}/themes/${themeId}`, {
      method: 'PUT',
      body: JSON.stringify({ definition, environment }),
    }),

  flags: (projectId: string, env: Environment) =>
    request<{ flags: FeatureFlag[] }>(`/projects/${projectId}/flags?env=${env}`),

  saveFlag: (projectId: string, key: string, body: JsonObject, environment: Environment) =>
    request<{ key: string }>(`/projects/${projectId}/flags/${key}`, {
      method: 'PUT',
      body: JSON.stringify({ ...body, environment }),
    }),

  deleteFlag: (projectId: string, key: string, env: Environment) =>
    request<void>(`/projects/${projectId}/flags/${key}?env=${env}`, { method: 'DELETE' }),

  evaluateFlags: (projectId: string, env: Environment, query: Record<string, string>) =>
    request<{ results: Array<{ key: string; enabled: boolean }> }>(
      `/projects/${projectId}/flags/evaluate?env=${env}&${new URLSearchParams(query)}`,
    ),

  experiments: (projectId: string, env: Environment) =>
    request<{ experiments: Array<Record<string, unknown>> }>(
      `/projects/${projectId}/experiments?env=${env}`,
    ),

  saveExperiment: (projectId: string, id: string, body: JsonObject, environment: Environment) =>
    request<unknown>(`/projects/${projectId}/experiments/${id}`, {
      method: 'PUT',
      body: JSON.stringify({ ...body, environment }),
    }),

  analytics: (projectId: string) =>
    request<{
      totals: Array<{ name: string; count: number }>;
      recent: Array<{ name: string; properties: JsonObject; received_at: number }>;
    }>(`/projects/${projectId}/analytics/summary`),
};
