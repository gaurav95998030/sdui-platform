import { useState } from 'react';
import { ACTION_TYPES, EVENT_NAMES } from '../catalog';
import { useBuilder, useSelectedNode } from '../store';
import type { JsonObject } from '../types';

type Tab = 'props' | 'layout' | 'style' | 'actions' | 'binding';

/**
 * Property editor for the selected node.
 *
 * Tabs follow the JSON schema's own sections (`props`, `modifier`, `style`,
 * `actions`) rather than inventing a separate vocabulary, so what an author edits
 * here maps one-to-one onto what the JSON tab shows and the device receives.
 */
export function Inspector() {
  const node = useSelectedNode();
  const selected = useBuilder((s) => s.selected);
  const patchNode = useBuilder((s) => s.patchNode);
  const patchSection = useBuilder((s) => s.patchSection);
  const deleteNode = useBuilder((s) => s.deleteNode);
  const [tab, setTab] = useState<Tab>('props');

  if (!node || !selected) {
    return (
      <div className="panel inspector">
        <div className="panel-header"><h3>Inspector</h3></div>
        <p className="muted pad">Select a node on the canvas or in the structure tree.</p>
      </div>
    );
  }

  const props = (node.props ?? {}) as JsonObject;
  const modifier = (node.modifier ?? {}) as JsonObject;
  const style = (node.style ?? {}) as JsonObject;

  const setProp = (key: string, value: unknown) => patchSection(selected, 'props', { [key]: value });
  const setModifier = (key: string, value: unknown) => patchSection(selected, 'modifier', { [key]: value });
  const setStyle = (key: string, value: unknown) => patchSection(selected, 'style', { [key]: value });

  return (
    <div className="panel inspector">
      <div className="panel-header">
        <h3>{node.type}</h3>
        {selected.length > 0 && (
          <button className="danger-link" onClick={() => deleteNode(selected)}>Delete</button>
        )}
      </div>

      <div className="tabs">
        {(['props', 'layout', 'style', 'actions', 'binding'] as Tab[]).map((t) => (
          <button key={t} className={tab === t ? 'active' : ''} onClick={() => setTab(t)}>
            {t}
          </button>
        ))}
      </div>

      <div className="inspector-scroll">
        {tab === 'props' && (
          <>
            <Field label="id" hint="Stable identity for analytics and state binding">
              <input value={node.id ?? ''} onChange={(e) => patchNode(selected, { id: e.target.value || undefined })} />
            </Field>
            {editableProps(node.type).map(({ key, kind, options, hint }) => (
              <Field key={key} label={key} hint={hint}>
                {kind === 'boolean' ? (
                  <input
                    type="checkbox"
                    checked={props[key] === true || props[key] === 'true'}
                    onChange={(e) => setProp(key, e.target.checked)}
                  />
                ) : kind === 'select' ? (
                  <select value={String(props[key] ?? '')} onChange={(e) => setProp(key, e.target.value)}>
                    <option value="">—</option>
                    {options?.map((option) => <option key={option} value={option}>{option}</option>)}
                  </select>
                ) : kind === 'number' ? (
                  <input
                    type="number"
                    value={props[key] === undefined ? '' : String(props[key])}
                    onChange={(e) => setProp(key, e.target.value === '' ? undefined : Number(e.target.value))}
                  />
                ) : (
                  <input
                    value={props[key] === undefined ? '' : String(props[key])}
                    onChange={(e) => setProp(key, e.target.value)}
                    placeholder="text or {{binding}}"
                  />
                )}
              </Field>
            ))}
            <JsonField
              label="All props (raw)"
              value={props}
              onChange={(value) => patchNode(selected, { props: value })}
            />
          </>
        )}

        {tab === 'layout' && (
          <>
            <Field label="width"><DimensionInput value={modifier.width} onChange={(v) => setModifier('width', v)} /></Field>
            <Field label="height"><DimensionInput value={modifier.height} onChange={(v) => setModifier('height', v)} /></Field>
            <Field label="weight" hint="Share of the parent row/column">
              <input type="number" step="0.5" value={modifier.weight === undefined ? '' : String(modifier.weight)}
                     onChange={(e) => setModifier('weight', e.target.value === '' ? undefined : Number(e.target.value))} />
            </Field>
            <Field label="padding"><SpacingInput value={modifier.padding} onChange={(v) => setModifier('padding', v)} /></Field>
            <Field label="margin"><SpacingInput value={modifier.margin} onChange={(v) => setModifier('margin', v)} /></Field>
            <Field label="cornerRadius">
              <input type="number" value={numberOf(modifier.cornerRadius)}
                     onChange={(e) => setModifier('cornerRadius', e.target.value === '' ? undefined : Number(e.target.value))} />
            </Field>
            <Field label="elevation">
              <input type="number" value={modifier.elevation === undefined ? '' : String(modifier.elevation)}
                     onChange={(e) => setModifier('elevation', e.target.value === '' ? undefined : Number(e.target.value))} />
            </Field>
            <Field label="background"><ColorInput value={modifier.background} onChange={(v) => setModifier('background', v)} /></Field>
            <Field label="alignment">
              <select value={String(modifier.alignment ?? '')} onChange={(e) => setModifier('alignment', e.target.value || undefined)}>
                <option value="">—</option>
                {['topStart','topCenter','topEnd','centerStart','center','centerEnd','bottomStart','bottomCenter','bottomEnd']
                  .map((a) => <option key={a} value={a}>{a}</option>)}
              </select>
            </Field>
            <Field label="enabled">
              <input type="checkbox" checked={modifier.enabled !== false}
                     onChange={(e) => setModifier('enabled', e.target.checked ? undefined : false)} />
            </Field>
          </>
        )}

        {tab === 'style' && (
          <>
            <Field label="typography">
              <select value={String(style.typography ?? '')} onChange={(e) => setStyle('typography', e.target.value || undefined)}>
                <option value="">—</option>
                {['displayLarge','displayMedium','displaySmall','headlineLarge','headlineMedium','headlineSmall',
                  'titleLarge','titleMedium','titleSmall','bodyLarge','bodyMedium','bodySmall',
                  'labelLarge','labelMedium','labelSmall'].map((t) => <option key={t} value={t}>{t}</option>)}
              </select>
            </Field>
            <Field label="textColor"><ColorInput value={style.textColor} onChange={(v) => setStyle('textColor', v)} /></Field>
            <Field label="fontSize">
              <input type="number" value={style.fontSize === undefined ? '' : String(style.fontSize)}
                     onChange={(e) => setStyle('fontSize', e.target.value === '' ? undefined : Number(e.target.value))} />
            </Field>
            <Field label="fontWeight">
              <select value={String(style.fontWeight ?? '')} onChange={(e) => setStyle('fontWeight', e.target.value || undefined)}>
                <option value="">—</option>
                {['thin','light','normal','medium','semibold','bold','extrabold','black'].map((w) => <option key={w} value={w}>{w}</option>)}
              </select>
            </Field>
            <Field label="textAlign">
              <select value={String(style.textAlign ?? '')} onChange={(e) => setStyle('textAlign', e.target.value || undefined)}>
                <option value="">—</option>
                {['start','center','end','justify'].map((a) => <option key={a} value={a}>{a}</option>)}
              </select>
            </Field>
            <Field label="maxLines">
              <input type="number" value={style.maxLines === undefined ? '' : String(style.maxLines)}
                     onChange={(e) => setStyle('maxLines', e.target.value === '' ? undefined : Number(e.target.value))} />
            </Field>
            <Field label="variant" hint="Button/card variant: filled, tonal, outlined, text">
              <input value={String(style.variant ?? '')} onChange={(e) => setStyle('variant', e.target.value || undefined)} />
            </Field>
            <Field label="containerColor"><ColorInput value={style.containerColor} onChange={(v) => setStyle('containerColor', v)} /></Field>
            <Field label="contentColor"><ColorInput value={style.contentColor} onChange={(v) => setStyle('contentColor', v)} /></Field>
            <Field label="tint"><ColorInput value={style.tint} onChange={(v) => setStyle('tint', v)} /></Field>
          </>
        )}

        {tab === 'actions' && (
          <ActionEditor
            actions={node.actions ?? {}}
            onChange={(actions) => patchNode(selected, { actions })}
          />
        )}

        {tab === 'binding' && (
          <>
            <Field label="visible" hint="true/false or an expression such as {{cart.count > 0}}">
              <input
                value={node.visible === undefined ? '' : String(node.visible)}
                onChange={(e) => patchNode(selected, { visible: e.target.value || undefined })}
                placeholder="{{user.isPremium}}"
              />
            </Field>
            <Field label="repeat.items" hint="Bind a collection to render this node once per element">
              <input
                value={node.repeat?.items ?? ''}
                onChange={(e) =>
                  patchNode(selected, {
                    repeat: e.target.value
                      ? { ...(node.repeat ?? {}), items: e.target.value }
                      : undefined,
                  })
                }
                placeholder="{{transactions}}"
              />
            </Field>
            {node.repeat && (
              <>
                <Field label="repeat.as">
                  <input value={node.repeat.as ?? 'item'}
                         onChange={(e) => patchNode(selected, { repeat: { ...node.repeat!, as: e.target.value } })} />
                </Field>
                <Field label="repeat.indexAs">
                  <input value={node.repeat.indexAs ?? 'index'}
                         onChange={(e) => patchNode(selected, { repeat: { ...node.repeat!, indexAs: e.target.value } })} />
                </Field>
              </>
            )}
            <Field label="animation.enter">
              <select
                value={String((node.animation as JsonObject | undefined)?.enter ?? '')}
                onChange={(e) =>
                  patchNode(selected, {
                    animation: e.target.value
                      ? { ...(node.animation ?? {}), enter: e.target.value }
                      : undefined,
                  })
                }
              >
                <option value="">—</option>
                {['fade','slideUp','slideDown','slideStart','slideEnd','scale','expand','collapse']
                  .map((a) => <option key={a} value={a}>{a}</option>)}
              </select>
            </Field>
          </>
        )}
      </div>
    </div>
  );
}

