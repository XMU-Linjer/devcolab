<template>
  <main class="app-shell review-page-shell" :class="{ 'is-sidebar-collapsed': sidebarCollapsed }">
    <AppSidebar
      v-model="sidebarCollapsed"
      active="code"
      :workspace-id="workspaceId"
      linked-navigation-active="review"
      :linked-count="0"
      :review-count="statusCounts.pending"
      :review-status="routeStatus"
      :review-status-counts="statusCounts"
      :drift-count="0"
      @open-workspace="openLinkedWorkbench"
      @open-linked="openLinkedWorkbench"
      @open-review="openStatus('pending')"
      @open-review-status="openStatus"
      @open-drift="openLinkedWorkbench"
    >
      <template #workspace-panel>
        <LinkedRepositoryContext
          :repositories="repositories"
          :repository-id="selectedRepositoryId"
          :file-tree="fileTree"
          :files-count="files.length"
          :selected-file-path="selectedFilePath"
          :documents="[]"
          selected-document-id=""
          :active-anchor="null"
          :loading="repositoryLoading"
          :show-documents="false"
          :show-summary="false"
          @select-repository="selectRepository"
          @select-file="selectRepositoryFile"
          @select-document="() => undefined"
        />
      </template>
    </AppSidebar>

    <section class="review-page-main">
      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        show-icon
        :closable="false"
      >
        <template #default>
          <el-button size="small" @click="reloadCurrentView">重试</el-button>
        </template>
      </el-alert>

      <section v-if="!requestId" class="review-list-shell">
        <header class="review-list-header">
          <div>
            <p>Code ↔ Doc Linked Workspace · 待我评审</p>
            <h1>{{ statusHeading }}</h1>
            <span>{{ workspace?.name || '工作区' }} · {{ pageData.totalElements }} 条变更请求</span>
          </div>
          <div class="review-list-tools">
            <el-input
              v-model="searchText"
              clearable
              placeholder="搜索摘要、文档或提交人"
            />
            <el-select v-model="sort" aria-label="排序">
              <el-option label="最新提交" value="createdAt,desc" />
              <el-option label="最早提交" value="createdAt,asc" />
            </el-select>
            <el-button :loading="loading" @click="loadListAndCounts">刷新</el-button>
          </div>
        </header>

        <el-skeleton v-if="loading" :rows="9" animated />
        <el-empty
          v-else-if="filteredItems.length === 0"
          :description="`${statusHeading}暂无文档变更请求`"
        />
        <div v-else class="review-request-list">
          <button
            v-for="item in filteredItems"
            :key="item.id"
            type="button"
            class="review-request-item"
            @click="openRequest(item.id)"
          >
            <div class="request-state">
              <span :class="`is-${item.status.toLowerCase()}`">{{ reviewStatusLabels[item.status] }}</span>
            </div>
            <div class="request-summary">
              <strong>{{ item.summary }}</strong>
              <p>{{ item.affectedDocumentTitles.join('、') || '未命名文档变更' }}</p>
              <small>
                {{ item.submittedByDisplayName }} · {{ formatDate(item.createdAt) }}
              </small>
            </div>
            <dl>
              <div><dt>Operations</dt><dd>{{ item.operationCount }}</dd></div>
              <div><dt>Evidence</dt><dd>{{ item.evidenceCount }}</dd></div>
            </dl>
            <span class="request-arrow">›</span>
          </button>
        </div>

        <el-pagination
          v-if="pageData.totalPages > 1"
          background
          layout="prev, pager, next"
          :current-page="pageData.page + 1"
          :page-size="pageData.size"
          :total="pageData.totalElements"
          @current-change="changePage"
        />
      </section>

      <section v-else class="review-detail-shell">
        <header class="review-detail-header">
          <div>
            <button type="button" @click="openStatus(routeStatus)">待我评审</button>
            <span>/</span>
            <strong>{{ detail?.request.summary || '加载评审请求' }}</strong>
          </div>
          <p>
            {{ workspace?.name || '工作区' }} ·
            {{ activeRepository?.name || '未选择仓库' }} ·
            {{ activeRepository?.defaultBranch || '-' }} ·
            {{ shortCommit }}
          </p>
        </header>

        <el-skeleton v-if="detailLoading" :rows="12" animated />
        <el-empty
          v-else-if="!detail || !activeOperation"
          description="评审请求不存在或没有 Operation"
        />
        <div
          v-else
          class="review-four-area"
          :class="{ 'is-inspector-collapsed': !inspectorOpen }"
        >
          <ReviewCodeEvidencePane
            :evidence="activeEvidence"
            :source-content="manualSource?.content"
            :source-path="manualSource?.path"
            :source-commit="manualSource?.commitSha"
            :source-repository="activeRepository?.name"
          />
          <ReviewDocumentPane
            :operation="activeOperation"
            :request-status="detail.request.status"
            :blocks="documentBlocks"
            :loading="documentLoading"
          />
          <ReviewInspector
            :detail="detail"
            :active-operation-id="activeOperation.operationId"
            :active-evidence-id="activeEvidence?.id || null"
            :open="inspectorOpen"
            :decision-loading="decisionLoading"
            @toggle="inspectorOpen = !inspectorOpen"
            @select-operation="selectOperation"
            @select-evidence="selectEvidence"
            @apply="confirmApply"
            @reject="openRejectDialog"
          />
        </div>
      </section>
    </section>

    <el-dialog
      v-model="applyDialogOpen"
      title="批准并应用文档变更"
      width="460px"
      append-to-body
    >
      <p>将以单个数据库事务应用 {{ detail?.operations.length || 0 }} 个 Operation。</p>
      <p>应用前会再次校验目标 Block 版本；发生冲突时不会覆盖人工修改。</p>
      <template #footer>
        <el-button @click="applyDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="decisionLoading" @click="applyRequest">
          确认批准并应用
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="rejectDialogOpen"
      title="拒绝文档变更"
      width="460px"
      append-to-body
      @closed="rejectReason = ''"
    >
      <p>请说明拒绝原因，提交人可据此重新生成建议。</p>
      <el-input
        v-model="rejectReason"
        type="textarea"
        :rows="5"
        maxlength="2000"
        show-word-limit
        placeholder="输入拒绝原因"
      />
      <template #footer>
        <el-button @click="rejectDialogOpen = false">取消</el-button>
        <el-button
          type="danger"
          :loading="decisionLoading"
          :disabled="!rejectReason.trim() || decisionLoading"
          @click="rejectRequest"
        >确认拒绝</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { listBlocks, type DocumentBlock } from '@/api/block';
