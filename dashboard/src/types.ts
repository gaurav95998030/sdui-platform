/** Mirrors the Kotlin model in `sdui-core`. Keep the two in sync when the schema moves. */

export type Json = string | number | boolean | null | Json[] | { [key: string]: Json };
export type JsonObject = { [key: string]: Json };

export interface SduiNode {
  type: string;
  id?: string;
  visible?: string | boolean;
  modifier?: JsonObject;
  style?: JsonObject;
  animation?: JsonObject;
  props?: JsonObject;
  actions?: Record<string, JsonObject>;
  repeat?: { items: string; as?: string; indexAs?: string; limit?: number };
  analytics?: JsonObject;
  children?: SduiNode[];
}

export interface SduiScreen {
  id: string;
  schemaVersion?: number;
  version?: string;
  title?: string;
  root: SduiNode;
  state?: JsonObject;
  apis?: Record<string, JsonObject>;
  onLoad?: JsonObject[];
  onAppear?: JsonObject[];
  theme?: string;
  background?: string;
  scrollable?: boolean;
  appBar?: {
    title?: string;
    subtitle?: string;
    showBack?: boolean;
    centered?: boolean;
    background?: string;
  };
  metadata?: JsonObject;
}

export type Environment = 'dev' | 'qa' | 'stage' | 'prod';

export interface Deployment {
  environment: Environment;
  version: string;
  versionId: number;
  deployedAt: number;
}

export interface ScreenSummary {
  screen_id: string;
  name: string;
  updated_at: number;
  versions: number;
  deployments: Deployment[];
}

export interface VersionSummary {
  id: number;
  version: string;
  status: string;
  author: string | null;
  message: string | null;
  created_at: number;
  deployedTo: string[];
}

export interface FeatureFlag {
  key: string;
  enabled: number;
  rollout_percent: number;
  countries: string | null;
  min_app_version: string | null;
  premium_only: number;
  user_ids: string | null;
}

export interface ThemeSpec {
  id: string;
  name?: string;
  mode?: 'system' | 'light' | 'dark';
  light?: Record<string, string>;
  dark?: Record<string, string>;
  shapes?: Record<string, number>;
  tokens?: Record<string, string>;
  spacing?: Record<string, number>;
}

/** Location of a node in the tree: child indices from the root, root itself is []. */
export type NodePath = number[];

export const pathKey = (path: NodePath): string => path.join('.') || 'root';

export const isSamePath = (a: NodePath | null, b: NodePath | null): boolean =>
  a != null && b != null && a.length === b.length && a.every((v, i) => v === b[i]);
