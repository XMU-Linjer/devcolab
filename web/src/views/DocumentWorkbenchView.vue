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
          <DocumentIcon class="nav-icon" />
          <span>文档工作台</span>
        </button>
      </nav>
    </aside>

    <section class="workspace workbench-page">
      <header class="topbar">
        <div>
          <p class="eyebrow">Document Workbench</p>
          <h1>{{ document?.title || '文档工作台' }}</h1>
        </div>
        <div class="topbar-actions">
          <el-button :icon="Back" @click="router.push(`/workspaces/${workspaceId}`)">
            返回空间
          </el-button>
          <el-tag v-if="workspace" effect="light">
            {{ workspace.currentUserRole === 'ADMIN' ? '管理员' : '普通成员' }}
          </el-tag>
        </div>
      </header>

      <section class="workbench-layout">
        <aside class="workbench-tree-panel">
          <div class="document-sidebar-header">
            <div>
              <h2>文档树</h2>
              <p class="section-hint">点击节点切换当前文档。</p>
            </div>
          </div>

          <el-skeleton v-if="treeLoading" :rows="6" animated />
          <el-empty v-else-if="documentTree.length === 0" description="暂无文档" />
          <DocumentTree
            v-else
            :nodes="documentTree"
            :active-document-id="documentId"
            @select="openDocument"
            @create-child="noopTreeAction"
            @rename="noopTreeAction"
            @move="noopTreeAction"
            @move-root="noopTreeAction"
            @delete="noopTreeAction"
          />
        </aside>

        <section class="workbench-editor-panel">
          <el-alert
            v-if="errorMessage"
            :title="errorMessage"
            type="error"
            show-icon
            :closable="false"
          />

          <el-skeleton v-if="documentLoading" :rows="10" animated />

          <template v-else-if="document">
            <DocumentStatusBar
              :document="document"
              :versions="versions"
              :can-review="canReview"
              :busy-action="busyAction"
              @submit="handleSubmit"
              @approve="handleApprove"
              @reject="handleReject"
              @deprecate="handleDeprecate"
            />

            <BlockEditor
              :document-id="document.id"
              :focus-block-id="focusBlockId"
              :readonly="isReadonly"
            />
          </template>

          <el-empty v-else description="请选择一篇文档" />
        </section>

        <aside class="workbench-side-panel">
          <el-tabs v-model="rightTab">
            <el-tab-pane label="时间线" name="timeline">
              <section class="document-history-panel workbench-tab-panel">
                <div class="panel-title-row">
                  <div>
                    <p class="eyebrow">Timeline</p>
                    <h3>操作时间线</h3>
                  </div>
                </div>

                <el-skeleton v-if="sideLoading" :rows="4" animated />
                <el-empty v-else-if="timeline.length === 0" description="暂无操作记录" />
                <div v-else class="timeline-list">
                  <article
                    v-for="item in timeline"
                    :key="item.id"
                    class="timeline-item"
                  >
                    <span class="timeline-dot" />
                    <div>
                      <strong>{{ operationActionText(item.action) }}</strong>
                      <p>{{ item.message }}</p>
                      <span>{{ formatTime(item.createdAt) }}</span>
                    </div>
                  </article>
                </div>
              </section>
            </el-tab-pane>

            <el-tab-pane label="Review Issue" name="issues">
              <ReviewIssuePanel
                v-if="document"
                :document-id="document.id"
                :version-id="currentVersionId"
              />
            </el-tab-pane>

            <el-tab-pane label="版本" name="versions">
              <VersionHistoryPanel
                v-if="document"
                :document-id="document.id"
                :versions="versions"
                :loading="sideLoading"
              />
            </el-tab-pane>
          </el-tabs>
        </aside>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Back, Document as DocumentIcon, House } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';

import {
  approveDocumentReview,
  deprecateDocument,
  getDocument,
  listDocumentTimeline,
  listDocumentTree,
  listDocumentVersions,
  rejectDocumentReview,
  submitDocumentReview,
  type DocumentOperationLog,
  type DocumentSummary,
  type DocumentTreeNode,
  type DocumentVersion,
} from '@/api/document';
import { getWorkspace, type Workspace } from '@/api/workspace';
import DocumentStatusBar from '@/components/document/DocumentStatusBar.vue';
import DocumentTree, {
  type FlatDocumentTreeNode,
} from '@/components/document/DocumentTree.vue';
import ReviewIssuePanel from '@/components/document/ReviewIssuePanel.vue';
import VersionHistoryPanel from '@/components/document/VersionHistoryPanel.vue';
import BlockEditor from '@/components/editor/BlockEditor.vue';
import { readableError } from '@/utils/error';

const route = useRoute();
const router = useRouter();

const workspace = ref<Workspace | null>(null);
const document = ref<DocumentSummary | null>(null);
const documentTree = ref<DocumentTreeNode[]>([]);
const versions = ref<DocumentVersion[]>([]);
const timeline = ref<DocumentOperationLog[]>([]);
const treeLoading = ref(false);
const documentLoading = ref(false);
const sideLoading = ref(false);
const errorMessage = ref('');
const busyAction = ref<'submit' | 'approve' | 'reject' | 'deprecate' | null>(null);
const rightTab = ref<'timeline' | 'issues' | 'versions'>('timeline');
const focusBlockId = ref<string | null>(null);