function ActionEditor({
  actions,
  onChange,
}: {
  actions: Record<string, JsonObject>;
  onChange: (actions: Record<string, JsonObject>) => void;
}) {
  const entries = Object.entries(actions);
  return (
    <div className="action-editor">
      {entries.length === 0 && <p className="muted">No actions on this node.</p>}
      {entries.map(([event, action]) => (
        <div className="action-card" key={event}>
          <div className="action-card-head">
            <strong>{event}</strong>
            <button
              className="danger-link"
              onClick={() => {
                const next = { ...actions };
                delete next[event];
                onChange(next);
              }}
            >
              remove
            </button>
          </div>
          <Field label="type">
            <select
              value={String(action.type ?? 'noop')}
              onChange={(e) => onChange({ ...actions, [event]: { ...action, type: e.target.value } })}
            >
              {ACTION_TYPES.map((type) => <option key={type} value={type}>{type}</option>)}
            </select>
          </Field>
          <JsonField
            label="params"
            value={(action.params ?? {}) as JsonObject}
            onChange={(params) => onChange({ ...actions, [event]: { ...action, params } })}
          />
          <JsonField
            label="then (chained actions)"
            value={(action.then ?? []) as unknown as JsonObject}
            onChange={(then) => onChange({ ...actions, [event]: { ...action, then: then as never } })}
          />
        </div>
      ))}
      <div className="add-action">
        <select
          defaultValue=""
          onChange={(e) => {
            if (!e.target.value) return;
            onChange({ ...actions, [e.target.value]: { type: 'noop', params: {} } });
            e.target.value = '';
          }}
        >
          <option value="">+ Add event…</option>
          {EVENT_NAMES.filter((name) => !(name in actions)).map((name) => (
            <option key={name} value={name}>{name}</option>
          ))}
        </select>
      </div>
    </div>
  );
}