import {
  applyDocumentChange,
  getDocumentChange,
  listDocumentChanges,
  rejectDocumentChange,
  type DocumentChangeDetail,
  type DocumentChangeEvidence,
  type DocumentChangeOperation,
  type DocumentChangePage,
  type DocumentChangeStatus,
} from '@/api/documentChange';
import {
  getGitRepositorySource,
  listGitRepositories,
  listGitRepositoryFiles,
  type GitRepository,
  type GitRepositoryFile,
  type GitRepositorySource,
} from '@/api/git';
import { getWorkspace, type Workspace } from '@/api/workspace';
import AppSidebar from '@/components/layout/AppSidebar.vue';
import LinkedRepositoryContext from '@/components/linked-workbench/LinkedRepositoryContext.vue';
import ReviewCodeEvidencePane from '@/components/review/ReviewCodeEvidencePane.vue';
import ReviewDocumentPane from '@/components/review/ReviewDocumentPane.vue';
import ReviewInspector from '@/components/review/ReviewInspector.vue';
import {
  reviewStatusLabels,
  selectedEvidence,
} from '@/components/review/reviewPresentation';
import { readableError } from '@/utils/error';
import { buildRepositoryTree } from '@/utils/repositoryTree';

type ReviewRouteStatus = 'pending' | 'applied' | 'rejected' | 'stale';

