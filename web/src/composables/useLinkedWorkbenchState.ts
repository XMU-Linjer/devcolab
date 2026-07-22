import { computed, ref } from 'vue';

import type { DocumentBlock } from '@/api/block';
import type {
  CodeAnchor,
  CodeDocumentLink,
  EngineeringIssue,
  LinkedEvidence,
  LinkedFixture,
  LinkActivationSource,
  WorkbenchMode,
} from '@/types/linkedWorkbench';

export function useLinkedWorkbenchState() {
  const mode = ref<WorkbenchMode>('LINKED');
  const inspectorOpen = ref(true);
  const activeLinkId = ref<string | null>(null);
  const lastActivationSource = ref<LinkActivationSource>('system');

  const selectedRepositoryId = ref('');
  const selectedFilePath = ref('');
  const selectedDocumentId = ref('');

  const codeAnchors = ref<CodeAnchor[]>([]);
  const documentBlocks = ref<DocumentBlock[]>([]);
  const links = ref<CodeDocumentLink[]>([]);
  const issues = ref<EngineeringIssue[]>([]);
  const evidence = ref<LinkedEvidence[]>([]);

  const activeLink = computed(() => links.value.find(link => link.id === activeLinkId.value) ?? null);
  const activeCodeAnchor = computed(() => codeAnchors.value.find(
    anchor => anchor.id === activeLink.value?.codeAnchorId,
  ) ?? null);
  const activeDocumentBlock = computed(() => documentBlocks.value.find(
    block => block.id === activeLink.value?.blockId,
  ) ?? null);
  const activeIssue = computed(() => issues.value.find(issue => issue.linkId === activeLinkId.value) ?? null);
  const activeEvidence = computed(() => evidence.value.filter(item => item.linkId === activeLinkId.value));
  const driftedLinkIds = computed(() => links.value
    .filter((link) => link.relationType === 'CONFLICTS_WITH'
      || codeAnchors.value.some(anchor => anchor.id === link.codeAnchorId && anchor.status !== 'VALID'))
    .map(link => link.id));

  function activateLink(linkId: string, source: LinkActivationSource) {
    if (!links.value.some(link => link.id === linkId)) return;
    activeLinkId.value = linkId;
    lastActivationSource.value = source;
  }

  function setMode(nextMode: WorkbenchMode) {
    mode.value = nextMode;
    if (nextMode !== 'DRIFT_REVIEW') return;
    inspectorOpen.value = true;
    if (!activeLinkId.value || !driftedLinkIds.value.includes(activeLinkId.value)) {
      const firstDrift = driftedLinkIds.value[0];
      if (firstDrift) activateLink(firstDrift, 'system');
    }
  }

  function toggleInspector(force?: boolean) {
    inspectorOpen.value = force ?? !inspectorOpen.value;
  }

  function selectFile(path: string) {
    selectedFilePath.value = path;
  }

  function selectDocument(documentId: string) {
    selectedDocumentId.value = documentId;
  }

  function replaceFixture(fixture: LinkedFixture) {
    codeAnchors.value = fixture.codeAnchors;
    links.value = fixture.links;
    issues.value = fixture.issues;
    evidence.value = fixture.evidence;
    if (!activeLinkId.value || !links.value.some(link => link.id === activeLinkId.value)) {
      activeLinkId.value = links.value[0]?.id ?? null;
      lastActivationSource.value = 'system';
    }
  }

  function replaceDocumentBlocks(blocks: DocumentBlock[]) {
    documentBlocks.value = blocks;
  }

  return {
    mode,
    inspectorOpen,
    activeLinkId,
    lastActivationSource,
    selectedRepositoryId,
    selectedFilePath,
    selectedDocumentId,
    codeAnchors,
    documentBlocks,
    links,
    issues,
    evidence,
    activeLink,
    activeCodeAnchor,
    activeDocumentBlock,
    activeIssue,
    activeEvidence,
    driftedLinkIds,
    activateLink,
    setMode,
    toggleInspector,
    selectFile,
    selectDocument,
    replaceFixture,
    replaceDocumentBlocks,
  };
}
