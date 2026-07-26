<template>
  <main class="app-shell linked-page-shell" :class="{ 'is-sidebar-collapsed': sidebarCollapsed }">
    <AppSidebar
      v-model="sidebarCollapsed"
      active="code"
      :workspace-id="workspaceId"
      :linked-navigation-active="sidebarNavigationActive"
      :linked-count="links.length"
      :review-count="pendingReviewCount"
      :drift-count="driftedLinkIds.length"
      @open-workspace="handleWorkspaceNavigation"
      @open-linked="handleLinkedNavigation"
      @open-review="handleReviewNavigation"
      @open-drift="handleModeChange('DRIFT_REVIEW')"
    >
      <template #workspace-panel>
        <LinkedRepositoryContext
          :repositories="repositories"
          :repository-id="selectedRepositoryId"
          :file-tree="fileTree"
          :files-count="files.length"
          :selected-file-path="selectedFilePath"
          :documents="relatedDocumentChoices"
          :selected-document-id="selectedDocumentId"
          :active-anchor="activeCodeAnchor"
          :file-link-counts="fileLinkCounts"
          :linked-block-count="activeLinkedBlockCount"
          :unresolved-issue-count="activeUnresolvedIssueCount"
          :recent-commit-count="recentCommitCount"
          :loading="contextLoading"
          @select-repository="handleRepositoryChange"
          @select-file="openSourceByPath"
          @select-document="handleDocumentChange"
        />
      </template>
    </AppSidebar>

    <section class="linked-page-main">
      <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />
      <el-alert
        v-if="activeRepository?.lastSyncError"
        :title="activeRepository.lastSyncError"
        type="warning"
        show-icon
        :closable="false"
      />
      <LinkedWorkbenchShell
        v-if="workspace"
        ref="workbenchShellRef"
        :workspace-name="workspace.name"
        :repository-name="activeRepository?.name"
        :branch="activeRepository?.defaultBranch"
        :commit-sha="selectedSource?.commitSha || activeRepository?.lastSyncedCommit"
        :mode="mode"
        :inspector-open="inspectorOpen"
        :source-content="selectedSource?.content || ''"
        :source-path="selectedSource?.path || selectedFilePath"
        :source-language="selectedSource?.language"
        :source-loading="sourceLoading"
        :anchors="codeAnchors"
        :links="links"
        :issues="issues"
        :active-link-id="activeLinkId"
        :document="document"
        :active-block-id="activeDocumentBlock?.id || null"
        :readonly="documentReadonly"
        :document-loading="documentLoading"
        :remote-block="collaborationRemoteBlock"
        :editing-states="collaborationEditingStates"
        :save-via-collaboration="saveBlockViaCollaboration"
        :active-link="activeLink"
        :active-anchor="activeCodeAnchor"
        :active-block="activeDocumentBlock"
        :active-issue="activeIssue"
        :active-evidence="activeEvidence"
        :versions="versions"
        :collaboration-connected="collaborationConnected"
        :members-count="collaborationMembers.length"
        @set-mode="handleModeChange"
        @toggle-inspector="toggleInspector()"
        @activate-code="handleActivate($event, 'code')"
        @activate-rail="handleActivate($event, 'rail')"
        @activate-inspector="handleActivate($event, 'inspector')"
        @select-block="handleBlockSelection"
        @blocks-loaded="handleBlocksLoaded"
        @editing-start="startEditing"
        @editing-stop="stopEditing"
      >
        <template #header-actions>
          <NotificationCenter />
          <el-button size="small" @click="router.push('/workspaces')">返回列表</el-button>
          <el-button
            v-if="workspace.currentUserRole === 'ADMIN' && activeRepository"
            size="small"
            :loading="syncing"
            :disabled="activeRepository.syncStatus === 'SYNC_PENDING' || activeRepository.syncStatus === 'SYNCING'"
            @click="handleSync"
          >重新同步</el-button>
        </template>
      </LinkedWorkbenchShell>
      <el-skeleton v-else :rows="12" animated />
    </section>
  </main>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import type { DocumentBlock, DocumentBlockContent } from '@/api/block';
