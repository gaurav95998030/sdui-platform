import type { CSSProperties, DragEvent, ReactNode } from 'react';
import { isContainer } from '../catalog';
import { interpolate, resolveTree, text, truthy } from '../expressions';
import { useBuilder } from '../store';
import type { Json, JsonObject, NodePath, SduiNode } from '../types';
import { isSamePath } from '../types';
import { FONT_WEIGHTS, TYPOGRAPHY, resolveColor, type ResolvedTheme } from './theme';

interface Ctx {
  theme: ResolvedTheme;
  scope: Record<string, Json>;
  selected: NodePath | null;
  interactive: boolean;
}

/**
 * Renders one node to DOM.
 *
 * This is a *preview*, not a second SDK: it re-implements enough of the modifier
 * pipeline and the component set to be trustworthy while designing, and everything
 * it cannot render falls back to a labelled placeholder rather than disappearing —
 * so an author always sees that something is there.
 */
export function NodePreview({
  node,
  path,
  ctx,
}: {
  node: SduiNode;
  path: NodePath;
  ctx: Ctx;
}): ReactNode {
  const select = useBuilder((s) => s.select);
  const dropNode = useBuilder((s) => s.dropNode);
  const addNode = useBuilder((s) => s.addNode);

  const props = (resolveTree((node.props ?? {}) as Json, ctx.scope) ?? {}) as JsonObject;
  const modifier = (node.modifier ?? {}) as JsonObject;
  const style = (node.style ?? {}) as JsonObject;

  const visible = evaluateVisible(node.visible, ctx.scope);
  const isSelected = isSamePath(ctx.selected, path);

  // `repeat` expands into siblings, exactly as the renderer does on device.
  if (node.repeat && ctx.interactive === false) {
    const items = interpolate(node.repeat.items, ctx.scope);
    if (Array.isArray(items)) {
      return (
        <>
          {items.slice(0, node.repeat.limit ?? 20).map((item, index) => (
            <NodePreview
              key={index}
              node={{ ...node, repeat: undefined }}
              path={path}
              ctx={{
                ...ctx,
                scope: {
                  ...ctx.scope,
                  [node.repeat!.as ?? 'item']: item,
                  [node.repeat!.indexAs ?? 'index']: index,
                },
              }}
            />
          ))}
        </>
      );
    }
  }

  const boxStyle = modifierStyle(modifier, ctx.theme);
  const container = isContainer(node.type);

  const handleDrop = (event: DragEvent) => {
    if (!container) return;
    event.stopPropagation();
    event.preventDefault();
    const payload = event.dataTransfer.getData('application/sdui');
    if (!payload) return;
    const parsed = JSON.parse(payload) as { template?: SduiNode; from?: NodePath };
    const index = node.children?.length ?? 0;
    if (parsed.from) dropNode(parsed.from, path, index);
    else if (parsed.template) addNode(path, index, parsed.template);
  };

  const wrapperStyle: CSSProperties = {
    ...boxStyle,
    opacity: visible ? boxStyle.opacity : 0.35,
    outline: isSelected ? '2px solid #4C5BD4' : undefined,
    outlineOffset: isSelected ? 1 : undefined,
    position: 'relative',
  };

  const body = renderBody(node, props, style, ctx, path);

  return (
    <div
      className={`pv-node${container ? ' pv-container' : ''}${isSelected ? ' pv-selected' : ''}`}
      style={wrapperStyle}
      data-type={node.type}
      draggable={path.length > 0}
      onDragStart={(event) => {
        event.stopPropagation();
        event.dataTransfer.setData('application/sdui', JSON.stringify({ from: path }));
        event.dataTransfer.effectAllowed = 'move';
      }}
      onDragOver={(event) => {
        if (container) {
          event.stopPropagation();
          event.preventDefault();
          event.dataTransfer.dropEffect = 'move';
        }
      }}
      onDrop={handleDrop}
      onClick={(event) => {
        event.stopPropagation();
        select(path);
      }}
      title={`${node.type}${node.id ? ` · #${node.id}` : ''}`}
    >
      {!visible && <span className="pv-hidden-badge">hidden</span>}
      {body}
    </div>
  );
}

function evaluateVisible(visible: SduiNode['visible'], scope: Record<string, Json>): boolean {
  if (visible === undefined) return true;
  if (typeof visible === 'boolean') return visible;
  if (!visible.includes('{{')) return truthy(visible);
  return truthy(interpolate(visible, scope) as Json);
}

function children(node: SduiNode, path: NodePath, ctx: Ctx): ReactNode {
  return node.children?.map((child, index) => (
    <NodePreview key={child.id ?? index} node={child} path={[...path, index]} ctx={ctx} />
  ));
}

function renderBody(
  node: SduiNode,
  props: JsonObject,
  style: JsonObject,
  ctx: Ctx,
  path: NodePath,
): ReactNode {
  const type = node.type.toLowerCase();
  const color = (raw: unknown, fallback?: string) =>
    resolveColor(typeof raw === 'string' ? raw : undefined, ctx.theme) ?? fallback;

  switch (type) {
    case 'column':
    case 'vstack':
      return (
        <div style={flexStyle('column', props)} className="pv-flex">
          {children(node, path, ctx)}
          {emptyHint(node)}
        </div>
      );

    case 'row':
    case 'hstack':
      return (
        <div style={flexStyle('row', props)} className="pv-flex">
          {children(node, path, ctx)}
          {emptyHint(node)}
        </div>
      );

    case 'box':
    case 'stack':
      return (
        <div style={{ position: 'relative', display: 'flex' }} className="pv-flex">
          {children(node, path, ctx)}
          {emptyHint(node)}
        </div>
      );

    case 'card':
    case 'surface':
      return (
        <div
          style={{
            background: color(style.containerColor ?? node.modifier?.background, ctx.theme.scheme.surfaceContainerLow),
            borderRadius: cornerRadius(node.modifier?.cornerRadius) ?? 12,
            boxShadow: (node.modifier?.elevation as number) ? '0 2px 8px rgba(0,0,0,.14)' : '0 1px 2px rgba(0,0,0,.06)',
            overflow: 'hidden',
            display: 'flex',
            flexDirection: 'column',
            gap: `${(props.spacing as number) ?? 0}px`,
          }}
        >
          {children(node, path, ctx)}
          {emptyHint(node)}
        </div>
      );

    case 'text':
    case 'label':
      return <span style={textStyle(style, ctx)}>{text(props.text ?? props.value ?? '')}</span>;

    case 'image':
    case 'img':
      return (
        <img
          src={text(props.url ?? props.src ?? props.source)}
          alt={text(props.alt) || 'image'}
          style={{
            width: '100%',
            height: '100%',
            objectFit: props.contentScale === 'fit' ? 'contain' : 'cover',
            display: 'block',
          }}
        />
      );

    case 'icon':
      return (
        <span
          className="pv-icon"
          style={{ color: color(style.tint ?? style.textColor, ctx.theme.scheme.onSurfaceVariant), fontSize: (props.size as number) ?? 20 }}
        >
          {iconGlyph(text(props.name ?? props.icon))}
        </span>
      );

    case 'spacer':
      return <div style={{ width: (props.width as number) ?? 0, height: (props.height as number) ?? 8 }} />;

    case 'divider':
      return (
        <div
          style={{
            background: color(props.color, ctx.theme.scheme.outlineVariant),
            height: props.orientation === 'vertical' ? '100%' : ((props.thickness as number) ?? 1),
            width: props.orientation === 'vertical' ? ((props.thickness as number) ?? 1) : '100%',
          }}
        />
      );

    case 'button': {
      const variant = text(props.variant ?? style.variant ?? 'filled');
      const filled = variant === 'filled' || variant === 'elevated';
      const tonal = variant === 'tonal';
      return (
        <span
          className="pv-button"
          style={{
            background: filled
              ? color(style.containerColor, ctx.theme.scheme.primary)
              : tonal
                ? color(style.containerColor, ctx.theme.scheme.secondaryContainer)
                : 'transparent',
            color: filled
              ? color(style.contentColor, ctx.theme.scheme.onPrimary)
              : tonal
                ? color(style.contentColor, ctx.theme.scheme.onSecondaryContainer)
                : color(style.contentColor, ctx.theme.scheme.primary),
            border: variant === 'outlined' ? `1px solid ${ctx.theme.scheme.outline}` : 'none',
            opacity: truthy(props.enabled === undefined ? true : (props.enabled as Json)) ? 1 : 0.4,
          }}
        >
          {props.leadingIcon ? <span className="pv-icon">{iconGlyph(text(props.leadingIcon))}</span> : null}
          {truthy(props.loading) ? '…' : text(props.text ?? props.title)}
          {props.trailingIcon ? <span className="pv-icon">{iconGlyph(text(props.trailingIcon))}</span> : null}
        </span>
      );
    }

    case 'iconbutton':
      return (
        <span className="pv-icon-button" style={{ color: color(style.tint, ctx.theme.scheme.onSurface) }}>
          {iconGlyph(text(props.icon ?? props.name))}
        </span>
      );

    case 'chip':
      return (
        <span className="pv-chip" style={{ borderColor: ctx.theme.scheme.outline, color: ctx.theme.scheme.onSurface }}>
          {props.icon ? <span className="pv-icon">{iconGlyph(text(props.icon))}</span> : null}
          {text(props.text)}
        </span>
      );

    case 'badge':
      return (
        <span className="pv-badge" style={{ background: color(style.containerColor, ctx.theme.scheme.error), color: ctx.theme.scheme.onError }}>
          {text(props.text)}
        </span>
      );

    case 'lazycolumn':
    case 'list':
    case 'lazyrow':
    case 'carousel':
    case 'grid': {
      const items = Array.isArray(props.items) ? (props.items as Json[]) : samples(3);
      const template = node.children?.[0];
      const horizontal = type === 'lazyrow' || type === 'carousel';
      const grid = type === 'grid';
      return (
        <div
          style={{
            display: grid ? 'grid' : 'flex',
            flexDirection: horizontal ? 'row' : 'column',
            gridTemplateColumns: grid ? `repeat(${(props.columns as number) ?? 2}, 1fr)` : undefined,
            gap: `${(props.spacing as number) ?? 8}px`,
            overflow: 'hidden',
          }}
        >
          {template
            ? items.slice(0, grid ? 4 : 4).map((item, index) => (
                <NodePreview
                  key={index}
                  node={template}
                  path={[...path, 0]}
                  ctx={{
                    ...ctx,
                    scope: {
                      ...ctx.scope,
                      [(node.repeat?.as ?? text(props.as)) || 'item']: item,
                      [(node.repeat?.indexAs ?? text(props.indexAs)) || 'index']: index,
                    },
                  }}
                />
              ))
            : emptyHint(node)}
        </div>
      );
    }

    case 'textfield':
    case 'input':
    case 'number':
    case 'password':
    case 'otp':
    case 'datepicker':
      return (
        <div className="pv-field" style={{ borderColor: ctx.theme.scheme.outline }}>
          <span className="pv-field-label" style={{ color: ctx.theme.scheme.onSurfaceVariant }}>
            {text(props.label) || text(props.name)}
          </span>
          <span className="pv-field-value" style={{ color: ctx.theme.scheme.onSurfaceVariant }}>
            {type === 'password' ? '••••••••' : type === 'otp' ? '••••••' : text(props.placeholder) || ' '}
          </span>
        </div>
      );

    case 'dropdown':
    case 'select':
      return (
        <div className="pv-field" style={{ borderColor: ctx.theme.scheme.outline }}>
          <span className="pv-field-label" style={{ color: ctx.theme.scheme.onSurfaceVariant }}>{text(props.label)}</span>
          <span className="pv-field-value" style={{ color: ctx.theme.scheme.onSurface }}>
            {firstOptionLabel(props.options)} ▾
          </span>
        </div>
      );

    case 'checkbox':
    case 'switch':
    case 'toggle':
      return (
        <div className="pv-toggle-row" style={{ color: ctx.theme.scheme.onSurface }}>
          <span>{text(props.label)}</span>
          <span className="pv-toggle" style={{ background: ctx.theme.scheme.outlineVariant }} />
        </div>
      );

    case 'radiogroup':
      return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, color: ctx.theme.scheme.onSurface }}>
          {props.label ? <span className="pv-field-label">{text(props.label)}</span> : null}
          {(Array.isArray(props.options) ? props.options : []).slice(0, 4).map((option, i) => (
            <span key={i}>◯ {optionLabel(option)}</span>
          ))}
        </div>
      );

    case 'progress':
    case 'loader':
      return props.variant === 'linear' ? (
        <div className="pv-progress-line" style={{ background: ctx.theme.scheme.surfaceVariant }}>
          <div style={{ background: ctx.theme.scheme.primary, width: '45%', height: '100%' }} />
        </div>
      ) : (
        <span className="pv-spinner" style={{ borderTopColor: ctx.theme.scheme.primary }} />
      );

    case 'skeleton':
    case 'shimmer':
      return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {Array.from({ length: (props.lines as number) ?? 1 }).map((_, index) => (
            <div
              key={index}
              className="pv-skeleton"
              style={{
                height: (props.lineHeight as number) ?? 16,
                width: index === ((props.lines as number) ?? 1) - 1 ? '60%' : '100%',
                background: ctx.theme.scheme.surfaceVariant,
              }}
            />
          ))}
        </div>
      );

    case 'empty':
    case 'emptystate':
      return (
        <div className="pv-empty" style={{ color: ctx.theme.scheme.onSurfaceVariant }}>
          <div className="pv-icon" style={{ fontSize: 32 }}>{iconGlyph(text(props.icon) || 'info')}</div>
          <strong style={{ color: ctx.theme.scheme.onSurface }}>{text(props.title)}</strong>
          <span>{text(props.message)}</span>
        </div>
      );

    case 'rating':
      return (
        <span style={{ color: resolveColor(text(style.tint) || 'brandGold', ctx.theme) ?? '#E9A23B', letterSpacing: 2 }}>
          {'★'.repeat(Math.max(0, Math.min(Number(props.value) || 0, Number(props.max) || 5)))}
          <span style={{ opacity: 0.3 }}>
            {'★'.repeat(Math.max(0, (Number(props.max) || 5) - (Number(props.value) || 0)))}
          </span>
        </span>
      );

    default:
      // A component the canvas does not know is still a real component on device;
      // showing a labelled placeholder is more honest than rendering nothing.
      return (
        <span className="pv-unknown">
          {node.type}
          {node.children?.length ? <> · {node.children.length} child(ren)</> : null}
        </span>
      );
  }
}