const route = useRoute();
const router = useRouter();
const workspaceId = computed(() => String(route.params.workspaceId || ''));
const requestId = computed(() => String(route.params.requestId || ''));
const routeStatus = computed<ReviewRouteStatus>(() => (
  ['pending', 'applied', 'rejected', 'stale'].includes(String(route.params.status))
    ? String(route.params.status) as ReviewRouteStatus
    : 'pending'
));
const apiStatus = computed<DocumentChangeStatus>(() => routeStatus.value.toUpperCase() as DocumentChangeStatus);

const workspace = ref<Workspace | null>(null);
const repositories = ref<GitRepository[]>([]);
const selectedRepositoryId = ref('');
const files = ref<GitRepositoryFile[]>([]);
const selectedFilePath = ref('');
const manualSource = ref<GitRepositorySource | null>(null);
const repositoryLoading = ref(false);
const loading = ref(false);
const detailLoading = ref(false);
const documentLoading = ref(false);
const decisionLoading = ref(false);
const errorMessage = ref('');
const searchText = ref('');
const sort = ref('createdAt,desc');
const page = ref(0);
const detail = ref<DocumentChangeDetail | null>(null);
const documentBlocks = ref<DocumentBlock[]>([]);
const inspectorOpen = ref(true);
const applyDialogOpen = ref(false);
const rejectDialogOpen = ref(false);
const rejectReason = ref('');
const sidebarCollapsed = ref(localStorage.getItem('devcollab.sidebar.collapsed') === 'true');
const statusCounts = ref<Record<ReviewRouteStatus, number>>({
  pending: 0,
  applied: 0,
  rejected: 0,
  stale: 0,
});
const pageData = ref<DocumentChangePage>({
  items: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
});

const fileTree = computed(() => buildRepositoryTree(files.value));
const activeRepository = computed(() =>
  repositories.value.find(item => item.id === selectedRepositoryId.value) ?? null);
const activeOperation = computed<DocumentChangeOperation | null>(() => {
  const operations = detail.value?.operations ?? [];
  const requested = typeof route.query.operationId === 'string' ? route.query.operationId : '';
  return operations.find(item => item.operationId === requested) ?? operations[0] ?? null;
});
const activeEvidence = computed<DocumentChangeEvidence | null>(() =>
  selectedEvidence(
    activeOperation.value,
    detail.value?.requestEvidence ?? [],
    typeof route.query.evidenceId === 'string' ? route.query.evidenceId : null,
  ));
const filteredItems = computed(() => {
  const keyword = searchText.value.trim().toLocaleLowerCase();
  if (!keyword) return pageData.value.items;
  return pageData.value.items.filter(item => [
    item.summary,
    item.submittedByDisplayName,
    ...item.affectedDocumentTitles,
  ].some(value => value.toLocaleLowerCase().includes(keyword)));
});
const statusHeading = computed(() => reviewStatusLabels[apiStatus.value]);
const shortCommit = computed(() => (
  activeEvidence.value?.commitHash
  || activeRepository.value?.lastSyncedCommit
  || '尚无 Commit'
).slice(0, 10));

onMounted(() => void loadInitialState());

watch(
  () => [route.params.status, route.params.requestId],
  ([nextStatus, nextRequest], [previousStatus, previousRequest]) => {
    if (nextStatus === previousStatus && nextRequest === previousRequest) return;
    errorMessage.value = '';
    if (nextRequest) void loadDetail();
    else {
      detail.value = null;
      void loadListAndCounts();
    }
  },
);

watch(activeOperation, () => void loadOperationDocument(), { flush: 'post' });
watch(sort, () => {
  if (!requestId.value) {
    page.value = 0;
    void loadListAndCounts();
  }
});

async function loadInitialState() {
  try {
    [workspace.value, repositories.value] = await Promise.all([
      getWorkspace(workspaceId.value),
      listGitRepositories(workspaceId.value),
    ]);
    const queryRepository = typeof route.query.repositoryId === 'string'
      ? route.query.repositoryId
      : '';
    selectedRepositoryId.value = repositories.value.some(item => item.id === queryRepository)
      ? queryRepository
      : repositories.value[0]?.id || '';
    await loadRepositoryFiles();
    if (requestId.value) await Promise.all([loadDetail(), loadCounts()]);
    else await loadListAndCounts();
  } catch (error) {
    errorMessage.value = readableError(error, '评审工作台加载失败');
  }
}