import { getPendingDocumentChangeCount } from '@/api/documentChange';
import {
  getDocument,
  listDocumentTree,
  listDocumentVersions,
  type DocumentSummary,
  type DocumentTreeNode,
  type DocumentVersion,
} from '@/api/document';
import {
  getGitRepositorySource,
  listGitChanges,
  listGitRepositories,
  listGitRepositoryFiles,
  syncGitRepository,
  type GitRepository,
  type GitChange,
  type GitRepositoryFile,
  type GitRepositorySource,
} from '@/api/git';
import { getWorkspace, type Workspace } from '@/api/workspace';
import AppSidebar from '@/components/layout/AppSidebar.vue';
import LinkedRepositoryContext from '@/components/linked-workbench/LinkedRepositoryContext.vue';
import LinkedWorkbenchShell from '@/components/linked-workbench/LinkedWorkbenchShell.vue';
import NotificationCenter from '@/components/notification/NotificationCenter.vue';
import { useDocumentCollaboration } from '@/composables/useDocumentCollaboration';
import { useLinkedWorkbenchState } from '@/composables/useLinkedWorkbenchState';
import { buildLinkedWorkbenchFixture } from '@/fixtures/linkedWorkbenchMock';
import type { LinkedDocumentChoice, LinkActivationSource, WorkbenchMode } from '@/types/linkedWorkbench';
import { readableError } from '@/utils/error';
import { focusPlan, linkIdForBlock } from '@/utils/linkedWorkbenchInteraction';
import { buildRepositoryTree } from '@/utils/repositoryTree';

const route = useRoute();
const router = useRouter();
const workspaceId = computed(() => String(route.params.workspaceId || ''));
const workspace = ref<Workspace | null>(null);
const repositories = ref<GitRepository[]>([]);
const files = ref<GitRepositoryFile[]>([]);
const gitChanges = ref<GitChange[]>([]);
const documentTree = ref<DocumentTreeNode[]>([]);
const document = ref<DocumentSummary | null>(null);
const versions = ref<DocumentVersion[]>([]);
const selectedSource = ref<GitRepositorySource | null>(null);
const contextLoading = ref(false);
const sourceLoading = ref(false);
const documentLoading = ref(false);
const syncing = ref(false);
const errorMessage = ref('');
const sidebarCollapsed = ref(localStorage.getItem('devcollab.sidebar.collapsed') === 'true');
const sidebarNavigationActive = ref<'linked' | 'review' | 'drift'>('linked');
const workbenchShellRef = ref<InstanceType<typeof LinkedWorkbenchShell> | null>(null);

const state = useLinkedWorkbenchState();
const {
  mode, inspectorOpen, activeLinkId, selectedRepositoryId, selectedFilePath, selectedDocumentId,
  codeAnchors, documentBlocks, links, issues, activeLink, activeCodeAnchor, activeDocumentBlock,
  activeIssue, activeEvidence, driftedLinkIds,
  toggleInspector,
} = state;

const activeRepository = computed(() => repositories.value.find(item => item.id === selectedRepositoryId.value) ?? null);
const fileTree = computed(() => buildRepositoryTree(files.value));
const documentChoices = computed(() => flattenDocumentTree(documentTree.value));
const latestVersion = computed(() => versions.value.reduce(
  (latest, item) => Math.max(latest, item.versionNo),
  0,
));
const relatedDocumentChoices = computed<LinkedDocumentChoice[]>(() => {
  const selected = documentChoices.value.find(item => item.id === selectedDocumentId.value);
  if (!selected) return documentChoices.value.slice(0, 3);
  return [{
    ...selected,
    version: latestVersion.value || undefined,
    reviewStatus: document.value?.reviewStatus,
  }];
});
const pendingReviewCount = ref(0);
const fileLinkCounts = computed<Record<string, number>>(() => selectedFilePath.value
  ? { [selectedFilePath.value]: links.value.length }
  : {});
const activeLinkedBlockCount = computed(() => activeCodeAnchor.value
  ? links.value.filter(link => link.codeAnchorId === activeCodeAnchor.value?.id).length
  : 0);
const activeUnresolvedIssueCount = computed(() => activeCodeAnchor.value
  ? issues.value.filter(issue => issue.codeAnchorId === activeCodeAnchor.value?.id
    && issue.status === 'OPEN').length
  : 0);
const recentCommitCount = computed(() => gitChanges.value.filter(change =>
  change.files.some(file => file.path === selectedFilePath.value || file.oldPath === selectedFilePath.value),
).length);
const documentReadonly = computed(() => document.value?.reviewStatus === 'DEPRECATED' || document.value?.reviewStatus === 'SUPERSEDED');

