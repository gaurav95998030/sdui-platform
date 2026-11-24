import type { FlagRow, TargetingContext, VariantSpec } from './types.js';

/**
 * Deterministic 0–99 bucket for a subject.
 *
 * The same user must land in the same bucket on every request and on every
 * server instance, so this is a pure hash of the id — never a random draw and
 * never sticky state in a database.
 */
export function bucket(subject: string, salt: string): number {
  let hash = 2166136261;
  const input = `${salt}:${subject}`;
  for (let i = 0; i < input.length; i++) {
    hash ^= input.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }
  return Math.abs(hash) % 100;
}

/** Numeric comparison of dotted versions; missing segments count as 0. */
export function compareVersions(a: string, b: string): number {
  const pa = a.split('.').map((p) => Number.parseInt(p, 10) || 0);
  const pb = b.split('.').map((p) => Number.parseInt(p, 10) || 0);
  const length = Math.max(pa.length, pb.length);
  for (let i = 0; i < length; i++) {
    const diff = (pa[i] ?? 0) - (pb[i] ?? 0);
    if (diff !== 0) return diff;
  }
  return 0;
}

/**
 * Evaluates one flag against a request.
 *
 * Every condition is an AND: a user must clear the kill switch, the allow-list,
 * the country, the minimum app version, the premium requirement *and* the
 * percentage bucket. That ordering means a rollout can always be stopped by
 * flipping `enabled`, regardless of the other rules.
 */
export function isFlagEnabled(flag: FlagRow, context: TargetingContext): boolean {
  if (!flag.enabled) return false;

  const explicitUsers = parseList(flag.user_ids);
  if (explicitUsers.length > 0) {
    return context.userId != null && explicitUsers.includes(context.userId);
  }

  const countries = parseList(flag.countries);
  if (countries.length > 0) {
    if (!context.country || !countries.includes(context.country.toUpperCase())) return false;
  }

  if (flag.min_app_version && context.appVersion) {
    if (compareVersions(context.appVersion, flag.min_app_version) < 0) return false;
  }

  if (flag.premium_only && !context.premium) return false;

  const percent = clamp(flag.rollout_percent, 0, 100);
  if (percent >= 100) return true;
  if (percent <= 0) return false;

  // Anonymous users get the flag only at a full rollout, so an experiment never
  // reports results it cannot attribute.
  if (!context.userId) return false;
  return bucket(context.userId, flag.key) < percent;
}

/** Picks an A/B variant by weighted deterministic bucketing. */
export function assignVariant(
  experimentId: string,
  variants: VariantSpec[],
  context: TargetingContext,
): VariantSpec | undefined {
  const usable = variants.filter((v) => v.weight > 0);
  if (usable.length === 0) return undefined;

  const subject = context.userId ?? 'anonymous';
  const point = bucket(subject, experimentId);
  const total = usable.reduce((sum, v) => sum + v.weight, 0);

  let cursor = 0;
  for (const variant of usable) {
    cursor += (variant.weight / total) * 100;
    if (point < cursor) return variant;
  }
  return usable[usable.length - 1];
}

function parseList(raw: string | null): string[] {
  if (!raw) return [];
  return raw
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}
