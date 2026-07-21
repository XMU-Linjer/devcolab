<template>
  <section class="git-panel">
    <div class="panel-title-row">
      <div>
        <p class="eyebrow">Git Knowledge</p>
        <h3>代码仓库与变更</h3>
        <p class="section-hint">仓库元数据由 Core 管理；外部 Webhook/Worker 后续复用同一变更契约。</p>
      </div>
      <el-button v-if="isAdmin" type="primary" @click="repositoryDialog = true">
        绑定仓库
      </el-button>
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
          <strong>{{ activeRepository.remoteUrl }}</strong>
          <span>默认分支：{{ activeRepository.defaultBranch }}</span>
        </div>
        <el-button v-if="isAdmin" @click="changeDialog = true">登记 Commit / PR</el-button>
      </div>

      <el-skeleton v-if="changesLoading" :rows="3" animated />
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
          <p>{{ change.authorName || '未知作者' }} · {{ formatTime(change.occurredAt) }}</p>
          <ul>
            <li v-for="file in change.files" :key="file.id">
              <span>{{ file.changeType }}</span>
              <code>{{ file.path }}</code>
              <em>+{{ file.additions }} / -{{ file.deletions }}</em>
            </li>
          </ul>
        </article>
      </div>
    </template>

    <el-dialog v-model="repositoryDialog" title="绑定 Git 仓库" width="520px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="仓库名称"><el-input v-model="repositoryForm.name" /></el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="repositoryForm.provider" class="full-width">
            <el-option label="GitHub" value="GITHUB" />
            <el-option label="GitLab" value="GITLAB" />
            <el-option label="Gitee" value="GITEE" />
            <el-option label="通用 Git" value="GENERIC" />
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
  </section>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';

import {
  ingestGitChange,
  listGitChanges,
  listGitRepositories,
  registerGitRepository,
  type GitChange,
  type GitChangeType,
  type GitFileChangeType,
  type GitProvider,
  type GitRepository,
} from '@/api/git';
import { readableError } from '@/utils/error';

const props = defineProps<{ workspaceId: string; currentUserRole: 'ADMIN' | 'MEMBER' }>();
const isAdmin = computed(() => props.currentUserRole === 'ADMIN');
const repositories = ref<GitRepository[]>([]);
const changes = ref<GitChange[]>([]);
const activeRepositoryId = ref('');
const loading = ref(false);
const changesLoading = ref(false);
const submitting = ref(false);
const repositoryDialog = ref(false);
const changeDialog = ref(false);
const activeRepository = computed(() => repositories.value.find(item => item.id === activeRepositoryId.value));
const repositoryForm = reactive({ name: '', provider: 'GENERIC' as GitProvider, remoteUrl: '', defaultBranch: 'main' });
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

async function loadRepositories() {
  loading.value = true;
  try {
    repositories.value = await listGitRepositories(props.workspaceId);
    activeRepositoryId.value ||= repositories.value[0]?.id || '';
    await loadChanges();
  } catch (error) {
    ElMessage.error(readableError(error, 'Git 仓库加载失败'));
  } finally {
    loading.value = false;
  }
}

async function loadChanges() {
  if (!activeRepositoryId.value) {
    changes.value = [];
    return;
  }
  changesLoading.value = true;
  try {
    changes.value = await listGitChanges(props.workspaceId, activeRepositoryId.value);
  } catch (error) {
    ElMessage.error(readableError(error, 'Git 变更加载失败'));
  } finally {
    changesLoading.value = false;
  }
}

function handleRepositoryChange() {
  void loadChanges();
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
      }],
    });
    changeDialog.value = false;
    await loadChanges();
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
</script>

<style scoped>
.git-panel { display: grid; gap: 18px; }
.panel-title-row, .repository-summary, .change-title { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.repository-summary { padding: 14px; border: 1px solid var(--border-color); border-radius: 12px; }
.repository-summary > div, .change-list { display: grid; gap: 10px; }
.repository-summary strong { word-break: break-all; }
.repository-summary span, .change-card p { color: var(--text-secondary); font-size: 13px; }
.change-card { padding: 14px; border: 1px solid var(--border-color); border-radius: 12px; }
.change-title { justify-content: flex-start; }
.change-card ul { margin: 10px 0 0; padding: 0; list-style: none; }
.change-card li { display: flex; gap: 10px; padding: 6px 0; font-size: 13px; }
.change-card li code { flex: 1; overflow-wrap: anywhere; }
.change-card em { color: var(--text-secondary); font-style: normal; }
.change-form { margin-top: 16px; }
.full-width { width: 100%; }
</style>
