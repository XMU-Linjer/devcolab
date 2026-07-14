<template>
  <main class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">D</span>
        <span>DevCollab</span>
      </div>

      <nav class="nav-list">
        <button class="nav-item" type="button" @click="router.push('/workspaces')">
          <House class="nav-icon" />
          <span>工作区</span>
        </button>
        <button class="nav-item is-active" type="button">
          <Document class="nav-icon" />
          <span>文档</span>
        </button>
      </nav>
    </aside>

    <section class="workspace">
      <header class="topbar">
        <div>
          <p class="eyebrow">Workspace</p>
          <h1>{{ workspace?.name || '工作区详情' }}</h1>
        </div>
        <div class="topbar-actions">
          <el-button :icon="Back" @click="router.push('/workspaces')">
            返回列表
          </el-button>
          <el-button type="primary" :icon="Plus" @click="openCreateRootDialog">
            创建文档
          </el-button>
        </div>
      </header>

      <WorkspaceSearchPanel
        v-if="workspace"
        :workspace-id="workspace.id"
        @open-document="openDocument"
      />

      <section class="document-workspace">
        <aside class="document-sidebar">
          <div class="document-sidebar-header">
            <div>
              <h2>文档树</h2>
              <p class="section-hint">当前 MVP 支持根文档创建和文档内容编辑。</p>
            </div>
            <el-tag v-if="workspace" size="small" effect="light">
              {{ roleText(workspace.currentUserRole) }}
            </el-tag>
          </div>

          <el-skeleton v-if="loading" :rows="5" animated />

          <el-alert
            v-else-if="errorMessage"
            :title="errorMessage"
            type="error"
            show-icon
            :closable="false"
          >
            <template #default>
              <el-button text type="primary" @click="loadWorkspacePage">
                重新加载
              </el-button>
            </template>
          </el-alert>

          <el-empty
            v-else-if="documentTree.length === 0"
            description="还没有文档"
          >
            <el-button type="primary" :icon="Plus" @click="openCreateRootDialog">
              创建第一篇文档
            </el-button>
          </el-empty>

          <DocumentTree
            v-else
            :nodes="documentTree"
            :active-document-id="selectedDocument?.id"
            @select="openDocument"
            @create-child="openCreateChildDialog"
            @rename="handleRenameDocument"
            @move="openMoveDialog"
            @move-root="handleMoveDocumentToRoot"
            @delete="handleDeleteDocument"
          />
        </aside>

        <section class="document-main">
          <el-skeleton v-if="documentLoading" :rows="8" animated />

          <div v-else-if="selectedDocument" class="document-preview">
            <div class="document-editor-heading">
              <div>
                <p class="eyebrow">Document</p>
                <div class="document-title-row">
                  <h2>{{ selectedDocument.title }}</h2>
                  <el-tag :type="reviewStatusTagType(selectedDocument.reviewStatus)" effect="light">
                    {{ reviewStatusText(selectedDocument.reviewStatus) }}
                  </el-tag>
                </div>
                <p class="section-hint">
                  这里是 Block 编辑器雏形，保存会直接写入 Knowledge Core。
                </p>
              </div>
              <div class="document-heading-actions">
                <el-button
                  v-if="canSubmitReview(selectedDocument)"
                  type="primary"
                  :loading="submittingReview"
                  @click="handleSubmitReview"
                >
                  提交评审
                </el-button>
                <el-button
                  v-if="canReviewDocument(selectedDocument)"
                  type="success"
                  :loading="approvingReview"
                  @click="handleApproveReview"
                >
                  通过评审
                </el-button>
                <el-button
                  v-if="canReviewDocument(selectedDocument)"
                  type="danger"
                  plain
                  :loading="rejectingReview"
                  @click="handleRejectReview"
                >
                  驳回
                </el-button>
                <el-tag effect="light">
                  更新于 {{ formatTime(selectedDocument.updatedAt) }}
                </el-tag>
              </div>
            </div>

            <div v-if="latestVersion" class="document-version-card">
              <div>
                <p class="eyebrow">Published Version</p>
                <strong>v{{ latestVersion.versionNo }} · {{ latestVersion.title }}</strong>
              </div>
              <span>{{ formatTime(latestVersion.publishedAt) }}</span>
            </div>

            <div class="document-history-grid">
              <section class="document-history-panel">
                <div class="panel-title-row">
                  <div>
                    <p class="eyebrow">Versions</p>
                    <h3>版本历史</h3>
                  </div>
                  <el-tag v-if="latestVersion" type="success" effect="light">
                    当前 v{{ latestVersion.versionNo }}
                  </el-tag>
                </div>

                <el-empty
                  v-if="documentVersions.length === 0"
                  description="暂无发布版本"
                  :image-size="72"
                />
                <div v-else class="version-list">
                  <article
                    v-for="version in documentVersions"
                    :key="version.id"
                    class="version-item"
                  >
                    <div>
                      <strong>v{{ version.versionNo }} · {{ version.title }}</strong>
                      <p>发布于 {{ formatTime(version.publishedAt) }}</p>
                    </div>
                    <el-tag size="small" effect="plain">
                      {{ snapshotBlockCount(version) }} 个 Block
                    </el-tag>
                  </article>
                </div>
              </section>

              <section class="document-history-panel">
                <div class="panel-title-row">
                  <div>
                    <p class="eyebrow">Review</p>
                    <h3>评审面板</h3>
                  </div>
                  <el-tag effect="light">
                    {{ reviewRecords.length }} 条记录
                  </el-tag>
                </div>

                <div v-if="canReviewDocument(selectedDocument)" class="review-comment-box">
                  <el-input
                    v-model="reviewComment"
                    type="textarea"
                    :rows="3"
                    maxlength="2000"
                    show-word-limit
                    placeholder="填写评审意见，例如：同意发布，或说明驳回原因"
                  />
                </div>
                <p v-else class="section-hint">
                  当前状态下无需处理评审，可查看历史记录。
                </p>

                <el-empty
                  v-if="reviewRecords.length === 0"
                  description="暂无评审记录"
                  :image-size="72"
                />
                <div v-else class="review-record-list">
                  <article
                    v-for="record in reviewRecords"
                    :key="record.id"
                    class="review-record-item"
                  >
                    <div class="review-record-header">
                      <el-tag :type="reviewActionTagType(record.action)" size="small">
                        {{ reviewActionText(record.action) }}
                      </el-tag>
                      <span>{{ formatTime(record.createdAt) }}</span>
                    </div>
                    <p v-if="record.comment">{{ record.comment }}</p>
                    <p v-else class="muted-text">未填写评审意见</p>
                  </article>
                </div>
              </section>
            </div>

            <BlockEditor
              :document-id="selectedDocument.id"
              :focus-block-id="focusedBlockId"
            />
          </div>

          <el-empty
            v-else
            description="选择或创建一篇文档"
          >
            <el-button type="primary" :icon="Plus" @click="openCreateRootDialog">
              创建文档
            </el-button>
          </el-empty>
        </section>
      </section>

      <section v-if="workspace" class="content-panel">
        <WorkspaceMembersPanel
          :workspace-id="workspace.id"
          :current-user-role="workspace.currentUserRole"
        />
      </section>
    </section>

    <DocumentCreateDialog
      v-model="dialogVisible"
      :parent-document-title="createParentDocument?.title"
      @create="handleCreateDocument"
    />

    <el-dialog
      v-model="moveDialogVisible"
      title="移动文档"
      width="420px"
      destroy-on-close
    >
      <el-form label-position="top">
        <el-form-item label="目标父文档">
          <el-select
            v-model="moveTargetParentId"
            class="full-width"
            clearable
            placeholder="不选择则移动到根层级"
          >
            <el-option
              v-for="option in moveParentOptions"
              :key="option.id"
              :label="`${'　'.repeat(option.level)}${option.title}`"
              :value="option.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="moveDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="movingDocument"
          @click="handleMoveDocument"
        >
          移动
        </el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { Back, Document, House, Plus } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import {
  approveDocumentReview,
  createDocument,
  deleteDocument,
  getDocument,
  listDocumentReviewRecords,
  listDocumentVersions,
  listDocumentTree,
  moveDocument,
  rejectDocumentReview,
  submitDocumentReview,
  updateDocument,
  type DocumentReviewAction,
  type DocumentReviewRecord,
  type DocumentReviewStatus,
  type DocumentSummary,
  type DocumentTreeNode,
  type DocumentVersion,
} from '@/api/document';
import { getWorkspace, type Workspace, type WorkspaceRole } from '@/api/workspace';
import DocumentCreateDialog from '@/components/document/DocumentCreateDialog.vue';
import DocumentTree, {
  type FlatDocumentTreeNode,
} from '@/components/document/DocumentTree.vue';
import BlockEditor from '@/components/editor/BlockEditor.vue';
import WorkspaceMembersPanel from '@/components/workspace/WorkspaceMembersPanel.vue';
import WorkspaceSearchPanel from '@/components/workspace/WorkspaceSearchPanel.vue';
import { readableError } from '@/utils/error';