async function loadRepositoryFiles() {
  if (!selectedRepositoryId.value) {
    files.value = [];
    return;
  }
  repositoryLoading.value = true;
  try {
    files.value = await listGitRepositoryFiles(workspaceId.value, selectedRepositoryId.value);
  } finally {
    repositoryLoading.value = false;
  }
}

async function loadCounts() {
  const statuses: ReviewRouteStatus[] = ['pending', 'applied', 'rejected', 'stale'];
  const responses = await Promise.all(statuses.map(status =>
    listDocumentChanges(workspaceId.value, {
      status: status.toUpperCase() as DocumentChangeStatus,
      page: 0,
      size: 1,
    })));
  statusCounts.value = Object.fromEntries(
    statuses.map((status, index) => [status, responses[index].totalElements]),
  ) as Record<ReviewRouteStatus, number>;
}

async function loadListAndCounts() {
  loading.value = true;
  errorMessage.value = '';
  try {
    [pageData.value] = await Promise.all([
      listDocumentChanges(workspaceId.value, {
        status: apiStatus.value,
        page: page.value,
        size: 20,
        sort: sort.value,
      }),
      loadCounts(),
    ]);
  } catch (error) {
    errorMessage.value = readableError(error, '评审请求列表加载失败');
  } finally {
    loading.value = false;
  }
}

async function loadDetail() {
  if (!requestId.value) return;
  detailLoading.value = true;
  errorMessage.value = '';
  try {
    detail.value = await getDocumentChange(workspaceId.value, requestId.value);
    await normalizeDetailSelection();
  } catch (error) {
    detail.value = null;
    errorMessage.value = readableError(error, '评审请求详情加载失败');
  } finally {
    detailLoading.value = false;
  }
}

async function normalizeDetailSelection() {
  const operation = activeOperation.value ?? detail.value?.operations[0];
  if (!operation) return;
  const evidence = selectedEvidence(
    operation,
    detail.value?.requestEvidence ?? [],
    typeof route.query.evidenceId === 'string' ? route.query.evidenceId : null,
  );
  await router.replace({
    query: {
      ...route.query,
      operationId: operation.operationId,
      evidenceId: evidence?.id,
      repositoryId: evidence?.repository.id || route.query.repositoryId,
      filePath: evidence?.filePath || route.query.filePath,
    },
  });
  if (evidence) {
    manualSource.value = null;
    selectedRepositoryId.value = evidence.repository.id;
    selectedFilePath.value = evidence.filePath;
    if (!files.value.some(file => file.path === evidence.filePath)) {
      await loadRepositoryFiles();
    }
  }
  await loadOperationDocument();
}

async function loadOperationDocument() {
  const operation = activeOperation.value;
  const documentId = operation?.target.documentId;
  if (!operation || !documentId || operation.operationType === 'CREATE_DOCUMENT') {
    documentBlocks.value = [];
    return;
  }
  documentLoading.value = true;
  try {
    documentBlocks.value = await listBlocks(documentId);
  } catch {
    documentBlocks.value = [];
  } finally {
    documentLoading.value = false;
  }
}

async function selectRepository(repositoryId: string) {
  selectedRepositoryId.value = repositoryId;
  selectedFilePath.value = '';
  manualSource.value = null;
  await router.replace({ query: { ...route.query, repositoryId, filePath: undefined } });
  await loadRepositoryFiles();
}

async function selectRepositoryFile(path: string) {
  selectedFilePath.value = path;
  await router.replace({ query: { ...route.query, filePath: path } });
  try {
    manualSource.value = await getGitRepositorySource(
      workspaceId.value,
      selectedRepositoryId.value,
      path,
    );
  } catch (error) {
    manualSource.value = null;
    ElMessage.error(readableError(error, '仓库文件读取失败'));
  }
}

