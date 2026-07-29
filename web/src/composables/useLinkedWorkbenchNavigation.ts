import { computed, ref, type ComputedRef, type MaybeRefOrGetter, toValue } from 'vue';

export interface LinkedWorkbenchScope {
  workspaceId: string;
  repositoryId: string;
  revision: string;
}

export interface LinkedWorkbenchSnapshot extends LinkedWorkbenchScope {
  version: 1;
  filePath: string | null;
  documentId: string | null;
}

export interface LinkedWorkbenchNavigationState {
  current: LinkedWorkbenchSnapshot | null;
  backStack: LinkedWorkbenchSnapshot[];
  forwardStack: LinkedWorkbenchSnapshot[];
}

export interface LinkedWorkbenchNavigation {
  state: ComputedRef<LinkedWorkbenchNavigationState>;
  canGoBack: ComputedRef<boolean>;
  canGoForward: ComputedRef<boolean>;
  backTarget: ComputedRef<LinkedWorkbenchSnapshot | null>;
  forwardTarget: ComputedRef<LinkedWorkbenchSnapshot | null>;
  restoreCurrent: (scope?: LinkedWorkbenchScope | null) => LinkedWorkbenchSnapshot | null;
  restoreLastScope: (workspaceId: string) => LinkedWorkbenchScope | null;
  updateCurrent: (snapshot: LinkedWorkbenchSnapshot) => void;
  navigateTo: (snapshot: LinkedWorkbenchSnapshot) => void;
  goBack: () => LinkedWorkbenchSnapshot | null;
  goForward: () => LinkedWorkbenchSnapshot | null;
  clearForScope: (scope?: LinkedWorkbenchScope | null) => void;
}

const STORAGE_PREFIX = 'devcollab.linked-workbench.navigation.v1';
const LAST_SCOPE_PREFIX = 'devcollab.linked-workbench.last-scope.v1';
const MAX_HISTORY = 20;
const memoryStates = new Map<string, LinkedWorkbenchNavigationState>();
const memoryLastScopes = new Map<string, LinkedWorkbenchScope>();
const stateRevision = ref(0);

function emptyState(): LinkedWorkbenchNavigationState {
  return { current: null, backStack: [], forwardStack: [] };
}

function scopeKey(scope: LinkedWorkbenchScope) {
  return [scope.workspaceId, scope.repositoryId, scope.revision]
    .map(value => encodeURIComponent(value))
    .join(':');
}

function storageKey(scope: LinkedWorkbenchScope) {
  return `${STORAGE_PREFIX}:${scopeKey(scope)}`;
}

function lastScopeKey(workspaceId: string) {
  return `${LAST_SCOPE_PREFIX}:${encodeURIComponent(workspaceId)}`;
}

function storage(): Storage | null {
  return typeof window === 'undefined' ? null : window.sessionStorage;
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0;
}

function isNullableString(value: unknown): value is string | null {
  return value === null || typeof value === 'string';
}

function isScope(value: unknown): value is LinkedWorkbenchScope {
  if (!value || typeof value !== 'object') return false;
  const candidate = value as Partial<LinkedWorkbenchScope>;
  return isNonEmptyString(candidate.workspaceId)
    && isNonEmptyString(candidate.repositoryId)
    && isNonEmptyString(candidate.revision);
}

function isSnapshot(value: unknown, scope?: LinkedWorkbenchScope): value is LinkedWorkbenchSnapshot {
  if (!isScope(value)) return false;
  const candidate = value as Partial<LinkedWorkbenchSnapshot>;
  return candidate.version === 1
    && isNullableString(candidate.filePath)
    && isNullableString(candidate.documentId)
    && (!scope
      || (candidate.workspaceId === scope.workspaceId
        && candidate.repositoryId === scope.repositoryId
        && candidate.revision === scope.revision));
}

function normalizeState(value: unknown, scope: LinkedWorkbenchScope): LinkedWorkbenchNavigationState {
  if (!value || typeof value !== 'object') return emptyState();
  const candidate = value as Partial<LinkedWorkbenchNavigationState>;
  return {
    current: isSnapshot(candidate.current, scope) ? candidate.current : null,
    backStack: Array.isArray(candidate.backStack)
      ? candidate.backStack.filter(item => isSnapshot(item, scope)).slice(-MAX_HISTORY)
      : [],
    forwardStack: Array.isArray(candidate.forwardStack)
      ? candidate.forwardStack.filter(item => isSnapshot(item, scope)).slice(-MAX_HISTORY)
      : [],
  };
}

function readState(scope: LinkedWorkbenchScope): LinkedWorkbenchNavigationState {
  const key = scopeKey(scope);
  const cached = memoryStates.get(key);
  if (cached) return cached;

  let result = emptyState();
  try {
    const raw = storage()?.getItem(storageKey(scope));
    if (raw) result = normalizeState(JSON.parse(raw), scope);
  } catch {
    result = emptyState();
  }
  memoryStates.set(key, result);
  return result;
}

