export type Environment = 'dev' | 'qa' | 'stage' | 'prod';

export const ENVIRONMENTS: Environment[] = ['dev', 'qa', 'stage', 'prod'];

export function parseEnvironment(raw: unknown): Environment {
  const value = String(raw ?? 'prod').toLowerCase();
  return (ENVIRONMENTS as string[]).includes(value) ? (value as Environment) : 'prod';
}

/** The shape the Android SDK deserialises. Keep in sync with `ScreenEnvelope.kt`. */
export interface ScreenEnvelope {
  screen: Record<string, unknown>;
  etag?: string;
  ttlSeconds?: number;
  experiment?: { experimentId: string; variant: string };
  flags?: Record<string, boolean>;
}

export interface TargetingContext {
  userId?: string;
  country?: string;
  appVersion?: string;
  premium: boolean;
}

export interface FlagRow {
  key: string;
  enabled: number;
  rollout_percent: number;
  countries: string | null;
  min_app_version: string | null;
  premium_only: number;
  user_ids: string | null;
}

export interface VariantSpec {
  name: string;
  versionId: number;
  weight: number;
}