async function selectOperation(operationId: string) {
  const operation = detail.value?.operations.find(item => item.operationId === operationId);
  const evidence = selectedEvidence(operation ?? null, detail.value?.requestEvidence ?? []);
  await router.replace({
    query: {
      ...route.query,
      operationId,
      evidenceId: evidence?.id,
      repositoryId: evidence?.repository.id || route.query.repositoryId,
      filePath: evidence?.filePath || route.query.filePath,
    },
  });
  if (evidence) {
    manualSource.value = null;
    selectedRepositoryId.value = evidence.repository.id;
    selectedFilePath.value = evidence.filePath;
    await loadRepositoryFiles();
  }
}

async function selectEvidence(evidenceId: string) {
  const allEvidence = [
    ...(activeOperation.value?.evidence ?? []),
    ...(detail.value?.requestEvidence ?? []),
  ];
  const evidence = allEvidence.find(item => item.id === evidenceId);
  if (!evidence) return;
  selectedRepositoryId.value = evidence.repository.id;
  selectedFilePath.value = evidence.filePath;
  manualSource.value = null;
  await router.replace({
    query: {
      ...route.query,
      evidenceId,
      repositoryId: evidence.repository.id,
      filePath: evidence.filePath,
    },
  });
  await loadRepositoryFiles();
}

function openStatus(status: ReviewRouteStatus) {
  page.value = 0;
  void router.push({
    name: 'workspace-reviews',
    params: { workspaceId: workspaceId.value, status },
    query: {
      repositoryId: selectedRepositoryId.value || undefined,
    },
  });
}

function openRequest(id: string) {
  void router.push({
    name: 'workspace-review-detail',
    params: {
      workspaceId: workspaceId.value,
      status: routeStatus.value,
      requestId: id,
    },
    query: {
      repositoryId: selectedRepositoryId.value || undefined,
    },
  });
}

function openLinkedWorkbench() {
  void router.push({
    name: 'workspace-code',
    params: { workspaceId: workspaceId.value },
    query: {
      repositoryId: selectedRepositoryId.value || undefined,
      filePath: selectedFilePath.value || undefined,
    },
  });
}

function changePage(nextPage: number) {
  page.value = nextPage - 1;
  void loadListAndCounts();
}

function reloadCurrentView() {
  if (requestId.value) void loadDetail();
  else void loadListAndCounts();
}

function confirmApply() {
  applyDialogOpen.value = true;
}

function openRejectDialog() {
  rejectReason.value = '';
  rejectDialogOpen.value = true;
}

async function applyRequest() {
  if (!detail.value || decisionLoading.value) return;
  decisionLoading.value = true;
  try {
    detail.value = await applyDocumentChange(workspaceId.value, detail.value.request.id);
    applyDialogOpen.value = false;
    await loadCounts();
    await loadOperationDocument();
    if (detail.value.request.status === 'STALE') {
      ElMessage.warning('目标内容已经变化，请求已标记为失效');
    } else {
      ElMessage.success('文档变更已原子应用');
    }
  } catch (error) {
    ElMessage.error(readableError(error, '批准应用失败'));
  } finally {
    decisionLoading.value = false;
  }
}

async function rejectRequest() {
  if (!detail.value || !rejectReason.value.trim() || decisionLoading.value) return;
  decisionLoading.value = true;
  try {
    detail.value = await rejectDocumentChange(
      workspaceId.value,
      detail.value.request.id,
      rejectReason.value.trim(),
    );
    rejectDialogOpen.value = false;
    await loadCounts();
    ElMessage.success('文档变更已拒绝');
    openStatus(routeStatus.value);
  } catch (error) {
    ElMessage.error(readableError(error, '拒绝评审请求失败'));
  } finally {
    decisionLoading.value = false;
  }
}

function formatDate(value: string) {
  return new Date(value).toLocaleString('zh-CN');
}
</script>

<style scoped>
.review-page-shell {
  height: 100vh;
  overflow: hidden;
}

.review-page-main {
  display: grid;
  min-width: 0;
  min-height: 0;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 8px;
  overflow: hidden;
  padding: 12px 14px 0;
  background: #f2f5f9;
}

