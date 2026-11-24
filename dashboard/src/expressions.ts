import type { Json, JsonObject } from './types';

/**
 * A compact evaluator for `{{ … }}` bindings, mirroring the Kotlin engine closely
 * enough that the canvas shows what the device will show.
 *
 * It is intentionally *not* a second implementation of the language: the device
 * remains the source of truth, and anything this cannot evaluate renders as an
 * empty string rather than as an error, so an unsupported expression degrades the
 * preview instead of breaking it.
 */

type Scope = Record<string, Json>;

const FUNCTIONS: Record<string, (args: Json[]) => Json> = {
  upper: ([v]) => String(text(v)).toUpperCase(),
  lower: ([v]) => String(text(v)).toLowerCase(),
  trim: ([v]) => text(v).trim(),
  length: ([v]) => (Array.isArray(v) ? v.length : text(v).length),
  join: ([v, sep]) => (Array.isArray(v) ? v.map(text).join(text(sep) || ', ') : ''),
  number: ([v]) => num(v) ?? 0,
  round: ([v, d]) => {
    const factor = 10 ** (num(d) ?? 0);
    return Math.round((num(v) ?? 0) * factor) / factor;
  },
  abs: ([v]) => Math.abs(num(v) ?? 0),
  min: (args) => Math.min(...args.map((a) => num(a) ?? 0)),
  max: (args) => Math.max(...args.map((a) => num(a) ?? 0)),
  sum: ([list, path]) =>
    Array.isArray(list)
      ? list.reduce<number>((total, item) => total + (num(atPath(item, text(path))) ?? 0), 0)
      : 0,
  currency: ([v, code]) => {
    const value = num(v) ?? 0;
    try {
      return new Intl.NumberFormat(undefined, {
        style: 'currency',
        currency: text(code) || 'USD',
      }).format(value);
    } catch {
      return String(value);
    }
  },
  compact: ([v]) => {
    const value = num(v) ?? 0;
    const abs = Math.abs(value);
    if (abs >= 1e9) return `${trim(value / 1e9)}B`;
    if (abs >= 1e6) return `${trim(value / 1e6)}M`;
    if (abs >= 1e3) return `${trim(value / 1e3)}K`;
    return trim(value);
  },
  initials: ([v]) =>
    text(v)
      .split(/[\s.]+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]!.toUpperCase())
      .join(''),
  truncate: ([v, n]) => {
    const value = text(v);
    const max = num(n) ?? 40;
    return value.length <= max ? value : `${value.slice(0, max).trimEnd()}…`;
  },
  formatdate: ([v, pattern]) => formatDate(v, text(pattern) || 'dd MMM yyyy'),
  default: (args) => args.find((a) => truthy(a)) ?? args[1] ?? '',
  if: ([c, a, b]) => (truthy(c) ? (a ?? '') : (b ?? '')),
  not: ([v]) => !truthy(v),
  string: ([v]) => text(v),
  now: () => Date.now(),
};

export function truthy(value: Json | undefined): boolean {
  if (value === undefined || value === null) return false;
  if (typeof value === 'boolean') return value;
  if (typeof value === 'number') return value !== 0;
  if (typeof value === 'string') return value.length > 0 && value !== 'false' && value !== '0';
  if (Array.isArray(value)) return value.length > 0;
  return Object.keys(value).length > 0;
}

export function text(value: Json | undefined): string {
  if (value === undefined || value === null) return '';
  if (typeof value === 'number') return Number.isInteger(value) ? String(value) : String(value);
  if (typeof value === 'string') return value;
  if (typeof value === 'boolean') return String(value);
  return JSON.stringify(value);
}

function num(value: Json | undefined): number | undefined {
  if (typeof value === 'number') return value;
  if (typeof value === 'boolean') return value ? 1 : 0;
  if (typeof value === 'string') {
    const parsed = Number.parseFloat(value);
    return Number.isNaN(parsed) ? undefined : parsed;
  }
  return undefined;
}

function trim(value: number): string {
  const rounded = Math.round(value * 10) / 10;
  return Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(1);
}

function formatDate(value: Json | undefined, pattern: string): string {
  const raw = typeof value === 'number' ? value : Date.parse(text(value));
  if (Number.isNaN(raw)) return '';
  const date = new Date(raw);
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  return pattern
    .replace('yyyy', String(date.getFullYear()))
    .replace('MMM', months[date.getMonth()]!)
    .replace('MM', String(date.getMonth() + 1).padStart(2, '0'))
    .replace('dd', String(date.getDate()).padStart(2, '0'));
}

