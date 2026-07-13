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
          <el-button type="primary" :icon="Plus" @click="dialogVisible = true">
            创建文档
          </el-button>
        </div>
      </header>

      <section class="document-workspace">
        <aside class="document-sidebar">
          <div class="document-sidebar-header">
            <h2>文档树</h2>
            <el-tag v-if="workspace" size="small" effect="light">
              {{ workspace.currentUserRole }}
            </el-tag>
          </div>

          <el-skeleton v-if="loading" :rows="5" animated />

          <el-alert
            v-else-if="errorMessage"
            :title="errorMessage"
            type="error"
            show-icon
            :closable="false"
          />

          <el-empty
            v-else-if="documentTree.length === 0"
            description="还没有文档"
          >
            <el-button type="primary" :icon="Plus" @click="dialogVisible = true">
              创建第一篇文档
            </el-button>
          </el-empty>

          <DocumentTree
            v-else
            :nodes="documentTree"
            :active-document-id="selectedDocument?.id"
            @select="openDocument"
          />
        </aside>

        <section class="document-main">
          <el-skeleton v-if="documentLoading" :rows="8" animated />

          <div v-else-if="selectedDocument" class="document-preview">
            <div class="document-editor-heading">
              <div>
                <p class="eyebrow">Document</p>
                <h2>{{ selectedDocument.title }}</h2>
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
          />
        </section>
      </section>
    </section>

    <DocumentCreateDialog
      v-model="dialogVisible"
      @create="handleCreateDocument"
    />
  </main>
</template>

<script setup lang="ts">
import { Back, Document, House, Plus } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import {
  createDocument,
  getDocument,
  listDocumentTree,
  type DocumentSummary,
  type DocumentTreeNode,
} from '@/api/document';
import { getWorkspace, type Workspace } from '@/api/workspace';
import DocumentCreateDialog from '@/components/document/DocumentCreateDialog.vue';
import DocumentTree from '@/components/document/DocumentTree.vue';
import BlockEditor from '@/components/editor/BlockEditor.vue';
import { readableError } from '@/utils/error';

const route = useRoute();
const router = useRouter();

const workspace = ref<Workspace | null>(null);
const documentTree = ref<DocumentTreeNode[]>([]);
const selectedDocument = ref<DocumentSummary | null>(null);
const loading = ref(false);
const documentLoading = ref(false);
const dialogVisible = ref(false);
const errorMessage = ref('');

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
      parentDocumentId: null,
    });
    dialogVisible.value = false;
    ElMessage.success('文档创建成功');
    await reloadDocumentTree(workspaceId);
    await openDocument(document.id);
  } catch (error) {
    ElMessage.error(readableError(error, '文档创建失败'));
  }
}

async function reloadDocumentTree(workspaceId: string) {
  documentTree.value = await listDocumentTree(workspaceId);
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

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
}
</script>
