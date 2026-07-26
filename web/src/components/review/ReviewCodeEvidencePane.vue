<template>
  <section class="review-code-pane">
    <header>
      <div>
        <strong :title="displayPath">{{ displayPath || '选择 Evidence 查看代码' }}</strong>
        <span v-if="hasContent">
          {{ displayRepository }} · {{ shortCommit }} · {{ rangeLabel }}
        </span>
      </div>
      <el-tag v-if="hasContent" size="small" effect="plain">{{ rangeLabel }}</el-tag>
    </header>

    <el-empty
      v-if="!hasContent"
      description="当前 Operation 没有关联代码证据"
    />
    <div v-else class="evidence-content" role="list" aria-label="提交时可信代码证据">
      <div
        v-for="(line, index) in lines"
        :key="index"
        class="evidence-line"
      >
        <span>{{ lineNumber(index) }}</span>
        <code>{{ line || ' ' }}</code>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';

import type { DocumentChangeEvidence } from '@/api/documentChange';
import { evidenceRangeLabel } from './reviewPresentation';

const props = defineProps<{
  evidence: DocumentChangeEvidence | null;
  sourceContent?: string | null;
  sourcePath?: string;
  sourceCommit?: string | null;
  sourceRepository?: string;
}>();

const hasManualSource = computed(() => props.sourceContent != null);
const hasContent = computed(() => hasManualSource.value || Boolean(props.evidence));
const displayPath = computed(() => hasManualSource.value
  ? props.sourcePath
  : props.evidence?.filePath);
const displayRepository = computed(() => hasManualSource.value
  ? props.sourceRepository || '仓库文件'
  : props.evidence?.repository.name || '');
const lines = computed(() => (
  hasManualSource.value ? props.sourceContent : props.evidence?.excerptText
)?.split(/\r?\n/) ?? []);
const shortCommit = computed(() => (
  hasManualSource.value ? props.sourceCommit : props.evidence?.commitHash
)?.slice(0, 10) ?? '');
const rangeLabel = computed(() => {
  if (hasManualSource.value) return `L1–${lines.value.length}`;
  return props.evidence ? evidenceRangeLabel(props.evidence) : '';
});

function lineNumber(index: number) {
  if (hasManualSource.value) return index + 1;
  return (props.evidence?.startLine ?? 1) + index;
}
</script>

<style scoped>
.review-code-pane {
  display: grid;
  min-width: 0;
  min-height: 0;
  grid-template-rows: auto minmax(0, 1fr);
  border-right: 1px solid #e3e8ef;
  background: #fbfcfe;
}

header {
  display: flex;
  min-width: 0;
  min-height: 64px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-bottom: 1px solid #e4e9f1;
  background: #fff;
}

header div {
  display: grid;
  min-width: 0;
  gap: 4px;
}

header strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

header span {
  color: #667085;
  font-size: 11px;
}

.evidence-content {
  min-width: 0;
  overflow: auto;
  padding: 10px 0 28px;
}

.evidence-line {
  display: grid;
  min-width: max-content;
  grid-template-columns: 54px minmax(0, 1fr);
  border-left: 3px solid #3974f6;
  background: #e9f1ff;
}

.evidence-line > span {
  padding-right: 10px;
  color: #7a8aa4;
  font: 11px/23px Consolas, monospace;
  text-align: right;
  user-select: none;
}

.evidence-line code {
  min-height: 23px;
  padding-right: 18px;
  color: #344054;
  font: 12px/23px Consolas, 'Cascadia Code', monospace;
  white-space: pre;
}
</style>