export function atPath(source: Json | undefined, path: string): Json | undefined {
  if (source === undefined || source === null || !path) return source ?? undefined;
  let cursor: Json | undefined = source;
  for (const rawSegment of path.split('.')) {
    const match = /^(.*?)((?:\[\d+\])+)$/.exec(rawSegment);
    const key = match ? match[1]! : rawSegment;
    if (key) {
      if (cursor === null || typeof cursor !== 'object' || Array.isArray(cursor)) return undefined;
      cursor = (cursor as JsonObject)[key];
    }
    if (match?.[2]) {
      for (const index of match[2].matchAll(/\[(\d+)\]/g)) {
        if (!Array.isArray(cursor)) return undefined;
        cursor = cursor[Number(index[1])];
      }
    }
    if (cursor === undefined || cursor === null) return undefined;
  }
  return cursor;
}

/**
 * Evaluates one expression body (already stripped of its braces).
 *
 * Supported: paths, string/number/boolean literals, `? :`, `?:`, `&&`, `||`, `!`,
 * comparisons, `+ - * / %`, indexing, `.length`, and the function table above.
 */
export function evaluate(source: string, scope: Scope): Json | undefined {
  const expression = source.trim();
  if (!expression) return undefined;

  try {
    return parseTernary(new Tokens(expression), scope);
  } catch {
    return undefined;
  }
}

/** Resolves a template that mixes literal text with `{{ }}` fragments. */
export function interpolate(template: string, scope: Scope): Json | undefined {
  if (!template.includes('{{')) return template;

  const whole = /^\{\{([^{}]*)\}\}$/.exec(template.trim());
  if (whole) return evaluate(whole[1]!, scope);

  return template.replace(/\{\{([^{}]*)\}\}/g, (_, body: string) => text(evaluate(body, scope)));
}

/** Deep-resolves every string inside a props object. */
export function resolveTree(value: Json, scope: Scope): Json {
  if (typeof value === 'string') {
    return value.includes('{{') ? (interpolate(value, scope) ?? '') : value;
  }
  if (Array.isArray(value)) return value.map((item) => resolveTree(item, scope));
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, resolveTree(v, scope)]));
  }
  return value;
}

// ---------------------------------------------------------------------------
// Recursive-descent parser. Small enough to keep inline; see `Expression.kt`
// for the canonical grammar.
// ---------------------------------------------------------------------------

class Tokens {
  private index = 0;
  constructor(private readonly source: string) {}

  peek(): string {
    this.skipSpace();
    return this.source.slice(this.index);
  }

  eat(token: string): boolean {
    this.skipSpace();
    if (this.source.startsWith(token, this.index)) {
      this.index += token.length;
      return true;
    }
    return false;
  }

  expect(token: string): void {
    if (!this.eat(token)) throw new Error(`Expected ${token}`);
  }

  readWhile(pattern: RegExp): string {
    this.skipSpace();
    let out = '';
    while (this.index < this.source.length && pattern.test(this.source[this.index]!)) {
      out += this.source[this.index++];
    }
    return out;
  }

  readString(quote: string): string {
    let out = '';
    while (this.index < this.source.length && this.source[this.index] !== quote) {
      out += this.source[this.index++];
    }
    this.index++;
    return out;
  }

  private skipSpace(): void {
    while (this.index < this.source.length && /\s/.test(this.source[this.index]!)) this.index++;
  }
}

function parseTernary(t: Tokens, scope: Scope): Json | undefined {
  const condition = parseElvis(t, scope);
  if (t.eat('?')) {
    const whenTrue = parseTernary(t, scope);
    t.expect(':');
    const whenFalse = parseTernary(t, scope);
    return truthy(condition) ? whenTrue : whenFalse;
  }
  return condition;
}

function parseElvis(t: Tokens, scope: Scope): Json | undefined {
  let left = parseOr(t, scope);
  while (t.peek().startsWith('?:') || t.peek().startsWith('??')) {
    t.eat(t.peek().slice(0, 2));
    const right = parseOr(t, scope);
    left = left === undefined || left === null ? right : left;
  }
  return left;
}

function parseOr(t: Tokens, scope: Scope): Json | undefined {
  let left = parseAnd(t, scope);
  while (t.eat('||')) left = truthy(left) || truthy(parseAnd(t, scope));
  return left;
}

function parseAnd(t: Tokens, scope: Scope): Json | undefined {
  let left = parseComparison(t, scope);
  while (t.eat('&&')) left = truthy(left) && truthy(parseComparison(t, scope));
  return left;
}

function parseComparison(t: Tokens, scope: Scope): Json | undefined {
  let left = parseAdditive(t, scope);
  for (;;) {
    const operator = ['==', '!=', '<=', '>=', '<', '>'].find((op) => t.peek().startsWith(op));
    if (!operator) return left;
    t.eat(operator);
    const right = parseAdditive(t, scope);
    left = compare(operator, left, right);
  }
}