const workspaceId = computed(() => route.params.workspaceId as string);
const documentId = computed(() => route.params.documentId as string);
const currentVersionId = computed(() => (
  versions.value.find((version) => version.status === 'CURRENT')?.id
  ?? versions.value[0]?.id
  ?? null
));
const canReview = computed(() => workspace.value?.currentUserRole === 'ADMIN');
const isReadonly = computed(() => (
  document.value?.reviewStatus === 'DEPRECATED'
  || document.value?.reviewStatus === 'SUPERSEDED'
));

onMounted(() => {
  focusBlockId.value = typeof route.query.blockId === 'string'
    ? route.query.blockId
    : null;
  void loadWorkbench();
});

watch(
  () => route.params.documentId,
  () => {
    focusBlockId.value = typeof route.query.blockId === 'string'
      ? route.query.blockId
      : null;
    void loadDocumentBundle();
  },
);

async function loadWorkbench() {
  await Promise.all([
    loadWorkspace(),
    loadDocumentTree(),
    loadDocumentBundle(),
  ]);
}

async function loadWorkspace() {
  try {
    workspace.value = await getWorkspace(workspaceId.value);
  } catch (error) {
    ElMessage.error(readableError(error, '工作区信息加载失败'));
  }
}

async function loadDocumentTree() {
  treeLoading.value = true;
  try {
    documentTree.value = await listDocumentTree(workspaceId.value);
  } catch (error) {
    ElMessage.error(readableError(error, '文档树加载失败'));
  } finally {
    treeLoading.value = false;
  }
}

async function loadDocumentBundle() {
  documentLoading.value = true;
  sideLoading.value = true;
  errorMessage.value = '';

  try {
    const [documentData, versionData, timelineData] = await Promise.all([
      getDocument(documentId.value),
      listDocumentVersions(documentId.value),
      listDocumentTimeline(documentId.value),
    ]);
    document.value = documentData;
    versions.value = versionData;
    timeline.value = timelineData;
  } catch (error) {
    document.value = null;
    versions.value = [];
    timeline.value = [];
    errorMessage.value = readableError(error, '文档工作台加载失败');
  } finally {
    documentLoading.value = false;
    sideLoading.value = false;
  }
}

function openDocument(nextDocumentId: string) {
  void router.push({
    name: 'document-workbench',
    params: {
      workspaceId: workspaceId.value,
      documentId: nextDocumentId,
    },
  });
}

function noopTreeAction(_node: FlatDocumentTreeNode) {
  ElMessage.info('文档树结构操作请回到空间详情页处理。');
}

async function handleSubmit() {
  if (!document.value) {
    return;
  }

  busyAction.value = 'submit';
  try {
    document.value = await submitDocumentReview(document.value.id);
    await refreshSideData();
    ElMessage.success(
      document.value.reviewStatus === 'IN_REVIEW'
        ? '文档已提交评审'
        : '新修订已创建',
    );
  } catch (error) {
    ElMessage.error(readableError(error, '提交评审失败'));
  } finally {
    busyAction.value = null;
  }
}

async function handleApprove() {
  if (!document.value) {
    return;
  }

  busyAction.value = 'approve';
  try {
    document.value = await approveDocumentReview(document.value.id);
    await refreshSideData();
    ElMessage.success('文档已发布为新版本');
  } catch (error) {
    ElMessage.error(readableError(error, '通过评审失败'));
  } finally {
    busyAction.value = null;
  }
}

async function handleReject() {
  if (!document.value) {
    return;
  }

  busyAction.value = 'reject';
  try {
    document.value = await rejectDocumentReview(document.value.id);
    await refreshSideData();
    ElMessage.success('文档已驳回');
  } catch (error) {
    ElMessage.error(readableError(error, '驳回评审失败'));
  } finally {
    busyAction.value = null;
  }
}

async function handleDeprecate() {
  if (!document.value) {
    return;
  }

  try {
    await ElMessageBox.confirm(
      '废弃后文档将进入只读状态，不能继续编辑或评审。确定废弃吗？',
      '废弃文档',
      {
        confirmButtonText: '废弃',
        cancelButtonText: '取消',
        type: 'warning',
      },
    );
  } catch {
    return;
  }

  busyAction.value = 'deprecate';
  try {
    document.value = await deprecateDocument(document.value.id);
    await refreshSideData();
    ElMessage.success('文档已废弃');
  } catch (error) {
    ElMessage.error(readableError(error, '废弃文档失败'));
  } finally {
    busyAction.value = null;
  }
}

async function refreshSideData() {
  if (!document.value) {
    return;
  }

  const [versionData, timelineData] = await Promise.all([
    listDocumentVersions(document.value.id),
    listDocumentTimeline(document.value.id),
  ]);
  versions.value = versionData;
  timeline.value = timelineData;
}

function operationActionText(action: string) {
  const actionMap: Record<string, string> = {
    DOCUMENT_CREATED: '创建文档',
    DOCUMENT_UPDATED: '更新文档',
    DOCUMENT_MOVED: '移动文档',
    DOCUMENT_DELETED: '删除文档',
    DOCUMENT_DEPRECATED: '废弃文档',
    DOCUMENT_BLOCK_CREATED: '新增内容块',
    DOCUMENT_BLOCK_UPDATED: '更新内容块',
    DOCUMENT_BLOCK_DELETED: '删除内容块',
    DOCUMENT_BLOCK_MOVED: '调整内容块排序',
    DOCUMENT_REVIEW_SUBMITTED: '提交评审',
    DOCUMENT_REVIEW_APPROVED: '通过评审',
    DOCUMENT_REVIEW_REJECTED: '驳回评审',
  };
  return actionMap[action] ?? action;
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
}
</script>
