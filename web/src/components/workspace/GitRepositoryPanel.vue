<template>
  <section class="git-panel">
    <div class="panel-title-row">
      <div>
        <p class="eyebrow">Git Knowledge</p>
        <h3>代码仓库与变更</h3>
        <p class="section-hint">仓库元数据由 Core 管理；外部 Webhook/Worker 后续复用同一变更契约。</p>
      </div>
      <div class="repository-actions">
        <el-button
          v-if="activeRepository"
          type="primary"
          plain
          @click="openCodeWorkbench"
        >
          打开代码工作台
        </el-button>
        <el-button v-if="isAdmin" type="primary" @click="repositoryDialog = true">
          绑定仓库
        </el-button>
      </div>
    </div>

    <el-skeleton v-if="loading" :rows="4" animated />
    <el-empty v-else-if="repositories.length === 0" description="尚未绑定 Git 仓库" />
    <template v-else>
      <el-tabs v-model="activeRepositoryId" @tab-change="handleRepositoryChange">
        <el-tab-pane
          v-for="repository in repositories"
          :key="repository.id"
          :label="repository.name"
          :name="repository.id"
        />
      </el-tabs>

      <div v-if="activeRepository" class="repository-summary">
        <div>
          <el-tag effect="plain">{{ activeRepository.provider }}</el-tag>
          <el-tag :type="statusType(activeRepository.syncStatus)">
            {{ statusText(activeRepository.syncStatus) }}
          </el-tag>
          <strong>{{ activeRepository.remoteUrl }}</strong>
          <span>默认分支：{{ activeRepository.defaultBranch }}</span>
          <span v-if="activeRepository.lastSyncedCommit">
            HEAD：{{ activeRepository.lastSyncedCommit.slice(0, 10) }}
          </span>
        </div>
        <div v-if="isAdmin" class="repository-actions">
          <el-button
            :loading="syncing"
            :disabled="activeRepository.syncStatus === 'SYNCING' || activeRepository.syncStatus === 'SYNC_PENDING'"
            @click="handleSync"
          >同步</el-button>
          <el-button @click="changeDialog = true">调试录入</el-button>
          <el-button type="danger" plain @click="handleDeleteRepository">删除</el-button>
        </div>
      </div>

      <el-alert
        v-if="activeRepository?.lastSyncError"
        :title="activeRepository.lastSyncError"
        type="error"
        show-icon
        :closable="false"
      />

      <section class="repository-log">
        <div class="repository-log-title">
          <strong>最近变更</strong>
          <span>完整代码与文件树请进入代码工作台</span>
        </div>
        <el-skeleton v-if="detailsLoading" :rows="3" animated />
        <el-empty v-else-if="changes.length === 0" description="暂无同步变更" />
        <div v-else class="change-list">
          <article v-for="change in changes" :key="change.id" class="change-card">
            <div class="change-title">
              <el-tag size="small" :type="change.changeType === 'PULL_REQUEST' ? 'success' : 'info'">
                {{ change.changeType === 'PULL_REQUEST' ? 'PR' : 'Commit' }}
              </el-tag>
              <strong>{{ change.title }}</strong>
              <code>{{ change.commitSha.slice(0, 8) }}</code>
            </div>
            <div class="identity-row">
              <span>
                作者：{{ identity(change.authorName, change.authorEmail) }}
                · {{ formatTime(change.authoredAt || change.occurredAt) }}
              </span>
              <span v-if="hasDifferentCommitter(change)">
                提交者：{{ identity(change.committerName, change.committerEmail) }}
                · {{ formatTime(change.occurredAt) }}
              </span>
            </div>
            <ul>
              <li v-for="file in change.files" :key="file.id" class="diff-row">
                <span>{{ file.changeType }}</span>
                <button class="diff-path" type="button" @click="openDiff(change, file)">
                  <code>{{ file.path }}</code>
                  <small v-if="file.oldPath">原路径：{{ file.oldPath }}</small>
                </button>
                <em v-if="file.binaryFile">二进制变更</em>
                <em v-else class="line-stats">
                  <b>+{{ file.additions }}</b> / <i>-{{ file.deletions }}</i>
                </em>
              </li>
            </ul>
          </article>
        </div>
      </section>
    </template>

    <el-dialog v-model="repositoryDialog" title="绑定 Git 仓库" width="520px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="仓库名称"><el-input v-model="repositoryForm.name" /></el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="repositoryForm.provider" class="full-width" disabled>
            <el-option label="GitHub（公开 HTTPS 仓库）" value="GITHUB" />
          </el-select>
        </el-form-item>
        <el-form-item label="远程地址"><el-input v-model="repositoryForm.remoteUrl" placeholder="https://..." /></el-form-item>
        <el-form-item label="默认分支"><el-input v-model="repositoryForm.defaultBranch" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="repositoryDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleRegister">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="changeDialog" title="登记标准化 Git 变更" width="620px" destroy-on-close>
      <el-alert title="这是 Webhook/Worker 的调试入口，不保存 Git Token，也不会执行 git clone。" type="info" show-icon :closable="false" />
      <el-form label-position="top" class="change-form">
        <el-form-item label="类型">
          <el-radio-group v-model="changeForm.changeType">
            <el-radio-button value="COMMIT">Commit</el-radio-button>
            <el-radio-button value="PULL_REQUEST">Pull Request</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="外部唯一 ID"><el-input v-model="changeForm.externalId" /></el-form-item>
        <el-form-item label="标题"><el-input v-model="changeForm.title" /></el-form-item>
        <el-form-item label="Commit SHA"><el-input v-model="changeForm.commitSha" /></el-form-item>
        <el-form-item label="作者"><el-input v-model="changeForm.authorName" /></el-form-item>
        <el-form-item label="变化文件路径"><el-input v-model="changeForm.path" placeholder="knowledge-core/src/..." /></el-form-item>
        <el-form-item label="文件变化">
          <el-select v-model="changeForm.fileChangeType" class="full-width">
            <el-option label="新增" value="ADDED" />
            <el-option label="修改" value="MODIFIED" />
            <el-option label="删除" value="DELETED" />
            <el-option label="重命名" value="RENAMED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="changeDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleIngest">登记</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="diffDialog" title="文件变更" width="860px" destroy-on-close>
      <template v-if="selectedDiff">
        <div class="diff-dialog-summary">
          <code>{{ selectedDiff.file.path }}</code>
          <span v-if="selectedDiff.file.binaryFile">二进制文件无法显示行级 Diff</span>
          <span v-else>+{{ selectedDiff.file.additions }} / -{{ selectedDiff.file.deletions }}</span>
          <small>基准：{{ selectedDiff.change.parentCommitSha?.slice(0, 8) || '空目录（首次提交）' }}</small>
        </div>
        <pre v-if="selectedDiff.file.patchExcerpt" class="patch-view">{{ selectedDiff.file.patchExcerpt }}</pre>
        <el-empty v-else :description="selectedDiff.file.binaryFile ? '二进制内容不生成 Patch' : '该文件没有可展示的文本 Patch'" />
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import {
  deleteGitRepository,
  ingestGitChange,
  listGitChanges,
  listGitRepositories,
  registerGitRepository,
  syncGitRepository,
  type GitChange,
  type GitChangeType,
  type GitFileChangeType,
  type GitFileDiff,
  type GitProvider,
  type GitRepository,
} from '@/api/git';
import { readableError } from '@/utils/error';

