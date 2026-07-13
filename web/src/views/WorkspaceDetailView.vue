<template>
  <main class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">D</span>
        <span>DevCollab</span>
      </div>

      <nav class="nav-list">
        <button class="nav-item is-active" type="button" @click="router.push('/workspaces')">
          <House class="nav-icon" />
          <span>工作区</span>
        </button>
        <button class="nav-item" type="button">
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
        <el-button :icon="Back" @click="router.push('/workspaces')">
          返回列表
        </el-button>
      </header>

      <section class="content-panel">
        <el-skeleton v-if="loading" :rows="4" animated />

        <el-alert
          v-else-if="errorMessage"
          :title="errorMessage"
          type="error"
          show-icon
          :closable="false"
        />

        <div v-else class="panel-header">
          <div>
            <h2>下一步：文档树链路</h2>
            <p>这里已经能按 ID 读取工作区，下一批会接入文档树、创建文档和打开文档。</p>
          </div>
          <el-tag effect="light">{{ workspace?.currentUserRole }}</el-tag>
        </div>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { Back, Document, House } from '@element-plus/icons-vue';
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { getWorkspace, type Workspace } from '@/api/workspace';
import { readableError } from '@/utils/error';

const route = useRoute();
const router = useRouter();

const workspace = ref<Workspace | null>(null);
const loading = ref(false);
const errorMessage = ref('');

onMounted(() => {
  void loadWorkspace();
});

async function loadWorkspace() {
  const workspaceId = route.params.workspaceId;

  if (typeof workspaceId !== 'string') {
    errorMessage.value = '工作区地址无效';
    return;
  }

  loading.value = true;
  errorMessage.value = '';

  try {
    workspace.value = await getWorkspace(workspaceId);
  } catch (error) {
    errorMessage.value = readableError(error, '工作区加载失败');
  } finally {
    loading.value = false;
  }
}
</script>