function persistState(scope: LinkedWorkbenchScope, state: LinkedWorkbenchNavigationState) {
  const persistedScope: LinkedWorkbenchScope = {
    workspaceId: scope.workspaceId,
    repositoryId: scope.repositoryId,
    revision: scope.revision,
  };
  memoryStates.set(scopeKey(scope), state);
  try {
    storage()?.setItem(storageKey(scope), JSON.stringify(state));
    storage()?.setItem(lastScopeKey(scope.workspaceId), JSON.stringify(persistedScope));
  } catch {
    // Session persistence is best effort; in-memory navigation remains available.
  }
  memoryLastScopes.set(scope.workspaceId, persistedScope);
  stateRevision.value += 1;
}

function sameTarget(left: LinkedWorkbenchSnapshot | null, right: LinkedWorkbenchSnapshot | null) {
  return Boolean(left && right
    && left.workspaceId === right.workspaceId
    && left.repositoryId === right.repositoryId
    && left.revision === right.revision
    && left.filePath === right.filePath
    && left.documentId === right.documentId);
}

function trimHistory(items: LinkedWorkbenchSnapshot[]) {
  return items.slice(-MAX_HISTORY);
}

export function createLinkedWorkbenchSnapshot(
  scope: LinkedWorkbenchScope,
  filePath: string | null,
  documentId: string | null,
): LinkedWorkbenchSnapshot {
  return { version: 1, ...scope, filePath, documentId };
}

export function resetLinkedWorkbenchNavigationMemoryForTests() {
  memoryStates.clear();
  memoryLastScopes.clear();
  stateRevision.value += 1;
}

export function useLinkedWorkbenchNavigation(
  activeScope: MaybeRefOrGetter<LinkedWorkbenchScope | null>,
): LinkedWorkbenchNavigation {
  const scope = () => toValue(activeScope);
  const state = computed(() => {
    void stateRevision.value;
    const currentScope = scope();
    return currentScope ? readState(currentScope) : emptyState();
  });
  const canGoBack = computed(() => state.value.backStack.length > 0);
  const canGoForward = computed(() => state.value.forwardStack.length > 0);
  const backTarget = computed(() => state.value.backStack.at(-1) ?? null);
  const forwardTarget = computed(() => state.value.forwardStack.at(-1) ?? null);

  function restoreCurrent(targetScope = scope()) {
    return targetScope ? readState(targetScope).current : null;
  }

  function restoreLastScope(workspaceId: string) {
    const cached = memoryLastScopes.get(workspaceId);
    if (cached) return cached;
    try {
      const raw = storage()?.getItem(lastScopeKey(workspaceId));
      if (!raw) return null;
      const parsed: unknown = JSON.parse(raw);
      if (!isScope(parsed) || parsed.workspaceId !== workspaceId) return null;
      memoryLastScopes.set(workspaceId, parsed);
      return parsed;
    } catch {
      return null;
    }
  }

  function updateCurrent(snapshot: LinkedWorkbenchSnapshot) {
    if (!isSnapshot(snapshot)) return;
    const currentState = readState(snapshot);
    persistState(snapshot, { ...currentState, current: snapshot });
  }

  function navigateTo(snapshot: LinkedWorkbenchSnapshot) {
    if (!isSnapshot(snapshot)) return;
    const currentState = readState(snapshot);
    if (sameTarget(currentState.current, snapshot)) {
      persistState(snapshot, { ...currentState, current: snapshot });
      return;
    }
    const backStack = currentState.current
      ? trimHistory([...currentState.backStack, currentState.current])
      : currentState.backStack;
    persistState(snapshot, { current: snapshot, backStack, forwardStack: [] });
  }

  function goBack() {
    const currentScope = scope();
    if (!currentScope) return null;
    const currentState = readState(currentScope);
    const target = currentState.backStack.at(-1) ?? null;
    if (!target) return null;
    const forwardStack = currentState.current
      ? trimHistory([...currentState.forwardStack, currentState.current])
      : currentState.forwardStack;
    persistState(currentScope, {
      current: target,
      backStack: currentState.backStack.slice(0, -1),
      forwardStack,
    });
    return target;
  }

  function goForward() {
    const currentScope = scope();
    if (!currentScope) return null;
    const currentState = readState(currentScope);
    const target = currentState.forwardStack.at(-1) ?? null;
    if (!target) return null;
    const backStack = currentState.current
      ? trimHistory([...currentState.backStack, currentState.current])
      : currentState.backStack;
    persistState(currentScope, {
      current: target,
      backStack,
      forwardStack: currentState.forwardStack.slice(0, -1),
    });
    return target;
  }

  function clearForScope(targetScope = scope()) {
    if (!targetScope) return;
    memoryStates.delete(scopeKey(targetScope));
    try {
      storage()?.removeItem(storageKey(targetScope));
    } catch {
      // Ignore unavailable session storage.
    }
    stateRevision.value += 1;
  }

  return {
    state,
    canGoBack,
    canGoForward,
    backTarget,
    forwardTarget,
    restoreCurrent,
    restoreLastScope,
    updateCurrent,
    navigateTo,
    goBack,
    goForward,
    clearForScope,
  };
}