const {
  connected: collaborationConnected,
  members: collaborationMembers,
  editingStates: collaborationEditingStates,
  latestRemoteBlock: collaborationRemoteBlock,
  startEditing,
  stopEditing,
  updateContent: updateBlockContentViaCollaboration,
} = useDocumentCollaboration(workspaceId, selectedDocumentId);

onMounted(() => void loadWorkbench());
const pollTimer = window.setInterval(() => {
  if (repositories.value.some(item => item.syncStatus === 'SYNC_PENDING' || item.syncStatus === 'SYNCING')) {
    void refreshSyncState();
  }
}, 2500);
onBeforeUnmount(() => window.clearInterval(pollTimer));

async function loadWorkbench() {
  if (!workspaceId.value) { errorMessage.value = '工作区地址无效'; return; }
  contextLoading.value = true;
  try {
    [workspace.value, repositories.value, documentTree.value, pendingReviewCount.value] = await Promise.all([
      getWorkspace(workspaceId.value),
      listGitRepositories(workspaceId.value),
      listDocumentTree(workspaceId.value),
      getPendingDocumentChangeCount(workspaceId.value).catch(() => 0),
    ]);
    const queryRepositoryId = typeof route.query.repositoryId === 'string' ? route.query.repositoryId : '';
    state.selectedRepositoryId.value = repositories.value.some(item => item.id === queryRepositoryId)
      ? queryRepositoryId : repositories.value[0]?.id || '';
    const queryDocumentId = typeof route.query.documentId === 'string' ? route.query.documentId : '';
    state.selectDocument(documentChoices.value.some(item => item.id === queryDocumentId)
      ? queryDocumentId : documentChoices.value[0]?.id || '');
    await Promise.all([loadRepositoryDetails(), loadDocumentBundle()]);
  } catch (error) {
    errorMessage.value = readableError(error, '关联工作台加载失败');
  } finally {
    contextLoading.value = false;
  }
}

async function loadRepositoryDetails() {
  if (!selectedRepositoryId.value) { files.value = []; gitChanges.value = []; selectedSource.value = null; return; }
  [files.value, gitChanges.value] = await Promise.all([
    listGitRepositoryFiles(workspaceId.value, selectedRepositoryId.value),
    listGitChanges(workspaceId.value, selectedRepositoryId.value).catch(() => []),
  ]);
  const current = files.value.find(file => file.path === selectedFilePath.value && file.readable)
    ?? files.value.find(file => file.readable) ?? files.value[0];
  if (current) await openSource(current);
}

async function loadDocumentBundle() {
  if (!selectedDocumentId.value) { document.value = null; versions.value = []; return; }
  documentLoading.value = true;
  try {
    [document.value, versions.value] = await Promise.all([
      getDocument(selectedDocumentId.value), listDocumentVersions(selectedDocumentId.value),
    ]);
  } catch (error) {
    document.value = null; versions.value = [];
    ElMessage.error(readableError(error, '关联文档加载失败'));
  } finally { documentLoading.value = false; }
}

async function handleRepositoryChange(repositoryId: string) {
  state.selectedRepositoryId.value = repositoryId;
  state.selectFile('');
  selectedSource.value = null;
  await router.replace({ query: { ...route.query, repositoryId } });
  try { contextLoading.value = true; await loadRepositoryDetails(); }
  catch (error) { ElMessage.error(readableError(error, '仓库内容加载失败')); }
  finally { contextLoading.value = false; }
}

async function openSourceByPath(path: string) {
  const file = files.value.find(item => item.path === path);
  if (file) await openSource(file);
}

async function openSource(file: GitRepositoryFile) {
  sourceLoading.value = true;
  state.selectFile(file.path);
  try {
    selectedSource.value = await getGitRepositorySource(workspaceId.value, selectedRepositoryId.value, file.path);
    rebuildFixture();
  } catch (error) {
    selectedSource.value = null;
    ElMessage.error(readableError(error, '源码读取失败'));
  } finally { sourceLoading.value = false; }
}

async function handleDocumentChange(documentId: string) {
  state.selectDocument(documentId);
  documentBlocks.value = [];
  state.replaceFixture({ codeAnchors: [], links: [], issues: [], evidence: [] });
  await router.replace({ query: { ...route.query, documentId } });
  await loadDocumentBundle();
}

function handleBlocksLoaded(blocks: DocumentBlock[]) {
  state.replaceDocumentBlocks(blocks);
  rebuildFixture();
}