const props = defineProps<{ workspaceId: string; currentUserRole: 'ADMIN' | 'MEMBER' }>();
const router = useRouter();
const isAdmin = computed(() => props.currentUserRole === 'ADMIN');
const repositories = ref<GitRepository[]>([]);
const changes = ref<GitChange[]>([]);
const activeRepositoryId = ref('');
const loading = ref(false);
const detailsLoading = ref(false);
const submitting = ref(false);
const syncing = ref(false);
const repositoryDialog = ref(false);
const changeDialog = ref(false);
const diffDialog = ref(false);
const selectedDiff = ref<{ change: GitChange; file: GitFileDiff } | null>(null);
const activeRepository = computed(() => repositories.value.find(item => item.id === activeRepositoryId.value));
const repositoryForm = reactive({ name: '', provider: 'GITHUB' as GitProvider, remoteUrl: '', defaultBranch: 'main' });
const changeForm = reactive({
  changeType: 'COMMIT' as GitChangeType,
  externalId: '',
  title: '',
  commitSha: '',
  authorName: '',
  path: '',
  fileChangeType: 'MODIFIED' as GitFileChangeType,
});

onMounted(() => void loadRepositories());
const pollTimer = window.setInterval(() => {
  if (repositories.value.some(item => item.syncStatus === 'SYNC_PENDING' || item.syncStatus === 'SYNCING')) {
    void loadRepositories(false);
  }
}, 2500);
onBeforeUnmount(() => window.clearInterval(pollTimer));