const route = useRoute();
const router = useRouter();

const workspace = ref<Workspace | null>(null);
const documentTree = ref<DocumentTreeNode[]>([]);
const selectedDocument = ref<DocumentSummary | null>(null);
const documentVersions = ref<DocumentVersion[]>([]);
const reviewRecords = ref<DocumentReviewRecord[]>([]);
const loading = ref(false);
const documentLoading = ref(false);
const dialogVisible = ref(false);
const createParentDocument = ref<FlatDocumentTreeNode | null>(null);
const moveDialogVisible = ref(false);
const movingDocument = ref(false);
const movingDocumentNode = ref<FlatDocumentTreeNode | null>(null);
const moveTargetParentId = ref<string | null>(null);
const errorMessage = ref('');
const focusedBlockId = ref<string | null>(null);
const submittingReview = ref(false);
const approvingReview = ref(false);
const rejectingReview = ref(false);
const reviewComment = ref('');

const flattenedDocumentOptions = computed(() => flattenDocumentTree(documentTree.value));
const latestVersion = computed(() => documentVersions.value[0] ?? null);
const moveParentOptions = computed(() => {
  if (!movingDocumentNode.value) {
    return flattenedDocumentOptions.value;
  }

  const forbiddenIds = descendantIds(
    documentTree.value,
    movingDocumentNode.value.id,
  );
  forbiddenIds.add(movingDocumentNode.value.id);
  return flattenedDocumentOptions.value.filter(
    (item) => !forbiddenIds.has(item.id),
  );
});