function Field({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <label className="field">
      <span className="field-label">
        {label}
        {hint && <em title={hint}>?</em>}
      </span>
      {children}
    </label>
  );
}

/** Free-form JSON with validation, so power users are never blocked by the form UI. */
function JsonField({
  label,
  value,
  onChange,
}: {
  label: string;
  value: JsonObject;
  onChange: (value: JsonObject) => void;
}) {
  const [draft, setDraft] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const serialised = JSON.stringify(value, null, 2);

  return (
    <div className="field">
      <span className="field-label">{label}</span>
      <textarea
        className={error ? 'invalid' : ''}
        rows={Math.min(12, serialised.split('\n').length + 1)}
        value={draft ?? serialised}
        onChange={(event) => {
          setDraft(event.target.value);
          try {
            onChange(JSON.parse(event.target.value) as JsonObject);
            setError(null);
          } catch (parseError) {
            setError((parseError as Error).message);
          }
        }}
        onBlur={() => {
          setDraft(null);
          setError(null);
        }}
      />
      {error && <span className="field-error">{error}</span>}
    </div>
  );
}

function DimensionInput({ value, onChange }: { value: unknown; onChange: (value: unknown) => void }) {
  const current = value === undefined ? '' : String(value);
  return (
    <div className="inline">
      <select
        value={['match_parent', 'wrap_content'].includes(current) ? current : 'custom'}
        onChange={(e) => onChange(e.target.value === 'custom' ? 0 : e.target.value)}
      >
        <option value="match_parent">match_parent</option>
        <option value="wrap_content">wrap_content</option>
        <option value="custom">custom…</option>
      </select>
      {!['match_parent', 'wrap_content'].includes(current) && (
        <input
          value={current}
          placeholder="120 or 50%"
          onChange={(e) => {
            const raw = e.target.value;
            const numeric = Number(raw);
            onChange(raw === '' ? undefined : Number.isNaN(numeric) ? raw : numeric);
          }}
        />
      )}
    </div>
  );
}

