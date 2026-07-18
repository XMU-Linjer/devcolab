<template>
  <div class="structured-block-preview" :class="`is-${type.toLowerCase()}`">
    <template v-if="type === 'HEADING'">
      <component :is="headingTag(rootNodes[0])">
        {{ nodeText(rootNodes[0]) }}
      </component>
    </template>

    <pre v-else-if="type === 'CODE'"><code>{{ nodeText(rootNodes[0]) }}</code></pre>

    <ul v-else-if="type === 'TODO'" class="structured-task-list">
      <li v-for="(item, index) in taskItems" :key="index">
        <input
          type="checkbox"
          :checked="item.attrs?.checked === true"
          disabled
        >
        <span>{{ nodeText(item) }}</span>
      </li>
    </ul>

    <template v-else>
      <p v-if="rootNodes.length === 0">{{ text }}</p>
      <template v-else>
        <p v-for="(node, index) in rootNodes" :key="index">
          {{ nodeText(node) }}
        </p>
      </template>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

import type { DocumentBlockType, TiptapNode } from '@/api/block';

const props = defineProps<{
  type: DocumentBlockType;
  text: string;
  document?: TiptapNode | null;
}>();

const rootNodes = computed(() => props.document?.content ?? []);
const taskItems = computed<TiptapNode[]>(() => {
  const root = rootNodes.value[0];
  if (root?.type === 'taskList') {
    return root.content ?? [];
  }
  return [{
    type: 'taskItem',
    attrs: { checked: false },
    content: [{
      type: 'paragraph',
      content: props.text ? [{ type: 'text', text: props.text }] : [],
    }],
  }];
});

function headingTag(node?: TiptapNode) {
  const level = node?.attrs?.level;
  return level === 1 || level === 2 || level === 3 ? `h${level}` : 'h2';
}

function nodeText(node?: TiptapNode): string {
  if (!node) {
    return props.text;
  }
  if (node.type === 'text') {
    return node.text ?? '';
  }
  if (node.type === 'hardBreak') {
    return '\n';
  }
  return (node.content ?? []).map(nodeText).join('');
}
</script>
