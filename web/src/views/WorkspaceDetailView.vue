<template>
  <main class="app-shell" :class="{ 'is-sidebar-collapsed': sidebarCollapsed }">
    <AppSidebar
      v-model="sidebarCollapsed"
      active="documents"
      :workspace-id="currentWorkspaceId()"
    />

    <section class="workspace">
      <header class="topbar">
        <div>
          <p class="eyebrow">Workspace</p>
          <h1>{{ workspace?.name || '工作区详情' }}</h1>
        </div>
        <div class="topbar-actions">
          <NotificationCenter />
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
        @open-document="openDocumentWorkbench"
      />

      <section class="document-workspace">
        <aside class="document-sidebar">
          <div class="document-sidebar-header">
          <div>
            <h2>文档树</h2>
            <p class="section-hint">点击文档进入工作台，或右键管理文档结构。</p>
          </div>
          <div class="document-tree-actions">
            <el-button
              size="small"
              :loading="importingDocuments"
              @click="importReadyRepositoryDocuments(currentWorkspaceId(), false)"
            >
              导入仓库文档
            </el-button>
            <el-tag v-if="workspace" size="small" effect="light">
              {{ workspace.currentUserRole === 'ADMIN' ? '管理员' : '普通成员' }}
            </el-tag>
          </div>
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
            <p v-if="documentImportHint" class="section-hint">
              {{ documentImportHint }}
            </p>
            <el-button type="primary" :icon="Plus" @click="openCreateRootDialog">
              创建第一篇文档
            </el-button>
          </el-empty>

          <DocumentTree
            v-else
            :nodes="documentTree"
            @select="openDocumentWorkbench"
            @create-child="openCreateChildDialog"
            @rename="handleRenameDocument"
            @move="openMoveDialog"
            @move-root="handleMoveDocumentToRoot"
            @delete="handleDeleteDocument"
          />
        </aside>

        <section class="document-main">
          <div class="document-placeholder">
            <div class="document-placeholder-content">
              <el-icon class="document-placeholder-icon"><Document /></el-icon>
              <h3>选择文档进入工作台</h3>
              <p>
                点击左侧文档树中的节点，进入独立的文档工作台页面。
                工作台提供 Block 编辑、版本历史、评审和 Issue 能力。
              </p>
            </div>
          </div>
        </section>
      </section>

      <section v-if="workspace" class="content-panel">
        <WorkspaceMembersPanel
          :workspace-id="workspace.id"
          :current-user-role="workspace.currentUserRole"
        />
      </section>

      <section v-if="workspace" class="content-panel">
        <GitRepositoryPanel
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
import { Back, Document, Plus } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import {
  createDocument,
  deleteDocument,
  listDocumentTree,
  moveDocument,
  updateDocument,
  type DocumentTreeNode,
} from '@/api/document';
import { getWorkspace, type Workspace } from '@/api/workspace';
import {
  importGitMarkdownDocuments,
  listGitRepositories,
} from '@/api/git';
import DocumentCreateDialog from '@/components/document/DocumentCreateDialog.vue';
import AppSidebar from '@/components/layout/AppSidebar.vue';
import NotificationCenter from '@/components/notification/NotificationCenter.vue';
import DocumentTree, {
  type FlatDocumentTreeNode,
} from '@/components/document/DocumentTree.vue';
import WorkspaceMembersPanel from '@/components/workspace/WorkspaceMembersPanel.vue';
import WorkspaceSearchPanel from '@/components/workspace/WorkspaceSearchPanel.vue';
import GitRepositoryPanel from '@/components/workspace/GitRepositoryPanel.vue';
import { readableError } from '@/utils/error';

const route = useRoute();
const router = useRouter();

const workspace = ref<Workspace | null>(null);
const documentTree = ref<DocumentTreeNode[]>([]);
const loading = ref(false);
const errorMessage = ref('');
const sidebarCollapsed = ref(
  localStorage.getItem('devcollab.sidebar.collapsed') === 'true',
);
const importingDocuments = ref(false);
const documentImportHint = ref('');

const dialogVisible = ref(false);
const createParentDocument = ref<FlatDocumentTreeNode | null>(null);

const moveDialogVisible = ref(false);
const movingDocument = ref(false);
const movingDocumentNode = ref<FlatDocumentTreeNode | null>(null);
const moveTargetParentId = ref<string | null>(null);

