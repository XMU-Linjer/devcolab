<template>
  <aside class="sidebar" :class="{ 'is-collapsed': modelValue }">
    <div class="brand">
      <span class="brand-mark">D</span>
      <span v-if="!modelValue" class="sidebar-label">DevCollab</span>
    </div>

    <div class="sidebar-content">
      <nav class="nav-list" aria-label="主导航">
        <button
          class="nav-item"
          :class="{ 'is-active': active === 'home' }"
          type="button"
          title="首页"
          @click="openHome"
        >
          <House class="nav-icon" />
          <span v-if="!modelValue" class="sidebar-label">首页</span>
        </button>
      </nav>

      <template v-if="workspaceId">
        <button
          v-if="modelValue"
          class="nav-item sidebar-document-shortcut"
          :class="{ 'is-active': active === 'documents' }"
          type="button"
          title="工程文档"
          @click="router.push(`/workspaces/${workspaceId}`)"
        >
          <Document class="nav-icon" />
        </button>

        <section v-else class="sidebar-document-section">
          <div class="sidebar-section-label">
            <span>工程文档</span>
            <button
              v-if="manageable"
              type="button"
              title="新建文档"
              aria-label="新建文档"
              @click="emit('create-document')"
            >
              <Plus />
            </button>
          </div>

          <p v-if="documentTree.length === 0" class="sidebar-tree-empty">暂无文档</p>
          <DocumentTree
            v-else
            :nodes="documentTree"
            :active-document-id="activeDocumentId"
            :manageable="manageable"
            @select="emit('select-document', $event)"
            @create-child="emit('create-child', $event)"
            @rename="emit('rename', $event)"
            @move="emit('move', $event)"
            @move-root="emit('move-root', $event)"
            @delete="emit('delete', $event)"
          />

          <button
            v-if="manageable"
            class="sidebar-new-document"
            type="button"
            @click="emit('create-document')"
          >
            <Plus />
            <span>新建文档</span>
          </button>
        </section>
      </template>
    </div>

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
import { DArrowLeft, DArrowRight, Document, House, Plus } from '@element-plus/icons-vue';
import { useRouter } from 'vue-router';

import type { DocumentTreeNode } from '@/api/document';
import DocumentTree, {
  type FlatDocumentTreeNode,
} from '@/components/document/DocumentTree.vue';

const props = withDefaults(defineProps<{
  modelValue: boolean;
  active: 'home' | 'documents';
  workspaceId?: string | null;
  documentTree?: DocumentTreeNode[];
  activeDocumentId?: string;
  manageable?: boolean;
}>(), {
  workspaceId: null,
  documentTree: () => [],
  activeDocumentId: undefined,
  manageable: false,
});

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
  'select-document': [documentId: string];
  'create-document': [];
  'create-child': [node: FlatDocumentTreeNode];
  rename: [node: FlatDocumentTreeNode];
  move: [node: FlatDocumentTreeNode];
  'move-root': [node: FlatDocumentTreeNode];
  delete: [node: FlatDocumentTreeNode];
}>();

const router = useRouter();

function toggle() {
  const value = !props.modelValue;
  localStorage.setItem('devcollab.sidebar.collapsed', String(value));
  emit('update:modelValue', value);
}

function openHome() {
  void router.push(props.workspaceId ? `/workspaces/${props.workspaceId}` : '/workspaces');
}
</script>
