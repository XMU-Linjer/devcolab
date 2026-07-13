<template>
  <main class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">D</span>
        <span>DevCollab</span>
      </div>

      <nav class="nav-list">
        <button class="nav-item is-active" type="button">
          <House class="nav-icon" />
          <span>工作区</span>
        </button>
        <button class="nav-item" type="button">
          <Document class="nav-icon" />
          <span>文档</span>
        </button>
        <button class="nav-item" type="button">
          <Connection class="nav-icon" />
          <span>协作</span>
        </button>
      </nav>
    </aside>

    <section class="workspace">
      <header class="topbar">
        <div>
          <p class="eyebrow">Knowledge Core</p>
          <h1>工作区</h1>
        </div>
        <div class="topbar-actions">
          <span class="current-user">
            {{ authStore.currentUser?.displayName || authStore.currentUser?.username }}
          </span>
          <el-button type="primary" :icon="Plus">创建工作区</el-button>
          <el-button :icon="SwitchButton" @click="handleLogout">
            退出
          </el-button>
        </div>
      </header>

      <section class="content-panel">
        <div class="panel-header">
          <div>
            <h2>登录链路已接入</h2>
            <p>当前工作台已经通过路由守卫保护，未登录用户会自动回到登录页。</p>
          </div>
          <el-tag type="success" effect="light">Authenticated</el-tag>
        </div>

        <div class="status-grid">
          <article class="status-card">
            <span>当前用户</span>
            <strong>{{ authStore.currentUser?.username }}</strong>
          </article>
          <article class="status-card">
            <span>认证状态</span>
            <strong>Access Token 已保存</strong>
          </article>
          <article class="status-card">
            <span>下一模块</span>
            <strong>工作区列表</strong>
          </article>
        </div>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import {
  Connection,
  Document,
  House,
  Plus,
  SwitchButton,
} from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';

import { useAuthStore } from '@/stores/auth';

const authStore = useAuthStore();
const router = useRouter();

async function handleLogout() {
  await authStore.logout();
  ElMessage.success('已退出登录');
  await router.push('/login');
}
</script>