const flattenedDocumentOptions = computed(() => flattenDocumentTree(documentTree.value));
const moveParentOptions = computed(() => {
  if (!movingDocumentNode.value) {
    return flattenedDocumentOptions.value;
  }
  const forbiddenIds = descendantIds(documentTree.value, movingDocumentNode.value.id);
  forbiddenIds.add(movingDocumentNode.value.id);
  return flattenedDocumentOptions.value.filter((item) => !forbiddenIds.has(item.id));
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
    if (treeData.length === 0) {
      await importReadyRepositoryDocuments(workspaceId, true);
    }
  } catch (error) {
    errorMessage.value = readableError(error, '工作区加载失败');
  } finally {
    loading.value = false;
  }
}

async function importReadyRepositoryDocuments(
  workspaceId: string | null,
  silent: boolean,
) {
  if (!workspaceId || importingDocuments.value) {
    return;
  }
  importingDocuments.value = true;
  documentImportHint.value = '';
  try {
    const repositories = (await listGitRepositories(workspaceId))
      .filter((repository) => repository.syncStatus === 'READY');
    if (repositories.length === 0) {
      documentImportHint.value = '同步 Git 仓库后，Markdown 文档会自动进入文档树。';
      if (!silent) ElMessage.info(documentImportHint.value);
      return;
    }
    const results = await Promise.all(
      repositories.map((repository) =>
        importGitMarkdownDocuments(workspaceId, repository.id),
      ),
    );
    const imported = results.reduce(
      (total, result) => total + result.importedDocuments,
      0,
    );
    const unavailable = results.reduce(
      (total, result) => total + result.unavailableDocuments,
      0,
    );
    if (imported > 0) {
      await reloadDocumentTree(workspaceId);
      ElMessage.success(`已从仓库导入 ${imported} 篇 Markdown 文档`);
    } else if (unavailable > 0) {
      documentImportHint.value = '仓库文件尚无可导入内容，请重新同步仓库后再试。';
      if (!silent) ElMessage.warning(documentImportHint.value);
    } else if (!silent) {
      ElMessage.info('仓库文档已经全部导入');
    }
  } catch (error) {
    documentImportHint.value = readableError(error, '仓库文档导入失败');
    if (!silent) ElMessage.error(documentImportHint.value);
  } finally {
    importingDocuments.value = false;
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
    openDocumentWorkbench(document.id);
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

    await updateDocument(node.id, { title: value.trim() });
    ElMessage.success('文档标题已更新');
    await refreshTree();
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
    await moveDocument(movingDocumentNode.value.id, {
      parentDocumentId: moveTargetParentId.value || null,
    });
    moveDialogVisible.value = false;
    movingDocumentNode.value = null;
    ElMessage.success('文档已移动');
    await refreshTree();
  } catch (error) {
    ElMessage.error(readableError(error, '文档移动失败'));
  } finally {
    movingDocument.value = false;
  }
}

async function handleMoveDocumentToRoot(node: FlatDocumentTreeNode) {
  try {
    await moveDocument(node.id, { parentDocumentId: null });
    ElMessage.success('文档已移动到根层级');
    await refreshTree();
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
    await deleteDocument(node.id);
    ElMessage.success('文档已删除');
    await reloadDocumentTree(workspaceId);
  } catch (error) {
    ElMessage.error(readableError(error, '文档删除失败'));
  }
}

async function reloadDocumentTree(workspaceId: string) {
  documentTree.value = await listDocumentTree(workspaceId);
}

async function refreshTree() {
  const workspaceId = currentWorkspaceId();
  if (!workspaceId) {
    return;
  }
  await reloadDocumentTree(workspaceId);
}

function openDocumentWorkbench(documentId: string, blockId?: string | null) {
  const workspaceId = currentWorkspaceId();
  if (!workspaceId) {
    return;
  }

  void router.push({
    name: 'document-workbench',
    params: { workspaceId, documentId },
    query: blockId ? { blockId } : undefined,
  });
}

function currentWorkspaceId() {
  const workspaceId = route.params.workspaceId;
  return typeof workspaceId === 'string' ? workspaceId : null;
}

function flattenDocumentTree(
  nodes: DocumentTreeNode[],
  level = 0,
  parentDocumentId: string | null = null,
): FlatDocumentTreeNode[] {
  return nodes.flatMap((node) => [
    { id: node.id, title: node.title, level, parentDocumentId },
    ...flattenDocumentTree(node.children, level + 1, node.id),
  ]);
}

function descendantIds(nodes: DocumentTreeNode[], documentId: string): Set<string> {
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