function emptyHint(node: SduiNode): ReactNode {
  if (node.children?.length) return null;
  return <span className="pv-drop-hint">Drop components here</span>;
}

function flexStyle(direction: 'row' | 'column', props: JsonObject): CSSProperties {
  const justify = text(props.justify);
  const align = text(props.align);
  const map: Record<string, string> = {
    start: 'flex-start', top: 'flex-start', center: 'center', end: 'flex-end',
    bottom: 'flex-end', spaceBetween: 'space-between', spaceAround: 'space-around',
    spaceEvenly: 'space-evenly', stretch: 'stretch',
  };
  return {
    display: 'flex',
    flexDirection: direction,
    gap: `${(props.spacing as number) ?? 0}px`,
    justifyContent: map[justify] ?? 'flex-start',
    alignItems: map[align] ?? (direction === 'column' ? 'stretch' : 'flex-start'),
    width: '100%',
  };
}

function textStyle(style: JsonObject, ctx: Ctx): CSSProperties {
  const role = TYPOGRAPHY[text(style.typography)] ?? TYPOGRAPHY.bodyLarge!;
  return {
    color: resolveColor(text(style.textColor), ctx.theme) ?? ctx.theme.scheme.onSurface,
    fontSize: (style.fontSize as number) ?? role.size,
    fontWeight: FONT_WEIGHTS[text(style.fontWeight)] ?? role.weight,
    lineHeight: `${(style.lineHeight as number) ?? role.lineHeight}px`,
    fontStyle: truthy(style.italic as Json) ? 'italic' : undefined,
    textAlign: (text(style.textAlign) || undefined) as CSSProperties['textAlign'],
    letterSpacing: style.letterSpacing as number | undefined,
    display: '-webkit-box',
    WebkitLineClamp: (style.maxLines as number) ?? undefined,
    WebkitBoxOrient: 'vertical',
    overflow: style.maxLines ? 'hidden' : undefined,
  };
}