function rebuildFixture() {
  if (!selectedSource.value?.readable || !selectedSource.value.content || documentBlocks.value.length === 0 || !activeRepository.value) {
    state.replaceFixture({ codeAnchors: [], links: [], issues: [], evidence: [] });
    return;
  }
  state.replaceFixture(buildLinkedWorkbenchFixture({
    repositoryId: activeRepository.value.id,
    branch: activeRepository.value.defaultBranch,
    commitSha: selectedSource.value.commitSha || activeRepository.value.lastSyncedCommit || 'working-tree',
    filePath: selectedSource.value.path,
    language: selectedSource.value.language || 'text',
    source: selectedSource.value.content,
    blocks: documentBlocks.value,
  }));
  void nextTick(() => focusActiveLink('system'));
}

async function handleActivate(linkId: string, source: LinkActivationSource) {
  state.activateLink(linkId, source);
  await nextTick();
  focusActiveLink(source);
}

function handleBlockSelection(blockId: string) {
  const linkId = linkIdForBlock(links.value, blockId);
  if (linkId) void handleActivate(linkId, 'document');
}

async function handleModeChange(nextMode: WorkbenchMode) {
  sidebarNavigationActive.value = nextMode === 'DRIFT_REVIEW' ? 'drift' : 'linked';
  state.setMode(nextMode);
  await nextTick();
  focusActiveLink('system');
}

async function handleLinkedNavigation() {
  sidebarNavigationActive.value = 'linked';
  await handleModeChange('LINKED');
}

async function handleWorkspaceNavigation() {
  sidebarNavigationActive.value = 'linked';
  state.setMode('LINKED');
  toggleInspector(false);
  await router.replace({
    name: 'workspace-code',
    params: { workspaceId: workspaceId.value },
    query: route.query,
  });
  await nextTick();
  focusActiveLink('system');
}

async function handleReviewNavigation() {
  await router.push({
    name: 'workspace-reviews',
    params: {
      workspaceId: workspaceId.value,
      status: 'pending',
    },
    query: {
      repositoryId: selectedRepositoryId.value || undefined,
    },
  });
}

function focusActiveLink(source: LinkActivationSource) {
  const link = activeLink.value;
  if (!link) return;
  const plan = focusPlan(source);
  if (plan.code) workbenchShellRef.value?.focusAnchor(link.codeAnchorId);
  if (plan.document) workbenchShellRef.value?.focusBlock(link.blockId);
}

async function saveBlockViaCollaboration(block: DocumentBlock, content: DocumentBlockContent) {
  return updateBlockContentViaCollaboration(block.id, content, block.version);
}

async function handleSync() {
  if (!selectedRepositoryId.value) return;
  syncing.value = true;
  try {
    const updated = await syncGitRepository(workspaceId.value, selectedRepositoryId.value);
    repositories.value = repositories.value.map(item => item.id === updated.id ? updated : item);
    ElMessage.success('仓库同步任务已提交');
  } catch (error) { ElMessage.error(readableError(error, '仓库同步失败')); }
  finally { syncing.value = false; }
}

async function refreshSyncState() {
  const previous = activeRepository.value?.syncStatus;
  repositories.value = await listGitRepositories(workspaceId.value);
  const current = activeRepository.value?.syncStatus;
  if ((previous === 'SYNC_PENDING' || previous === 'SYNCING') && current !== previous && current !== 'SYNC_PENDING' && current !== 'SYNCING') {
    await loadRepositoryDetails();
  }
}

function flattenDocumentTree(nodes: DocumentTreeNode[], depth = 0): LinkedDocumentChoice[] {
  return nodes.flatMap(node => [
    { id: node.id, title: node.title, depth },
    ...flattenDocumentTree(node.children, depth + 1),
  ]);
}
</script>

<style scoped>
.linked-page-shell { height: 100vh; overflow: hidden; }
.linked-page-main { display: grid; min-width: 0; min-height: 0; grid-template-rows: auto auto minmax(0, 1fr); gap: 8px; overflow: hidden; padding: 12px 14px 0; background: #f2f5f9; }
.linked-page-main > .linked-workbench-shell:first-child { grid-row: 1 / -1; }
@media (max-width: 760px) { .linked-page-shell { height: auto; min-height: 100vh; overflow: visible; } .linked-page-main { min-height: 100vh; } }
</style>
