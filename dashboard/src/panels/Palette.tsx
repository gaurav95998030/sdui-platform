import { useState } from 'react';
import { CATALOG, GROUPS } from '../catalog';
import { useBuilder } from '../store';

/**
 * The component library.
 *
 * Entries are dragged onto the canvas or the tree. Both drop targets read the
 * same `application/sdui` payload, so there is one insertion path regardless of
 * where the designer lets go.
 */
export function Palette() {
  const [query, setQuery] = useState('');
  const addNode = useBuilder((s) => s.addNode);
  const doc = useBuilder((s) => s.doc);
  const selected = useBuilder((s) => s.selected);

  const filtered = CATALOG.filter(
    (entry) =>
      entry.label.toLowerCase().includes(query.toLowerCase()) ||
      entry.type.toLowerCase().includes(query.toLowerCase()),
  );

  /** Click-to-add drops into the selected container, or the root as a fallback. */
  const quickAdd = (template: () => ReturnType<(typeof CATALOG)[number]['template']>) => {
    if (!doc) return;
    const parent = selected ?? [];
    addNode(parent, 999, template());
  };

  return (
    <div className="panel palette">
      <div className="panel-header">
        <h3>Components</h3>
      </div>
      <input
        className="search"
        placeholder="Search components…"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
      />
      <div className="palette-scroll">
        {GROUPS.map((group) => {
          const entries = filtered.filter((entry) => entry.group === group);
          if (entries.length === 0) return null;
          return (
            <div key={group} className="palette-group">
              <div className="palette-group-title">{group}</div>
              <div className="palette-grid">
                {entries.map((entry) => (
                  <button
                    key={entry.type}
                    className="palette-item"
                    draggable
                    onDragStart={(event) => {
                      event.dataTransfer.setData(
                        'application/sdui',
                        JSON.stringify({ template: entry.template() }),
                      );
                      event.dataTransfer.effectAllowed = 'copy';
                    }}
                    onClick={() => quickAdd(entry.template)}
                    title={`${entry.label} — drag onto the canvas or click to add`}
                  >
                    <span className="palette-icon">{entry.icon}</span>
                    <span>{entry.label}</span>
                  </button>
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