async function loadRepositories(showLoading = true) {
  if (showLoading) loading.value = true;
  try {
    repositories.value = await listGitRepositories(props.workspaceId);
    activeRepositoryId.value ||= repositories.value[0]?.id || '';
    await loadDetails();
  } catch (error) {
    ElMessage.error(readableError(error, 'Git 仓库加载失败'));
  } finally {
    if (showLoading) loading.value = false;
  }
}

async function loadDetails() {
  if (!activeRepositoryId.value) {
    changes.value = [];
    return;
  }
  detailsLoading.value = true;
  try {
    changes.value = await listGitChanges(props.workspaceId, activeRepositoryId.value);
  } catch (error) {
    ElMessage.error(readableError(error, 'Git 变更加载失败'));
  } finally {
    detailsLoading.value = false;
  }
}

function handleRepositoryChange() {
  void loadDetails();
}

function openCodeWorkbench() {
  if (!activeRepositoryId.value) return;
  void router.push({
    name: 'workspace-code',
    params: { workspaceId: props.workspaceId },
    query: { repositoryId: activeRepositoryId.value },
  });
}

async function handleRegister() {
  if (!repositoryForm.name.trim() || !repositoryForm.remoteUrl.trim()) {
    ElMessage.warning('请填写仓库名称和远程地址');
    return;
  }
  submitting.value = true;
  try {
    const repository = await registerGitRepository(props.workspaceId, repositoryForm);
    repositories.value.unshift(repository);
    activeRepositoryId.value = repository.id;
    repositoryDialog.value = false;
    changes.value = [];
    ElMessage.success('Git 仓库已绑定');
  } catch (error) {
    ElMessage.error(readableError(error, 'Git 仓库绑定失败'));
  } finally {
    submitting.value = false;
  }
}