function SpacingInput({ value, onChange }: { value: unknown; onChange: (value: unknown) => void }) {
  const object = value && typeof value === 'object' ? (value as Record<string, number>) : null;
  return (
    <div className="inline">
      <input
        type="number"
        placeholder="all"
        value={typeof value === 'number' ? String(value) : (object?.all ?? '')}
        onChange={(e) => onChange(e.target.value === '' ? undefined : Number(e.target.value))}
      />
      <input
        type="number"
        placeholder="h"
        value={object?.horizontal ?? ''}
        onChange={(e) =>
          onChange({ ...(object ?? {}), horizontal: e.target.value === '' ? undefined : Number(e.target.value) })
        }
      />
      <input
        type="number"
        placeholder="v"
        value={object?.vertical ?? ''}
        onChange={(e) =>
          onChange({ ...(object ?? {}), vertical: e.target.value === '' ? undefined : Number(e.target.value) })
        }
      />
    </div>
  );
}

/** Accepts theme roles, custom tokens and hex literals — the same set the SDK resolves. */
function ColorInput({ value, onChange }: { value: unknown; onChange: (value: unknown) => void }) {
  const current = value === undefined ? '' : String(value);
  return (
    <div className="inline">
      <input
        value={current}
        placeholder="primary, brandGold, #4C5BD4"
        onChange={(e) => onChange(e.target.value || undefined)}
      />
      <input
        type="color"
        value={current.startsWith('#') ? current.slice(0, 7) : '#4C5BD4'}
        onChange={(e) => onChange(e.target.value)}
      />
    </div>
  );
}

