import type { DragEvent } from 'react';
import { entryFor, isContainer } from '../catalog';
import { useBuilder } from '../store';
import type { NodePath, SduiNode } from '../types';
import { isSamePath, pathKey } from '../types';

/**
 * Structural view of the tree.
 *
 * It is the reliable way to reach nodes the canvas makes hard to hit — a zero-size
 * spacer, a node hidden behind a `visible` guard, or a deeply nested container.
 */
export function TreePanel() {
  const doc = useBuilder((s) => s.doc);
  if (!doc) return <div className="panel"><div className="panel-header"><h3>Tree</h3></div></div>;

  return (
    <div className="panel tree">
      <div className="panel-header">
        <h3>Structure</h3>
      </div>
      <div className="tree-scroll">
        <TreeRow node={doc.root} path={[]} depth={0} />
      </div>
    </div>
  );
}

function TreeRow({ node, path, depth }: { node: SduiNode; path: NodePath; depth: number }) {
  const selected = useBuilder((s) => s.selected);
  const select = useBuilder((s) => s.select);
  const dropNode = useBuilder((s) => s.dropNode);
  const addNode = useBuilder((s) => s.addNode);
  const deleteNode = useBuilder((s) => s.deleteNode);
  const duplicate = useBuilder((s) => s.duplicate);

  const isSelected = isSamePath(selected, path);
  const entry = entryFor(node.type);
  const container = isContainer(node.type);

  const onDrop = (event: DragEvent) => {
    event.stopPropagation();
    event.preventDefault();
    const payload = event.dataTransfer.getData('application/sdui');
    if (!payload) return;
    const parsed = JSON.parse(payload) as { template?: SduiNode; from?: NodePath };

    // Dropping onto a container appends inside it; dropping onto a leaf inserts
    // after that leaf, which is what a designer expects from a list.
    const targetParent = container ? path : path.slice(0, -1);
    const targetIndex = container ? (node.children?.length ?? 0) : path[path.length - 1]! + 1;

    if (parsed.from) dropNode(parsed.from, targetParent, targetIndex);
    else if (parsed.template) addNode(targetParent, targetIndex, parsed.template);
  };

  return (
    <div className="tree-branch">
      <div
        className={`tree-row${isSelected ? ' selected' : ''}`}
        style={{ paddingLeft: 8 + depth * 14 }}
        draggable={path.length > 0}
        onDragStart={(event) => {
          event.stopPropagation();
          event.dataTransfer.setData('application/sdui', JSON.stringify({ from: path }));
        }}
        onDragOver={(event) => {
          event.stopPropagation();
          event.preventDefault();
        }}
        onDrop={onDrop}
        onClick={() => select(path)}
      >
        <span className="tree-icon">{entry?.icon ?? '◇'}</span>
        <span className="tree-label">{node.type}</span>
        {node.id && <span className="tree-id">#{node.id}</span>}
        {node.repeat && <span className="tree-tag">repeat</span>}
        {node.visible !== undefined && <span className="tree-tag">visible</span>}
        {node.actions && Object.keys(node.actions).length > 0 && <span className="tree-tag">action</span>}

        {path.length > 0 && (
          <span className="tree-actions">
            <button title="Duplicate" onClick={(e) => { e.stopPropagation(); duplicate(path); }}>⧉</button>
            <button title="Delete" onClick={(e) => { e.stopPropagation(); deleteNode(path); }}>✕</button>
          </span>
        )}
      </div>
      {node.children?.map((child, index) => (
        <TreeRow key={child.id ?? pathKey([...path, index])} node={child} path={[...path, index]} depth={depth + 1} />
      ))}
    </div>
  );
}
