<template>
  <footer class="linked-status-bar">
    <span :class="{ 'is-online': collaborationConnected }">● {{ collaborationConnected ? '协作在线' : '协作离线' }}</span>
    <span>{{ membersCount }} 人在线</span>
    <span>{{ linksCount }} 条显式关联</span>
    <span v-if="activeAnchor">
      {{ activeAnchor.status }} ·
      {{ activeAnchor.startLine !== null && activeAnchor.endLine !== null
        ? `L${activeAnchor.startLine}–${activeAnchor.endLine}`
        : '文件级关联' }}
    </span>
    <span class="status-spacer" />
    <span>关系数据：正式 Binding</span>
  </footer>
</template>

<script setup lang="ts">
import type { CodeAnchor } from '@/types/linkedWorkbench';
defineProps<{ collaborationConnected: boolean; membersCount: number; linksCount: number; activeAnchor: CodeAnchor | null }>();
</script>

<style scoped>
.linked-status-bar { display: flex; min-width: 0; align-items: center; gap: 16px; overflow: hidden; padding: 7px 12px; border-top: 1px solid #dfe6f0; background: #f8fafc; color: #667085; font-size: 11px; white-space: nowrap; }
.linked-status-bar .is-online { color: #16803a; }.status-spacer { flex: 1; }
</style>
