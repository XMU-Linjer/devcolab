<template>
  <header class="linked-workbench-header">
    <div class="linked-heading">
      <p class="eyebrow">Code ↔ Doc Linked Workspace</p>
      <h1>{{ workspaceName || '工程关联工作台' }}</h1>
      <p>{{ repositoryName || '未选择仓库' }} · {{ branch || '-' }} · {{ shortCommit }}</p>
    </div>
    <div class="linked-header-actions">
      <div class="mode-switcher" role="group" aria-label="工作台模式">
        <button
          v-for="item in modes"
          :key="item.value"
          type="button"
          :class="{ 'is-active': mode === item.value }"
          @click="emit('set-mode', item.value)"
        >{{ item.label }}</button>
      </div>
      <el-button size="small" @click="emit('toggle-inspector')">
        {{ inspectorOpen ? '收起检查器' : '打开检查器' }}
      </el-button>
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { WorkbenchMode } from '@/types/linkedWorkbench';

const props = defineProps<{
  workspaceName?: string;
  repositoryName?: string;
  branch?: string;
  commitSha?: string | null;
  mode: WorkbenchMode;
  inspectorOpen: boolean;
}>();

const emit = defineEmits<{
  'set-mode': [mode: WorkbenchMode];
  'toggle-inspector': [];
}>();

const shortCommit = computed(() => props.commitSha?.slice(0, 10) || '尚无 Commit');
const modes: Array<{ value: WorkbenchMode; label: string }> = [
  { value: 'LINKED', label: '关联对照' },
  { value: 'CODE_FOCUS', label: '代码聚焦' },
  { value: 'DOCUMENT_FOCUS', label: '文档聚焦' },
  { value: 'DRIFT_REVIEW', label: '漂移审查' },
];
</script>

<style scoped>
.linked-workbench-header { display: flex; min-width: 0; align-items: center; justify-content: space-between; gap: 18px; padding: 14px 18px; border-bottom: 1px solid #dfe6f0; background: #fff; }
.linked-heading { min-width: 0; }
.linked-heading h1 { margin: 2px 0 3px; font-size: 23px; }
.linked-heading p:last-child { overflow: hidden; margin: 0; color: #667085; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.linked-header-actions { display: flex; align-items: center; justify-content: flex-end; gap: 8px; }
.mode-switcher { display: flex; overflow: hidden; border: 1px solid #d0d7e2; border-radius: 7px; }
.mode-switcher button { border: 0; border-right: 1px solid #d0d7e2; padding: 7px 10px; background: #fff; color: #475467; cursor: pointer; }
.mode-switcher button:last-child { border-right: 0; }
.mode-switcher button.is-active { background: #155eef; color: #fff; }
@media (max-width: 1100px) { .linked-workbench-header { align-items: flex-start; flex-direction: column; } .linked-header-actions { width: 100%; flex-wrap: wrap; justify-content: flex-start; } }
</style>
