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
          <h1>前端工作台初始化完成</h1>
        </div>
        <el-button type="primary" :icon="Plus">创建工作区</el-button>
      </header>

      <section class="content-panel">
        <div class="panel-header">
          <div>
            <h2>下一步：登录注册链路</h2>
            <p>当前页面只是前端壳子，用来验证 Vue、路由、Pinia、Axios 和组件库已经接入。</p>
          </div>
          <el-tag type="success" effect="light">Vue 3 + TypeScript</el-tag>
        </div>

        <div class="status-grid">
          <article class="status-card">
            <span>工程</span>
            <strong>Vite 已接入</strong>
          </article>
          <article class="status-card">
            <span>状态</span>
            <strong>Pinia 已接入</strong>
          </article>
          <article class="status-card">
            <span>接口</span>
            <strong>Axios 待接登录</strong>
          </article>
        </div>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { Connection, Document, House, Plus } from '@element-plus/icons-vue';
</script>
