<template>
  <aside class="sidebar" :class="{ 'is-collapsed': modelValue }">
    <div class="brand">
      <span class="brand-mark">D</span>
      <span v-if="!modelValue" class="sidebar-label">DevCollab</span>
    </div>

    <nav class="nav-list" aria-label="主导航">
      <button
        class="nav-item"
        :class="{ 'is-active': active === 'workspaces' }"
        type="button"
        title="工作区"
        @click="router.push('/workspaces')"
      >
        <House class="nav-icon" />
        <span v-if="!modelValue" class="sidebar-label">工作区</span>
      </button>
      <button
        class="nav-item"
        :class="{ 'is-active': active === 'documents' }"
        type="button"
        title="文档"
        :disabled="!workspaceId"
        @click="openDocuments"
      >
        <Document class="nav-icon" />
        <span v-if="!modelValue" class="sidebar-label">文档</span>
      </button>
    </nav>

    <button
      class="sidebar-toggle"
      type="button"
      :title="modelValue ? '展开导航' : '收起导航'"
      :aria-label="modelValue ? '展开导航' : '收起导航'"
      @click="toggle"
    >
      <DArrowRight v-if="modelValue" />
      <DArrowLeft v-else />
    </button>
  </aside>
</template>

<script setup lang="ts">
import { DArrowLeft, DArrowRight, Document, House } from '@element-plus/icons-vue';
import { useRouter } from 'vue-router';

const props = defineProps<{
  modelValue: boolean;
  active: 'workspaces' | 'documents';
  workspaceId?: string | null;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
}>();

const router = useRouter();

function toggle() {
  const value = !props.modelValue;
  localStorage.setItem('devcollab.sidebar.collapsed', String(value));
  emit('update:modelValue', value);
}

function openDocuments() {
  if (props.workspaceId) {
    void router.push(`/workspaces/${props.workspaceId}`);
  }
}
</script>
