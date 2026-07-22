<template>
  <main class="app-shell" :class="{ 'is-sidebar-collapsed': sidebarCollapsed }">
    <AppSidebar v-model="sidebarCollapsed" active="home" />

    <section class="workspace">
      <header class="topbar">
        <div>
          <p class="eyebrow">Knowledge Core</p>
          <h1>工作区</h1>
        </div>
        <div class="topbar-actions">
          <NotificationCenter />
          <span class="current-user">
            {{ authStore.currentUser?.displayName || authStore.currentUser?.username }}
          </span>
          <el-button type="primary" :icon="Plus" @click="dialogVisible = true">
            创建工作区
          </el-button>
          <el-button :icon="SwitchButton" :loading="loggingOut" @click="handleLogout">
            退出
          </el-button>
        </div>
      </header>

      <section class="content-panel">
        <div class="panel-header">
          <div>
            <h2>我的工作区</h2>
            <p>工作区是文档树、Block 编辑和协作权限的业务入口。先创建工作区，再在里面维护项目知识。</p>
          </div>
          <el-tag type="success" effect="light">
            {{ workspaces.length }} 个工作区
          </el-tag>
        </div>

        <el-alert
          v-if="errorMessage"
          class="workspace-alert"
          :title="errorMessage"
          type="error"
          show-icon
          :closable="false"
        >
          <template #default>
            <el-button text type="primary" @click="loadWorkspaces">重新加载</el-button>
          </template>
        </el-alert>

        <div v-if="loading" class="workspace-loading">
          <el-skeleton :rows="4" animated />
        </div>

        <el-empty
          v-else-if="workspaces.length === 0"
          description="还没有工作区"
        >
          <el-button type="primary" :icon="Plus" @click="dialogVisible = true">
            创建第一个工作区
          </el-button>
        </el-empty>

        <div v-else class="workspace-grid">
          <article
            v-for="workspaceItem in workspaces"
            :key="workspaceItem.id"
            class="workspace-card"
            @click="openWorkspace(workspaceItem.id)"
          >
            <div class="workspace-card-header">
              <h3>{{ workspaceItem.name }}</h3>
              <el-tag size="small" effect="light">
                {{ roleText(workspaceItem.currentUserRole) }}
              </el-tag>
            </div>
            <p>创建时间：{{ formatTime(workspaceItem.createdAt) }}</p>
            <p>更新时间：{{ formatTime(workspaceItem.updatedAt) }}</p>
            <span class="card-link">进入工作区 →</span>
          </article>
        </div>
      </section>
    </section>

    <WorkspaceCreateDialog
      v-model="dialogVisible"
      @create="handleCreateWorkspace"
    />
  </main>
</template>

<script setup lang="ts">
import { Plus, SwitchButton } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import {
  createWorkspace,
  listWorkspaces,
  type Workspace,
  type WorkspaceRole,
} from '@/api/workspace';
import NotificationCenter from '@/components/notification/NotificationCenter.vue';
import AppSidebar from '@/components/layout/AppSidebar.vue';
import WorkspaceCreateDialog from '@/components/workspace/WorkspaceCreateDialog.vue';
import { useAuthStore } from '@/stores/auth';
import { readableError } from '@/utils/error';

const authStore = useAuthStore();
const router = useRouter();

const workspaces = ref<Workspace[]>([]);
const loading = ref(false);
const loggingOut = ref(false);
const dialogVisible = ref(false);
const errorMessage = ref('');
const sidebarCollapsed = ref(
  localStorage.getItem('devcollab.sidebar.collapsed') === 'true',
);

onMounted(() => {
  void loadWorkspaces();
});

async function loadWorkspaces() {
  loading.value = true;
  errorMessage.value = '';

  try {
    workspaces.value = await listWorkspaces();
  } catch (error) {
    errorMessage.value = readableError(error, '工作区加载失败');
  } finally {
    loading.value = false;
  }
}

async function handleCreateWorkspace(name: string) {
  try {
    const workspace = await createWorkspace({ name });
    workspaces.value = [workspace, ...workspaces.value];
    dialogVisible.value = false;
    ElMessage.success('工作区创建成功');
  } catch (error) {
    ElMessage.error(readableError(error, '工作区创建失败'));
  }
}

async function openWorkspace(workspaceId: string) {
  await router.push({
    name: 'workspace-code',
    params: { workspaceId },
  });
}

async function handleLogout() {
  loggingOut.value = true;
  try {
    await authStore.logout();
    ElMessage.success('已退出登录');
    await router.push('/login');
  } finally {
    loggingOut.value = false;
  }
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
</script>