/** Web equivalent of `buildModifier`, in the same order. */
function modifierStyle(modifier: JsonObject, theme: ResolvedTheme): CSSProperties {
  const style: CSSProperties = {};

  const margin = spacing(modifier.margin);
  if (margin) style.margin = margin;

  const width = dimension(modifier.width ?? modifier.size);
  if (width) style.width = width;
  const height = dimension(modifier.height ?? modifier.size);
  if (height) style.height = height;
  if (modifier.weight) style.flex = `${modifier.weight} 1 0%`;
  if (modifier.aspectRatio) style.aspectRatio = String(modifier.aspectRatio);
  if (modifier.maxWidth) style.maxWidth = `${modifier.maxWidth}px`;
  if (modifier.maxHeight) style.maxHeight = `${modifier.maxHeight}px`;
  if (modifier.minHeight) style.minHeight = `${modifier.minHeight}px`;

  const radius = cornerRadius(modifier.cornerRadius);
  if (radius !== undefined) style.borderRadius = radius;

  const background = resolveColor(typeof modifier.background === 'string' ? modifier.background : undefined, theme);
  if (background) style.background = background;

  const gradient = modifier.backgroundGradient as { colors?: string[]; type?: string } | undefined;
  if (gradient?.colors && gradient.colors.length > 1) {
    const stops = gradient.colors.map((c) => resolveColor(c, theme) ?? c).join(', ');
    style.background = gradient.type === 'horizontal'
      ? `linear-gradient(to right, ${stops})`
      : `linear-gradient(to bottom, ${stops})`;
  }

  const border = modifier.border as { width?: number; color?: string } | undefined;
  if (border) style.border = `${border.width ?? 1}px solid ${resolveColor(border.color, theme) ?? '#00000022'}`;

  if (modifier.elevation) style.boxShadow = `0 ${Number(modifier.elevation)}px ${Number(modifier.elevation) * 3}px rgba(0,0,0,.16)`;
  if (modifier.alpha !== undefined) style.opacity = Number(modifier.alpha);
  if (modifier.rotation) style.transform = `rotate(${modifier.rotation}deg)`;

  const padding = spacing(modifier.padding);
  if (padding) style.padding = padding;
  if (modifier.clip !== false && radius) style.overflow = 'hidden';

  return style;
}

