<template>
  <aside ref="sidebarRef" class="sidebar" :class="{ 'is-collapsed': modelValue }">
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

    <div
      v-if="!modelValue"
      class="sidebar-resizer"
      role="separator"
      aria-label="调整导航栏宽度"
      aria-orientation="vertical"
      :aria-valuemin="MIN_SIDEBAR_WIDTH"
      :aria-valuemax="MAX_SIDEBAR_WIDTH"
      :aria-valuenow="sidebarWidth"
      tabindex="0"
      @pointerdown="startResize"
      @keydown="resizeWithKeyboard"
      @dblclick="setSidebarWidth(DEFAULT_SIDEBAR_WIDTH)"
    />

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
import { onBeforeUnmount, onMounted, ref } from 'vue';
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
const DEFAULT_SIDEBAR_WIDTH = 252;
const MIN_SIDEBAR_WIDTH = 220;
const MAX_SIDEBAR_WIDTH = 420;
const SIDEBAR_WIDTH_KEY = 'devcollab.sidebar.width';

const sidebarRef = ref<HTMLElement | null>(null);
const sidebarWidth = ref(DEFAULT_SIDEBAR_WIDTH);
let resizeStartX = 0;
let resizeStartWidth = DEFAULT_SIDEBAR_WIDTH;

onMounted(() => {
  const savedWidth = Number(localStorage.getItem(SIDEBAR_WIDTH_KEY));
  setSidebarWidth(Number.isFinite(savedWidth) && savedWidth > 0
    ? savedWidth
    : DEFAULT_SIDEBAR_WIDTH);
});

onBeforeUnmount(stopResize);

function toggle() {
  const value = !props.modelValue;
  localStorage.setItem('devcollab.sidebar.collapsed', String(value));
  emit('update:modelValue', value);
}

function openHome() {
  void router.push(props.workspaceId ? `/workspaces/${props.workspaceId}` : '/workspaces');
}

function startResize(event: PointerEvent) {
  if (event.button !== 0) return;
  event.preventDefault();
  resizeStartX = event.clientX;
  resizeStartWidth = sidebarWidth.value;
  document.body.classList.add('is-resizing-sidebar');
  window.addEventListener('pointermove', handleResize);
  window.addEventListener('pointerup', stopResize);
  window.addEventListener('pointercancel', stopResize);
}

function handleResize(event: PointerEvent) {
  setSidebarWidth(resizeStartWidth + event.clientX - resizeStartX);
}

function stopResize() {
  document.body.classList.remove('is-resizing-sidebar');
  window.removeEventListener('pointermove', handleResize);
  window.removeEventListener('pointerup', stopResize);
  window.removeEventListener('pointercancel', stopResize);
}

function resizeWithKeyboard(event: KeyboardEvent) {
  if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
  event.preventDefault();
  setSidebarWidth(sidebarWidth.value + (event.key === 'ArrowRight' ? 16 : -16));
}

function setSidebarWidth(width: number) {
  const nextWidth = Math.round(Math.min(
    MAX_SIDEBAR_WIDTH,
    Math.max(MIN_SIDEBAR_WIDTH, width),
  ));
  sidebarWidth.value = nextWidth;
  localStorage.setItem(SIDEBAR_WIDTH_KEY, String(nextWidth));
  sidebarRef.value?.parentElement?.style.setProperty(
    '--sidebar-width',
    `${nextWidth}px`,
  );
}
</script>
