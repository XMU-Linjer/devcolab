<template>
  <section class="review-document-pane">
    <header>
      <div>
        <strong>{{ documentTitle }}</strong>
        <span>当前正式文档 · {{ operationLabel }}</span>
      </div>
      <el-tag size="small" effect="plain">{{ statusLabel }}</el-tag>
    </header>

    <div class="review-document-scroll">
      <el-skeleton v-if="loading" :rows="10" animated />
      <template v-else>
        <el-alert
          v-if="operation.conflict.conflicted"
          title="目标内容已发生变化；评审仍可查看，但不能覆盖人工修改。"
          type="warning"
          :closable="false"
          show-icon
        />

        <div v-if="operation.operationType === 'CREATE_DOCUMENT'" class="create-preview">
          <span class="proposal-badge">建议创建</span>
          <h2>{{ operation.proposal.documentTitle }}</h2>
          <p>{{ operation.proposal.documentType || 'REQUIREMENT' }}</p>
        </div>

        <template v-else>
          <section class="document-context">
            <p class="section-kicker">CURRENT DOCUMENT</p>
            <h2>{{ documentTitle }}</h2>
            <article
              v-for="block in contextualBlocks"
              :key="block.id"
              class="document-block"
              :class="{ 'is-target': block.id === operation.target.blockId }"
            >
              <small>Block {{ block.sortOrder + 1 }} · V{{ block.version }}</small>
              <StructuredBlockPreview
                :type="block.type"
                :text="block.content.text"
                :document="block.content.document"
              />
            </article>
            <el-empty
              v-if="contextualBlocks.length === 0"
              description="当前文档没有可显示的正式 Block"
            />
          </section>
        </template>

        <section class="inline-diff" :class="`is-${operation.operationType.toLowerCase()}`">
          <div class="diff-heading">
            <strong>{{ operationLabel }}</strong>
            <span v-if="operation.target.blockId">Block {{ shortBlockId }}</span>
          </div>
          <div v-if="showsBefore" class="diff-row is-before">
            <span>当前</span>
            <p>{{ beforeText || '（空内容）' }}</p>
          </div>
          <div v-if="showsAfter" class="diff-row is-after">
            <span>建议</span>
            <p>{{ afterText || '（空内容）' }}</p>
          </div>
        </section>
      </template>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';

import type { DocumentBlock } from '@/api/block';
import type {
  DocumentChangeOperation,
  DocumentChangeStatus,
} from '@/api/documentChange';
import StructuredBlockPreview from '@/components/document/StructuredBlockPreview.vue';
import {
  currentText,
  operationDocumentTitle,
  operationLabels,
  proposalText,
  reviewStatusLabels,
} from './reviewPresentation';

const props = defineProps<{
  operation: DocumentChangeOperation;
  requestStatus: DocumentChangeStatus;
  blocks: DocumentBlock[];
  loading?: boolean;
}>();

const documentTitle = computed(() => operationDocumentTitle(props.operation));
const operationLabel = computed(() => operationLabels[props.operation.operationType]);
const statusLabel = computed(() => reviewStatusLabels[props.requestStatus]);
const beforeText = computed(() => currentText(props.operation));
const afterText = computed(() => proposalText(props.operation));
const shortBlockId = computed(() => props.operation.target.blockId?.slice(0, 8) ?? '');
const showsBefore = computed(() => (
  props.operation.operationType === 'UPDATE_BLOCK'
  || props.operation.operationType === 'DELETE_BLOCK'
));
const showsAfter = computed(() => props.operation.operationType !== 'DELETE_BLOCK');
const contextualBlocks = computed(() => {
  if (!props.operation.target.blockId) return props.blocks;
  const targetIndex = props.blocks.findIndex(item => item.id === props.operation.target.blockId);
  if (targetIndex < 0) return props.blocks.slice(0, 4);
  return props.blocks.slice(Math.max(0, targetIndex - 1), targetIndex + 2);
});
</script>

<style scoped>
.review-document-pane {
  display: grid;
  min-width: 0;
  min-height: 0;
  grid-template-rows: auto minmax(0, 1fr);
  background: #fff;
}

header {
  display: flex;
  min-width: 0;
  min-height: 64px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  border-bottom: 1px solid #e4e9f1;
}

header div {
  display: grid;
  min-width: 0;
  gap: 4px;
}

header strong {
  overflow: hidden;
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

header span {
  color: #667085;
  font-size: 11px;
}

.review-document-scroll {
  min-width: 0;
  overflow: auto;
  padding: 22px 28px 36px;
}

.section-kicker {
  margin: 0 0 5px;
  color: #667085;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: .08em;
}

.document-context h2,
.create-preview h2 {
  margin: 0 0 20px;
  color: #101828;
  font-size: 24px;
}

.document-block {
  margin: 0 0 14px;
  border-left: 3px solid transparent;
  padding: 12px 15px;
  color: #344054;
}

.document-block.is-target {
  border-color: #3566f0;
  border-radius: 8px;
  background: #f5f8ff;
}

.document-block small {
  color: #667085;
  font-size: 10px;
}

.inline-diff {
  margin-top: 24px;
  border: 1px solid #dbe3ef;
  border-radius: 9px;
  overflow: hidden;
}

.diff-heading {
  display: flex;
  justify-content: space-between;
  padding: 9px 12px;
  background: #f8fafc;
  color: #475467;
  font-size: 11px;
}

.diff-row {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr);
  border-top: 1px solid #e6ebf2;
  padding: 10px 12px;
}

.diff-row > span {
  font-size: 11px;
  font-weight: 700;
}

.diff-row p {
  margin: 0;
  line-height: 1.65;
  white-space: pre-wrap;
}

.diff-row.is-before {
  background: #fff5f5;
}

.diff-row.is-before > span {
  color: #b42318;
}

.diff-row.is-after {
  background: #f0fdf4;
}

.diff-row.is-after > span {
  color: #027a48;
}

.proposal-badge {
  display: inline-flex;
  margin-bottom: 10px;
  border-radius: 5px;
  padding: 4px 8px;
  background: #e8f0ff;
  color: #155eef;
  font-size: 11px;
  font-weight: 700;
}
</style>