function spacing(value: unknown): string | undefined {
  if (value === undefined || value === null) return undefined;
  if (typeof value === 'number') return `${value}px`;
  if (typeof value === 'string') return `${Number(value) || 0}px`;
  const v = value as Record<string, number>;
  const all = v.all ?? 0;
  const horizontal = v.horizontal ?? v.h ?? all;
  const vertical = v.vertical ?? v.v ?? all;
  const top = v.top ?? vertical;
  const bottom = v.bottom ?? vertical;
  const start = v.start ?? v.left ?? horizontal;
  const end = v.end ?? v.right ?? horizontal;
  return `${top}px ${end}px ${bottom}px ${start}px`;
}

function cornerRadius(value: unknown): number | undefined {
  if (value === undefined || value === null) return undefined;
  if (typeof value === 'number') return value;
  const v = value as Record<string, number>;
  return v.all ?? v.topStart ?? v.top ?? 0;
}

function dimension(value: unknown): string | undefined {
  if (value === undefined || value === null) return undefined;
  if (typeof value === 'number') return `${value}px`;
  const raw = String(value);
  if (['match_parent', 'match', 'fill', 'full', '100%'].includes(raw)) return '100%';
  if (['wrap_content', 'wrap', 'auto'].includes(raw)) return 'auto';
  if (raw.endsWith('%')) return raw;
  const numeric = Number.parseFloat(raw);
  return Number.isNaN(numeric) ? undefined : `${numeric}px`;
}

