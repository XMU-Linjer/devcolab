<template>
  <div class="document-tree">
    <div
      v-for="node in flattenedNodes"
      :key="node.id"
      class="document-tree-row"
      :class="{ 'is-active': node.id === activeDocumentId }"
      :style="{ paddingLeft: `${6 + node.level * 14}px` }"
    >
      <button
        v-if="node.hasChildren"
        class="document-tree-expand"
        type="button"
        :aria-label="node.expanded ? '收起子文档' : '展开子文档'"
        @click.stop="toggleExpanded(node.id)"
      >
        <ArrowRight :class="{ 'is-expanded': node.expanded }" />
      </button>
      <span v-else class="document-tree-expand-placeholder" />

      <button
        class="document-tree-item"
        type="button"
        :title="node.title"
        @click="emit('select', node.id)"
      >
        <Folder v-if="node.hasChildren" class="document-tree-icon" />
        <Document v-else class="document-tree-icon" />
        <span>{{ node.title }}</span>
      </button>

      <el-dropdown trigger="click" @command="handleCommand(node, $event)">
        <el-button
          class="document-tree-action"
          :icon="MoreFilled"
          text
          circle
          size="small"
          @click.stop
        />
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="create-child">新建子文档</el-dropdown-item>
            <el-dropdown-item command="rename">重命名</el-dropdown-item>
            <el-dropdown-item command="move">移动到...</el-dropdown-item>
            <el-dropdown-item
              command="move-root"
              :disabled="node.parentDocumentId === null"
            >
              移到根层级
            </el-dropdown-item>
            <el-dropdown-item divided command="delete">
              删除
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ArrowRight, Document, Folder, MoreFilled } from '@element-plus/icons-vue';
import { computed, ref, watch } from 'vue';

import type { DocumentTreeNode } from '@/api/document';

export interface FlatDocumentTreeNode {
  id: string;
  title: string;
  level: number;
  parentDocumentId: string | null;
  hasChildren?: boolean;
  expanded?: boolean;
}

const props = defineProps<{
  nodes: DocumentTreeNode[];
  activeDocumentId?: string;
}>();

const emit = defineEmits<{
  select: [documentId: string];
  'create-child': [node: FlatDocumentTreeNode];
  rename: [node: FlatDocumentTreeNode];
  move: [node: FlatDocumentTreeNode];
  'move-root': [node: FlatDocumentTreeNode];
  delete: [node: FlatDocumentTreeNode];
}>();

const expandedIds = ref(new Set<string>());

const flattenedNodes = computed(() => flatten(props.nodes));

watch(
  () => [props.nodes, props.activeDocumentId] as const,
  ([nodes, activeDocumentId]) => {
    if (!activeDocumentId) return;
    const ancestors = findAncestors(nodes, activeDocumentId);
    if (ancestors.length === 0) return;
    expandedIds.value = new Set([...expandedIds.value, ...ancestors]);
  },
  { immediate: true, deep: true },
);

function flatten(
  nodes: DocumentTreeNode[],
  level = 0,
  parentDocumentId: string | null = null,
): FlatDocumentTreeNode[] {
  return nodes.flatMap((node) => {
    const expanded = expandedIds.value.has(node.id);
    return [{
      id: node.id,
      title: node.title,
      level,
      parentDocumentId,
      hasChildren: node.children.length > 0,
      expanded,
    },
    ...(expanded ? flatten(node.children, level + 1, node.id) : []),
  ];
  });
}

function toggleExpanded(documentId: string) {
  const next = new Set(expandedIds.value);
  if (next.has(documentId)) next.delete(documentId);
  else next.add(documentId);
  expandedIds.value = next;
}

function findAncestors(
  nodes: DocumentTreeNode[],
  targetId: string,
  ancestors: string[] = [],
): string[] {
  for (const node of nodes) {
    if (node.id === targetId) return ancestors;
    const result = findAncestors(node.children, targetId, [...ancestors, node.id]);
    if (result.length > 0) return result;
  }
  return [];
}

function handleCommand(
  node: FlatDocumentTreeNode,
  command: string | number | object,
) {
  if (command === 'create-child') {
    emit('create-child', node);
  }
  if (command === 'rename') {
    emit('rename', node);
  }
  if (command === 'move') {
    emit('move', node);
  }
  if (command === 'move-root') {
    emit('move-root', node);
  }
  if (command === 'delete') {
    emit('delete', node);
  }
}
</script>
