import type { NodePath, SduiNode } from './types';

/**
 * Immutable operations on the component tree.
 *
 * Every edit returns a new root so React re-renders predictably and undo/redo is
 * a matter of keeping previous roots — no structural sharing bugs from in-place
 * mutation of a deeply nested object.
 */

export function nodeAt(root: SduiNode, path: NodePath): SduiNode | undefined {
  let current: SduiNode | undefined = root;
  for (const index of path) {
    current = current?.children?.[index];
    if (!current) return undefined;
  }
  return current;
}

export function parentOf(root: SduiNode, path: NodePath): SduiNode | undefined {
  if (path.length === 0) return undefined;
  return nodeAt(root, path.slice(0, -1));
}

function mapAt(root: SduiNode, path: NodePath, transform: (node: SduiNode) => SduiNode): SduiNode {
  if (path.length === 0) return transform(root);
  const [head, ...rest] = path;
  const children = root.children ?? [];
  if (head === undefined || head < 0 || head >= children.length) return root;
  const next = [...children];
  next[head] = mapAt(children[head]!, rest, transform);
  return { ...root, children: next };
}

export function updateNode(root: SduiNode, path: NodePath, patch: Partial<SduiNode>): SduiNode {
  return mapAt(root, path, (node) => ({ ...node, ...patch }));
}

/** Merges into one of the node's sub-objects (`props`, `modifier`, `style`). */
export function updateNodeSection(
  root: SduiNode,
  path: NodePath,
  section: 'props' | 'modifier' | 'style' | 'animation',
  patch: Record<string, unknown>,
): SduiNode {
  return mapAt(root, path, (node) => {
    const merged = { ...(node[section] ?? {}), ...patch } as Record<string, unknown>;
    // Deleting a key is expressed as `undefined`, so cleared inputs do not persist
    // as empty strings in the published JSON.
    for (const [key, value] of Object.entries(patch)) {
      if (value === undefined || value === '') delete merged[key];
    }
    return { ...node, [section]: merged } as SduiNode;
  });
}

export function insertNode(
  root: SduiNode,
  parentPath: NodePath,
  index: number,
  node: SduiNode,
): SduiNode {
  return mapAt(root, parentPath, (parent) => {
    const children = [...(parent.children ?? [])];
    children.splice(Math.max(0, Math.min(index, children.length)), 0, node);
    return { ...parent, children };
  });
}

export function removeNode(root: SduiNode, path: NodePath): SduiNode {
  if (path.length === 0) return root;
  const parentPath = path.slice(0, -1);
  const index = path[path.length - 1]!;
  return mapAt(root, parentPath, (parent) => {
    const children = [...(parent.children ?? [])];
    children.splice(index, 1);
    return { ...parent, children };
  });
}

export function duplicateNode(root: SduiNode, path: NodePath): SduiNode {
  const node = nodeAt(root, path);
  if (!node || path.length === 0) return root;
  const copy = structuredClone(node);
  if (copy.id) copy.id = `${copy.id}-copy`;
  return insertNode(root, path.slice(0, -1), path[path.length - 1]! + 1, copy);
}

/**
 * Moves a node to a new parent/index.
 *
 * Removing first shifts indices, so when the destination is a later sibling of
 * the same parent the target index is decremented — otherwise dragging an item
 * down by one lands it back where it started.
 */
export function moveNode(
  root: SduiNode,
  from: NodePath,
  toParent: NodePath,
  toIndex: number,
): SduiNode {
  const node = nodeAt(root, from);
  if (!node || from.length === 0) return root;
  if (isAncestor(from, toParent)) return root; // never drop a node inside itself

  const detached = removeNode(root, from);
  const sameParent = from.slice(0, -1).join('.') === toParent.join('.');
  const adjusted = sameParent && from[from.length - 1]! < toIndex ? toIndex - 1 : toIndex;
  return insertNode(detached, toParent, adjusted, node);
}

export function isAncestor(ancestor: NodePath, descendant: NodePath): boolean {
  if (ancestor.length > descendant.length) return false;
  return ancestor.every((value, index) => descendant[index] === value);
}

export function moveWithinParent(root: SduiNode, path: NodePath, delta: number): SduiNode {
  if (path.length === 0) return root;
  const index = path[path.length - 1]!;
  return moveNode(root, path, path.slice(0, -1), index + delta + (delta > 0 ? 1 : 0));
}

/** Depth-first walk, yielding each node with its path. */
export function walk(
  root: SduiNode,
  visit: (node: SduiNode, path: NodePath) => void,
  path: NodePath = [],
): void {
  visit(root, path);
  root.children?.forEach((child, index) => walk(child, visit, [...path, index]));
}

export function countNodes(root: SduiNode): number {
  let total = 0;
  walk(root, () => total++);
  return total;
}