function optionLabel(option: Json): string {
  if (option && typeof option === 'object' && !Array.isArray(option)) {
    return text((option as JsonObject).label ?? (option as JsonObject).value);
  }
  return text(option);
}

function firstOptionLabel(options: unknown): string {
  return Array.isArray(options) && options.length > 0 ? optionLabel(options[0] as Json) : 'Select…';
}

/** Placeholder data so a bound list still has shape before real data arrives. */
function samples(count: number): Json[] {
  return Array.from({ length: count }, (_, index) => ({
    id: `sample-${index}`,
    title: `Sample item ${index + 1}`,
    subtitle: 'Bound data appears here',
    amount: (index + 1) * 250,
  }));
}

const ICON_GLYPHS: Record<string, string> = {
  add: '＋', arrowBack: '←', back: '←', arrowForward: '→', forward: '→',
  calendar: '▦', cancel: '✕', camera: '▣', check: '✓', checkCircle: '✓',
  close: '✕', creditCard: '▭', delete: '🗑', download: '↓', edit: '✎',
  email: '✉', favorite: '♥', filter: '⧩', home: '⌂', info: 'ⓘ',
  chevronDown: '▾', chevronUp: '▴', chevronLeft: '‹', chevronRight: '›',
  location: '◉', lock: '🔒', logout: '⇥', menu: '☰', moreVertical: '⋮',
  moreHorizontal: '⋯', notifications: '🔔', person: '☺', profile: '☺',
  phone: '☎', play: '▶', qrCode: '▤', scanQR: '▤', refresh: '↻',
  remove: '－', search: '⌕', send: '➤', settings: '⚙', share: '↗',
  cart: '🛒', star: '★', starOutline: '☆', visible: '👁', hidden: '⃠',
  warning: '⚠',
};

function iconGlyph(name: string): string {
  return ICON_GLYPHS[name] ?? '◇';
}