.review-list-shell,
.review-detail-shell {
  min-width: 0;
  min-height: 0;
  border: 1px solid #dfe6f0;
  border-radius: 10px 10px 0 0;
  background: #fff;
  overflow: hidden;
}

.review-list-shell {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  padding-bottom: 18px;
}

.review-list-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding: 24px 26px 18px;
  border-bottom: 1px solid #e4e9f1;
}

.review-list-header p,
.review-detail-header p {
  margin: 0;
  color: #667085;
  font-size: 11px;
}

.review-list-header h1 {
  margin: 4px 0;
  color: #101828;
  font-size: 26px;
}

.review-list-header span {
  color: #667085;
  font-size: 12px;
}

.review-list-tools {
  display: grid;
  width: min(560px, 52%);
  grid-template-columns: minmax(180px, 1fr) 130px auto;
  gap: 8px;
}

.review-request-list {
  min-height: 0;
  overflow: auto;
  padding: 14px 22px;
}

.review-request-item {
  display: grid;
  width: 100%;
  grid-template-columns: 94px minmax(0, 1fr) 210px 28px;
  align-items: center;
  gap: 16px;
  margin-bottom: 9px;
  border: 1px solid #e1e7ef;
  border-radius: 9px;
  padding: 15px 16px;
  background: #fff;
  color: #344054;
  text-align: left;
  cursor: pointer;
}

.review-request-item:hover {
  border-color: #b8ccff;
  background: #fbfdff;
  box-shadow: 0 5px 18px rgb(16 24 40 / 6%);
}

.request-state span {
  display: inline-flex;
  border-radius: 5px;
  padding: 4px 8px;
  background: #edf3ff;
  color: #175cd3;
  font-size: 10px;
  font-weight: 700;
}

.request-state span.is-applied {
  background: #ecfdf3;
  color: #027a48;
}

.request-state span.is-rejected {
  background: #fef3f2;
  color: #b42318;
}

.request-state span.is-stale {
  background: #fff7e6;
  color: #b54708;
}

.request-summary {
  min-width: 0;
}

.request-summary strong,
.request-summary p {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.request-summary p {
  margin: 5px 0;
  color: #475467;
  font-size: 12px;
}

.request-summary small {
  color: #98a2b3;
  font-size: 10px;
}

.review-request-item dl {
  display: grid;
  grid-template-columns: 1fr 1fr;
  margin: 0;
}

.review-request-item dl div {
  display: grid;
  justify-items: center;
  border-left: 1px solid #e5eaf1;
}

.review-request-item dt {
  color: #98a2b3;
  font-size: 9px;
}

.review-request-item dd {
  margin: 3px 0 0;
  font-weight: 700;
}

.request-arrow {
  color: #98a2b3;
  font-size: 25px;
}

.review-list-shell > .el-pagination {
  justify-self: center;
  padding-top: 12px;
}

.review-detail-shell {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
}

.review-detail-header {
  display: flex;
  min-height: 58px;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 9px 14px;
  border-bottom: 1px solid #e1e7ef;
}

.review-detail-header > div {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
}

.review-detail-header button {
  border: 0;
  padding: 0;
  background: transparent;
  color: #2454d6;
  cursor: pointer;
}

.review-detail-header strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.review-four-area {
  display: grid;
  min-width: 0;
  min-height: 0;
  grid-template-columns:
    minmax(290px, .82fr)
    minmax(420px, 1.38fr)
    minmax(320px, 360px);
  overflow: hidden;
}

.review-four-area.is-inspector-collapsed {
  grid-template-columns:
    minmax(320px, .9fr)
    minmax(480px, 1.5fr)
    42px;
}

@media (max-width: 1100px) {
  .review-list-header {
    align-items: stretch;
    flex-direction: column;
  }

  .review-list-tools {
    width: 100%;
  }

  .review-four-area {
    grid-template-columns: minmax(260px, .8fr) minmax(390px, 1.2fr) 320px;
  }
}
</style>
