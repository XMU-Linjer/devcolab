<template>
  <div class="document-tree">
    <div
      v-for="node in flattenedNodes"
      :key="node.id"
      class="document-tree-row"
      :class="{ 'is-active': node.id === activeDocumentId }"
      :style="{ paddingLeft: `${12 + node.level * 18}px` }"
    >
      <button
        class="document-tree-item"
        type="button"
        @click="emit('select', node.id)"
      >
        <Document class="document-tree-icon" />
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
import { Document, MoreFilled } from '@element-plus/icons-vue';
import { computed } from 'vue';

import type { DocumentTreeNode } from '@/api/document';

export interface FlatDocumentTreeNode {
  id: string;
  title: string;
  level: number;
  parentDocumentId: string | null;
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

const flattenedNodes = computed(() => flatten(props.nodes));

function flatten(
  nodes: DocumentTreeNode[],
  level = 0,
  parentDocumentId: string | null = null,
): FlatDocumentTreeNode[] {
  return nodes.flatMap((node) => [
    {
      id: node.id,
      title: node.title,
      level,
      parentDocumentId,
    },
    ...flatten(node.children, level + 1, node.id),
  ]);
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
