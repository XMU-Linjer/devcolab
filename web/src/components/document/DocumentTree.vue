<template>
  <div class="document-tree">
    <button
      v-for="node in flattenedNodes"
      :key="node.id"
      class="document-tree-item"
      :class="{ 'is-active': node.id === activeDocumentId }"
      :style="{ paddingLeft: `${12 + node.level * 18}px` }"
      type="button"
      @click="emit('select', node.id)"
    >
      <Document class="document-tree-icon" />
      <span>{{ node.title }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { Document } from '@element-plus/icons-vue';
import { computed } from 'vue';

import type { DocumentTreeNode } from '@/api/document';

interface FlatNode {
  id: string;
  title: string;
  level: number;
}

const props = defineProps<{
  nodes: DocumentTreeNode[];
  activeDocumentId?: string;
}>();

const emit = defineEmits<{
  select: [documentId: string];
}>();

const flattenedNodes = computed(() => flatten(props.nodes));

function flatten(nodes: DocumentTreeNode[], level = 0): FlatNode[] {
  return nodes.flatMap((node) => [
    {
      id: node.id,
      title: node.title,
      level,
    },
    ...flatten(node.children, level + 1),
  ]);
}
</script>