async function handleIngest() {
  if (!activeRepositoryId.value || !changeForm.externalId.trim()
    || !changeForm.title.trim() || !changeForm.commitSha.trim() || !changeForm.path.trim()) {
    ElMessage.warning('请填写变更和文件信息');
    return;
  }
  submitting.value = true;
  try {
    const change = await ingestGitChange(props.workspaceId, activeRepositoryId.value, {
      changeType: changeForm.changeType,
      externalId: changeForm.externalId,
      title: changeForm.title,
      commitSha: changeForm.commitSha,
      headRef: activeRepository.value?.defaultBranch,
      authorName: changeForm.authorName || null,
      occurredAt: new Date().toISOString(),
      files: [{
        path: changeForm.path,
        changeType: changeForm.fileChangeType,
        additions: 0,
        deletions: 0,
        binaryFile: false,
      }],
    });
    changeDialog.value = false;
    await loadDetails();
    ElMessage.success(change.duplicate ? '重复事件已幂等返回' : 'Git 变更已登记');
  } catch (error) {
    ElMessage.error(readableError(error, 'Git 变更登记失败'));
  } finally {
    submitting.value = false;
  }
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

function identity(name: string | null, email: string | null) {
  const safeName = name || '未知';
  return email ? `${safeName} <${email}>` : safeName;
}

function hasDifferentCommitter(change: GitChange) {
  if (!change.committerName && !change.committerEmail) return false;
  return change.committerName !== change.authorName || change.committerEmail !== change.authorEmail;
}

function openDiff(change: GitChange, file: GitFileDiff) {
  selectedDiff.value = { change, file };
  diffDialog.value = true;
}

async function handleSync() {
  if (!activeRepositoryId.value) return;
  syncing.value = true;
  try {
    const updated = await syncGitRepository(props.workspaceId, activeRepositoryId.value);
    repositories.value = repositories.value.map(item => item.id === updated.id ? updated : item);
    ElMessage.success('仓库同步任务已提交');
  } catch (error) {
    ElMessage.error(readableError(error, '仓库同步失败'));
  } finally {
    syncing.value = false;
  }
}

async function handleDeleteRepository() {
  const repository = activeRepository.value;
  if (!repository) return;
  try {
    await ElMessageBox.confirm(`确定删除仓库“${repository.name}”及本地副本吗？`, '删除仓库', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消',
    });
    await deleteGitRepository(props.workspaceId, repository.id);
    repositories.value = repositories.value.filter(item => item.id !== repository.id);
    activeRepositoryId.value = repositories.value[0]?.id || '';
    await loadDetails();
    ElMessage.success('仓库删除任务已提交');
  } catch (error) {
    if (error === 'cancel' || error === 'close') return;
    ElMessage.error(readableError(error, '仓库删除失败'));
  }
}

function statusText(status: GitRepository['syncStatus']) {
  return { REGISTERED: '待同步', SYNC_PENDING: '等待同步', SYNCING: '同步中', READY: '已同步', FAILED: '同步失败' }[status];
}

function statusType(status: GitRepository['syncStatus']) {
  if (status === 'READY') return 'success';
  if (status === 'FAILED') return 'danger';
  if (status === 'SYNCING' || status === 'SYNC_PENDING') return 'warning';
  return 'info';
}

</script>

<style scoped>
.git-panel { display: grid; gap: 18px; }
.panel-title-row, .repository-summary, .change-title { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.repository-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.repository-summary { padding: 14px; border: 1px solid var(--border-color); border-radius: 12px; }
.repository-summary > div, .change-list { display: grid; gap: 10px; }
.repository-summary strong { word-break: break-all; }
.repository-summary span, .identity-row { color: var(--text-secondary); font-size: 13px; }
.identity-row { display: flex; gap: 8px 20px; flex-wrap: wrap; }
.change-card { padding: 14px; border: 1px solid var(--border-color); border-radius: 12px; }
.change-title { justify-content: flex-start; }
.change-card ul { margin: 10px 0 0; padding: 0; list-style: none; }
.change-card li { display: flex; gap: 10px; padding: 6px 0; font-size: 13px; }
.change-card li code { flex: 1; overflow-wrap: anywhere; }
.change-card em { color: var(--text-secondary); font-style: normal; }
.diff-path { flex: 1; display: grid; gap: 3px; padding: 0; border: 0; background: transparent; text-align: left; cursor: pointer; color: inherit; }
.diff-path:hover code { color: var(--el-color-primary); text-decoration: underline; }
.diff-path small { color: var(--text-secondary); }
.line-stats b { color: var(--el-color-success); }
.line-stats i { color: var(--el-color-danger); font-style: normal; }
.diff-dialog-summary { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; margin-bottom: 12px; }
.diff-dialog-summary code { flex: 1; min-width: 260px; overflow-wrap: anywhere; }
.diff-dialog-summary small { color: var(--text-secondary); }
.patch-view { max-height: 560px; overflow: auto; margin: 0; padding: 16px; border-radius: 8px; background: #0f172a; color: #e2e8f0; font-size: 12px; line-height: 1.55; white-space: pre; }
.change-form { margin-top: 16px; }
.full-width { width: 100%; }
.repository-log { display: grid; gap: 14px; padding-top: 4px; }
.repository-log-title { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.repository-log-title span { color: var(--text-secondary); font-size: 12px; }
</style>