function compare(operator: string, left: Json | undefined, right: Json | undefined): boolean {
  const ln = num(left);
  const rn = num(right);
  const comparable = ln !== undefined && rn !== undefined;
  switch (operator) {
    case '==':
      return comparable ? ln === rn : text(left) === text(right);
    case '!=':
      return comparable ? ln !== rn : text(left) !== text(right);
    case '<':
      return comparable ? ln < rn : text(left) < text(right);
    case '<=':
      return comparable ? ln <= rn : text(left) <= text(right);
    case '>':
      return comparable ? ln > rn : text(left) > text(right);
    default:
      return comparable ? ln >= rn : text(left) >= text(right);
  }
}

function parseAdditive(t: Tokens, scope: Scope): Json | undefined {
  let left = parseMultiplicative(t, scope);
  for (;;) {
    if (t.eat('+')) {
      const right = parseMultiplicative(t, scope);
      const ln = num(left);
      const rn = num(right);
      left =
        ln !== undefined && rn !== undefined && typeof left !== 'string' && typeof right !== 'string'
          ? ln + rn
          : text(left) + text(right);
    } else if (t.peek().startsWith('-') && !t.peek().startsWith('->')) {
      t.eat('-');
      left = (num(left) ?? 0) - (num(parseMultiplicative(t, scope)) ?? 0);
    } else {
      return left;
    }
  }
}

function parseMultiplicative(t: Tokens, scope: Scope): Json | undefined {
  let left = parseUnary(t, scope);
  for (;;) {
    if (t.eat('*')) left = (num(left) ?? 0) * (num(parseUnary(t, scope)) ?? 0);
    else if (t.eat('/')) {
      const divisor = num(parseUnary(t, scope)) ?? 0;
      left = divisor === 0 ? undefined : (num(left) ?? 0) / divisor;
    } else if (t.eat('%')) {
      const divisor = num(parseUnary(t, scope)) ?? 0;
      left = divisor === 0 ? undefined : (num(left) ?? 0) % divisor;
    } else return left;
  }
}

function parseUnary(t: Tokens, scope: Scope): Json | undefined {
  if (t.eat('!')) return !truthy(parseUnary(t, scope));
  if (t.peek().startsWith('-')) {
    t.eat('-');
    return -(num(parseUnary(t, scope)) ?? 0);
  }
  return parsePostfix(t, scope);
}

function parsePostfix(t: Tokens, scope: Scope): Json | undefined {
  let value = parsePrimary(t, scope);
  for (;;) {
    if (t.eat('.')) {
      const name = t.readWhile(/[A-Za-z0-9_$]/);
      value = member(value, name);
    } else if (t.eat('[')) {
      const index = parseTernary(t, scope);
      t.expect(']');
      if (Array.isArray(value)) value = value[num(index) ?? 0];
      else if (value && typeof value === 'object') value = (value as JsonObject)[text(index)];
      else value = undefined;
    } else return value;
  }
}

function member(value: Json | undefined, name: string): Json | undefined {
  if (name === 'length' || name === 'size' || name === 'count') {
    if (Array.isArray(value)) return value.length;
    if (typeof value === 'string') return value.length;
    if (value && typeof value === 'object') return Object.keys(value).length;
    return 0;
  }
  if (name === 'isEmpty') return !truthy(value);
  if (name === 'isNotEmpty') return truthy(value);
  if (name === 'first') return Array.isArray(value) ? value[0] : undefined;
  if (name === 'last') return Array.isArray(value) ? value[value.length - 1] : undefined;
  if (value && typeof value === 'object' && !Array.isArray(value)) return (value as JsonObject)[name];
  return undefined;
}

function parsePrimary(t: Tokens, scope: Scope): Json | undefined {
  if (t.eat('(')) {
    const inner = parseTernary(t, scope);
    t.expect(')');
    return inner;
  }
  if (t.eat("'")) return t.readString("'");
  if (t.eat('"')) return t.readString('"');

  const numeric = t.readWhile(/[0-9.]/);
  if (numeric) return Number.parseFloat(numeric);

  const identifier = t.readWhile(/[A-Za-z0-9_$]/);
  if (!identifier) throw new Error('Unexpected token');

  if (identifier === 'true') return true;
  if (identifier === 'false') return false;
  if (identifier === 'null') return undefined;

  if (t.eat('(')) {
    const args: Json[] = [];
    if (!t.eat(')')) {
      do {
        args.push(parseTernary(t, scope) ?? '');
      } while (t.eat(','));
      t.expect(')');
    }
    const fn = FUNCTIONS[identifier.toLowerCase()];
    return fn ? fn(args) : undefined;
  }

  return scope[identifier];
}