onMounted(() => {
  void loadWorkspacePage();
});

async function loadWorkspacePage() {
  const workspaceId = currentWorkspaceId();
  if (!workspaceId) {
    errorMessage.value = '工作区地址无效';
    return;
  }

  loading.value = true;
  errorMessage.value = '';

  try {
    const [workspaceData, treeData] = await Promise.all([
      getWorkspace(workspaceId),
      listDocumentTree(workspaceId),
    ]);
    workspace.value = workspaceData;
    documentTree.value = treeData;

    if (!selectedDocument.value && treeData[0]) {
      await openDocument(treeData[0].id);
    }
  } catch (error) {
    errorMessage.value = readableError(error, '工作区加载失败');
  } finally {
    loading.value = false;
  }
}

async function handleCreateDocument(title: string) {
  const workspaceId = currentWorkspaceId();
  if (!workspaceId) {
    return;
  }

  try {
    const document = await createDocument(workspaceId, {
      title,
      parentDocumentId: createParentDocument.value?.id ?? null,
    });
    dialogVisible.value = false;
    createParentDocument.value = null;
    ElMessage.success('文档创建成功');
    await reloadDocumentTree(workspaceId);
    await openDocument(document.id);
  } catch (error) {
    ElMessage.error(readableError(error, '文档创建失败'));
  }
}

function openCreateRootDialog() {
  createParentDocument.value = null;
  dialogVisible.value = true;
}

function openCreateChildDialog(node: FlatDocumentTreeNode) {
  createParentDocument.value = node;
  dialogVisible.value = true;
}

async function handleRenameDocument(node: FlatDocumentTreeNode) {
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入新的文档标题',
      '重命名文档',
      {
        inputValue: node.title,
        inputPattern: /^.{1,200}$/,
        inputErrorMessage: '文档标题长度必须在 1 到 200 个字符之间',
        confirmButtonText: '保存',
        cancelButtonText: '取消',
      },
    );

    const updated = await updateDocument(node.id, {
      title: value.trim(),
    });
    ElMessage.success('文档标题已更新');
    await refreshTreeAndSelected(updated.id);
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return;
    }
    ElMessage.error(readableError(error, '文档重命名失败'));
  }
}

