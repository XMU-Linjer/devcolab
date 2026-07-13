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
                <h2>{{ selectedDocument.title }}</h2>
                <p class="section-hint">
                  这里是 Block 编辑器雏形，保存会直接写入 Knowledge Core。
                </p>
              </div>
              <el-tag effect="light">
                更新于 {{ formatTime(selectedDocument.updatedAt) }}
              </el-tag>
            </div>

            <BlockEditor :document-id="selectedDocument.id" />
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
  createDocument,
  deleteDocument,
  getDocument,
  listDocumentTree,
  moveDocument,
  updateDocument,
  type DocumentSummary,
  type DocumentTreeNode,
} from '@/api/document';
import { getWorkspace, type Workspace, type WorkspaceRole } from '@/api/workspace';
import DocumentCreateDialog from '@/components/document/DocumentCreateDialog.vue';
import DocumentTree, {
  type FlatDocumentTreeNode,
} from '@/components/document/DocumentTree.vue';
import BlockEditor from '@/components/editor/BlockEditor.vue';
import WorkspaceMembersPanel from '@/components/workspace/WorkspaceMembersPanel.vue';
import { readableError } from '@/utils/error';

const route = useRoute();
const router = useRouter();

const workspace = ref<Workspace | null>(null);
const documentTree = ref<DocumentTreeNode[]>([]);
const selectedDocument = ref<DocumentSummary | null>(null);
const loading = ref(false);
const documentLoading = ref(false);
const dialogVisible = ref(false);
const createParentDocument = ref<FlatDocumentTreeNode | null>(null);
const moveDialogVisible = ref(false);
const movingDocument = ref(false);
const movingDocumentNode = ref<FlatDocumentTreeNode | null>(null);
const moveTargetParentId = ref<string | null>(null);
const errorMessage = ref('');

const flattenedDocumentOptions = computed(() => flattenDocumentTree(documentTree.value));
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
      if (documentTree.value[0]) {
        await openDocument(documentTree.value[0].id);
      }
    }
  } catch (error) {
    ElMessage.error(readableError(error, '文档删除失败'));
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

async function openDocument(documentId: string) {
  documentLoading.value = true;
  try {
    selectedDocument.value = await getDocument(documentId);
  } catch (error) {
    ElMessage.error(readableError(error, '文档加载失败'));
  } finally {
    documentLoading.value = false;
  }
}

function currentWorkspaceId() {
  const workspaceId = route.params.workspaceId;
  return typeof workspaceId === 'string' ? workspaceId : null;
}

function roleText(role: WorkspaceRole) {
  return role === 'ADMIN' ? '管理员' : '普通用户';
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
