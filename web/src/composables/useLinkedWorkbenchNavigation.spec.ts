import { beforeEach, describe, expect, it } from 'vitest';
import { ref } from 'vue';

import {
  createLinkedWorkbenchSnapshot,
  resetLinkedWorkbenchNavigationMemoryForTests,
  useLinkedWorkbenchNavigation,
  type LinkedWorkbenchScope,
} from './useLinkedWorkbenchNavigation';

const scope: LinkedWorkbenchScope = {
  workspaceId: 'workspace-1',
  repositoryId: 'repository-1',
  revision: 'revision-1',
};
const otherRepository = { ...scope, repositoryId: 'repository-2' };
const otherRevision = { ...scope, revision: 'revision-2' };

describe('useLinkedWorkbenchNavigation', () => {
  beforeEach(() => {
    sessionStorage.clear();
    resetLinkedWorkbenchNavigationMemoryForTests();
  });

  it('keeps current reading target without manufacturing history during restore', () => {
    const navigation = createNavigation();
    navigation.updateCurrent(snapshot('A.java', 'document-a'));
    navigation.updateCurrent(snapshot('A.java', 'document-a'));

    expect(navigation.restoreCurrent()).toEqual(snapshot('A.java', 'document-a'));
    expect(navigation.state.value.backStack).toEqual([]);
  });

  it('moves A to back when navigating to B and supports back and forward', () => {
    const navigation = createNavigation();
    navigation.updateCurrent(snapshot('A.java', 'document-a'));
    navigation.navigateTo(snapshot('B.java', 'document-b'));

    expect(navigation.state.value.backStack).toEqual([snapshot('A.java', 'document-a')]);
    expect(navigation.state.value.forwardStack).toEqual([]);
    expect(navigation.goBack()).toEqual(snapshot('A.java', 'document-a'));
    expect(navigation.goForward()).toEqual(snapshot('B.java', 'document-b'));
  });

  it('clears forward history when opening C after going back to A', () => {
    const navigation = createNavigation();
    navigation.updateCurrent(snapshot('A.java', 'document-a'));
    navigation.navigateTo(snapshot('B.java', 'document-b'));
    navigation.goBack();
    navigation.navigateTo(snapshot('C.java', 'document-c'));

    expect(navigation.state.value.current).toEqual(snapshot('C.java', 'document-c'));
    expect(navigation.state.value.forwardStack).toEqual([]);
  });

  it('does not duplicate consecutive identical targets', () => {
    const navigation = createNavigation();
    navigation.navigateTo(snapshot('A.java', 'document-a'));
    navigation.navigateTo(snapshot('A.java', 'document-a'));
    expect(navigation.state.value.backStack).toEqual([]);
  });

  it('restores state from sessionStorage after memory is cleared', () => {
    const navigation = createNavigation();
    navigation.updateCurrent(snapshot('A.java', 'document-a'));
    navigation.navigateTo(snapshot('B.java', 'document-b'));
    resetLinkedWorkbenchNavigationMemoryForTests();

    const restored = createNavigation();
    expect(restored.restoreCurrent()).toEqual(snapshot('B.java', 'document-b'));
    expect(restored.state.value.backStack).toEqual([snapshot('A.java', 'document-a')]);
    expect(restored.restoreLastScope(scope.workspaceId)).toEqual(scope);
  });

  it('isolates repository and revision scopes', () => {
    const navigation = createNavigation();
    navigation.updateCurrent(snapshot('A.java', 'document-a'));

    const repositoryNavigation = useLinkedWorkbenchNavigation(ref(otherRepository));
    repositoryNavigation.updateCurrent(createLinkedWorkbenchSnapshot(
      otherRepository,
      'R.java',
      'document-r',
    ));
    const revisionNavigation = useLinkedWorkbenchNavigation(ref(otherRevision));

    expect(navigation.restoreCurrent()).toEqual(snapshot('A.java', 'document-a'));
    expect(repositoryNavigation.restoreCurrent()).toEqual(createLinkedWorkbenchSnapshot(
      otherRepository,
      'R.java',
      'document-r',
    ));
    expect(revisionNavigation.restoreCurrent()).toBeNull();
  });

  it('ignores corrupted or incompatible session data', () => {
    sessionStorage.setItem(
      'devcollab.linked-workbench.navigation.v1:workspace-1:repository-1:revision-1',
      '{"current":{"version":2},"backStack":"invalid"}',
    );
    expect(createNavigation().restoreCurrent()).toBeNull();
  });

  it('caps back history at twenty entries', () => {
    const navigation = createNavigation();
    for (let index = 0; index < 25; index += 1) {
      navigation.navigateTo(snapshot(`${index}.java`, `document-${index}`));
    }
    expect(navigation.state.value.backStack).toHaveLength(20);
    expect(navigation.state.value.backStack[0].filePath).toBe('4.java');
  });
});

function createNavigation() {
  return useLinkedWorkbenchNavigation(ref(scope));
}

function snapshot(filePath: string, documentId: string) {
  return createLinkedWorkbenchSnapshot(scope, filePath, documentId);
}