function openMoveDialog(node: FlatDocumentTreeNode) {
  movingDocumentNode.value = node;
  moveTargetParentId.value = node.parentDocumentId;
  moveDialogVisible.value = true;
}

async function handleMoveDocument() {
  if (!movingDocumentNode.value) {
    return;
  }

  movingDocument.value = true;
  try {
    const moved = await moveDocument(movingDocumentNode.value.id, {
      parentDocumentId: moveTargetParentId.value || null,
    });
    moveDialogVisible.value = false;
    movingDocumentNode.value = null;
    ElMessage.success('文档已移动');
    await refreshTreeAndSelected(moved.id);
  } catch (error) {
    ElMessage.error(readableError(error, '文档移动失败'));
  } finally {
    movingDocument.value = false;
  }
}

async function handleMoveDocumentToRoot(node: FlatDocumentTreeNode) {
  try {
    const moved = await moveDocument(node.id, {
      parentDocumentId: null,
    });
    ElMessage.success('文档已移动到根层级');
    await refreshTreeAndSelected(moved.id);
  } catch (error) {
    ElMessage.error(readableError(error, '文档移动失败'));
  }
}

async function handleDeleteDocument(node: FlatDocumentTreeNode) {
  try {
    await ElMessageBox.confirm(
      `确定要删除「${node.title}」吗？子文档和内容块也会一并删除。`,
      '删除文档',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    );
  } catch {
    return;
  }

  const workspaceId = currentWorkspaceId();
  if (!workspaceId) {
    return;
  }

  try {
    const removedIds = descendantIds(documentTree.value, node.id);
    removedIds.add(node.id);
    await deleteDocument(node.id);
    ElMessage.success('文档已删除');
    await reloadDocumentTree(workspaceId);

    if (selectedDocument.value && removedIds.has(selectedDocument.value.id)) {
      selectedDocument.value = null;
      documentVersions.value = [];
      reviewRecords.value = [];
      if (documentTree.value[0]) {
        await openDocument(documentTree.value[0].id);
      }
    }
  } catch (error) {
    ElMessage.error(readableError(error, '文档删除失败'));
  }
}

async function handleSubmitReview() {
  if (!selectedDocument.value) {
    return;
  }

  submittingReview.value = true;
  try {
    selectedDocument.value = await submitDocumentReview(
      selectedDocument.value.id,
    );
    await loadReviewRecords(selectedDocument.value.id);
    ElMessage.success('文档已提交评审');
  } catch (error) {
    ElMessage.error(readableError(error, '提交评审失败'));
  } finally {
    submittingReview.value = false;
  }
}

async function handleApproveReview() {
  if (!selectedDocument.value) {
    return;
  }

  approvingReview.value = true;
  try {
    selectedDocument.value = await approveDocumentReview(
      selectedDocument.value.id,
      { comment: normalizeReviewComment(reviewComment.value) },
    );
    await loadDocumentVersions(selectedDocument.value.id);
    await loadReviewRecords(selectedDocument.value.id);
    reviewComment.value = '';
    ElMessage.success('文档已发布为新版本');
  } catch (error) {
    ElMessage.error(readableError(error, '通过评审失败'));
  } finally {
    approvingReview.value = false;
  }
}

async function handleRejectReview() {
  if (!selectedDocument.value) {
    return;
  }

  rejectingReview.value = true;
  try {
    selectedDocument.value = await rejectDocumentReview(
      selectedDocument.value.id,
      { comment: normalizeReviewComment(reviewComment.value) },
    );
    await loadReviewRecords(selectedDocument.value.id);
    reviewComment.value = '';
    ElMessage.success('文档已驳回，可修改后重新提交');
  } catch (error) {
    ElMessage.error(readableError(error, '驳回评审失败'));
  } finally {
    rejectingReview.value = false;
  }
}

async function reloadDocumentTree(workspaceId: string) {
  documentTree.value = await listDocumentTree(workspaceId);
}

async function refreshTreeAndSelected(documentId: string) {
  const workspaceId = currentWorkspaceId();
  if (!workspaceId) {
    return;
  }
  await reloadDocumentTree(workspaceId);
  await openDocument(documentId);
}