function numberOf(value: unknown): string {
  if (typeof value === 'number') return String(value);
  if (value && typeof value === 'object') {
    const all = (value as Record<string, number>).all;
    return all === undefined ? '' : String(all);
  }
  return '';
}

interface PropDescriptor {
  key: string;
  kind: 'text' | 'number' | 'boolean' | 'select';
  options?: string[];
  hint?: string;
}

/** Curated fields per component; the raw JSON editor below covers everything else. */
function editableProps(type: string): PropDescriptor[] {
  switch (type.toLowerCase()) {
    case 'text':
    case 'label':
      return [{ key: 'text', kind: 'text', hint: 'Supports {{bindings}}' }];
    case 'button':
      return [
        { key: 'text', kind: 'text' },
        { key: 'variant', kind: 'select', options: ['filled', 'tonal', 'outlined', 'text', 'elevated'] },
        { key: 'leadingIcon', kind: 'text' },
        { key: 'trailingIcon', kind: 'text' },
        { key: 'loading', kind: 'text', hint: 'Bind to an API loadingKey, e.g. {{ui.saving}}' },
        { key: 'enabled', kind: 'text', hint: 'true/false or a binding' },
      ];
    case 'image':
      return [
        { key: 'url', kind: 'text' },
        { key: 'contentScale', kind: 'select', options: ['crop', 'fit', 'fillBounds', 'fillWidth', 'inside'] },
        { key: 'alt', kind: 'text' },
      ];
    case 'icon':
    case 'iconbutton':
      return [
        { key: type.toLowerCase() === 'icon' ? 'name' : 'icon', kind: 'text' },
        { key: 'size', kind: 'number' },
      ];
    case 'column':
    case 'row':
      return [
        { key: 'spacing', kind: 'number' },
        { key: 'justify', kind: 'select', options: ['start', 'center', 'end', 'spaceBetween', 'spaceAround', 'spaceEvenly'] },
        { key: 'align', kind: 'select', options: ['start', 'center', 'end', 'stretch'] },
      ];
    case 'lazycolumn':
    case 'lazyrow':
    case 'grid':
      return [
        { key: 'items', kind: 'text', hint: 'Binding to an array, e.g. {{transactions}}' },
        { key: 'spacing', kind: 'number' },
        { key: 'columns', kind: 'number' },
        { key: 'pullToRefresh', kind: 'boolean' },
        { key: 'refreshing', kind: 'text', hint: 'Binding, e.g. {{ui.loading}}' },
      ];
    case 'textfield':
    case 'password':
    case 'number':
    case 'otp':
    case 'datepicker':
    case 'dropdown':
    case 'checkbox':
    case 'switch':
    case 'radiogroup':
      return [
        { key: 'name', kind: 'text', hint: 'Field name used by validateForm' },
        { key: 'label', kind: 'text' },
        { key: 'placeholder', kind: 'text' },
        { key: 'path', kind: 'text', hint: 'State path; defaults to form.<name>' },
        { key: 'form', kind: 'text', hint: 'Form id this field belongs to' },
      ];
    case 'spacer':
      return [{ key: 'height', kind: 'number' }, { key: 'width', kind: 'number' }];
    case 'divider':
      return [{ key: 'thickness', kind: 'number' }, { key: 'orientation', kind: 'select', options: ['horizontal', 'vertical'] }];
    case 'empty':
      return [{ key: 'icon', kind: 'text' }, { key: 'title', kind: 'text' }, { key: 'message', kind: 'text' }];
    case 'skeleton':
      return [{ key: 'lines', kind: 'number' }, { key: 'lineHeight', kind: 'number' }];
    case 'rating':
      return [{ key: 'value', kind: 'text' }, { key: 'max', kind: 'number' }, { key: 'size', kind: 'number' }];
    default:
      return [];
  }
}