async function openDocument(documentId: string, blockId: string | null = null) {
  documentLoading.value = true;
  focusedBlockId.value = blockId;
  try {
    selectedDocument.value = await getDocument(documentId);
    await Promise.all([
      loadDocumentVersions(documentId),
      loadReviewRecords(documentId),
    ]);
  } catch (error) {
    focusedBlockId.value = null;
    documentVersions.value = [];
    reviewRecords.value = [];
    ElMessage.error(readableError(error, '文档加载失败'));
  } finally {
    documentLoading.value = false;
  }
}

async function loadDocumentVersions(documentId: string) {
  documentVersions.value = await listDocumentVersions(documentId);
}

async function loadReviewRecords(documentId: string) {
  reviewRecords.value = await listDocumentReviewRecords(documentId);
}

function currentWorkspaceId() {
  const workspaceId = route.params.workspaceId;
  return typeof workspaceId === 'string' ? workspaceId : null;
}

function roleText(role: WorkspaceRole) {
  return role === 'ADMIN' ? '管理员' : '普通用户';
}

function canSubmitReview(document: DocumentSummary) {
  return document.reviewStatus === 'DRAFT' || document.reviewStatus === 'REJECTED';
}

function canReviewDocument(document: DocumentSummary) {
  return workspace.value?.currentUserRole === 'ADMIN'
    && document.reviewStatus === 'IN_REVIEW';
}

function normalizeReviewComment(comment: string) {
  const trimmed = comment.trim();
  return trimmed.length === 0 ? null : trimmed;
}

function snapshotBlockCount(version: DocumentVersion) {
  try {
    const snapshot = JSON.parse(version.snapshotPayload) as {
      blocks?: unknown[];
    };
    return Array.isArray(snapshot.blocks) ? snapshot.blocks.length : 0;
  } catch {
    return 0;
  }
}

function reviewActionText(action: DocumentReviewAction) {
  const actionMap: Record<DocumentReviewAction, string> = {
    SUBMITTED: '提交评审',
    APPROVED: '通过评审',
    REJECTED: '驳回评审',
  };
  return actionMap[action];
}

function reviewActionTagType(action: DocumentReviewAction) {
  const actionMap: Record<
    DocumentReviewAction,
    'primary' | 'success' | 'warning' | 'info' | 'danger'
  > = {
    SUBMITTED: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger',
  };
  return actionMap[action];
}

function reviewStatusText(status: DocumentReviewStatus) {
  const statusMap: Record<DocumentReviewStatus, string> = {
    DRAFT: '草稿',
    IN_REVIEW: '评审中',
    PUBLISHED: '已发布',
    REJECTED: '已驳回',
  };
  return statusMap[status];
}

function reviewStatusTagType(status: DocumentReviewStatus) {
  const statusMap: Record<
    DocumentReviewStatus,
    'primary' | 'success' | 'warning' | 'info' | 'danger'
  > = {
    DRAFT: 'info',
    IN_REVIEW: 'warning',
    PUBLISHED: 'success',
    REJECTED: 'danger',
  };
  return statusMap[status];
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
}

function flattenDocumentTree(
  nodes: DocumentTreeNode[],
  level = 0,
  parentDocumentId: string | null = null,
): FlatDocumentTreeNode[] {
  return nodes.flatMap((node) => [
    {
      id: node.id,
      title: node.title,
      level,
      parentDocumentId,
    },
    ...flattenDocumentTree(node.children, level + 1, node.id),
  ]);
}

function descendantIds(nodes: DocumentTreeNode[], documentId: string) {
  const result = new Set<string>();
  const target = findTreeNode(nodes, documentId);
  if (!target) {
    return result;
  }

  collectDescendants(target, result);
  return result;
}

function collectDescendants(node: DocumentTreeNode, result: Set<string>) {
  node.children.forEach((child) => {
    result.add(child.id);
    collectDescendants(child, result);
  });
}

function findTreeNode(
  nodes: DocumentTreeNode[],
  documentId: string,
): DocumentTreeNode | null {
  for (const node of nodes) {
    if (node.id === documentId) {
      return node;
    }
    const child = findTreeNode(node.children, documentId);
    if (child) {
      return child;
    }
  }
  return null;
}

</script>
