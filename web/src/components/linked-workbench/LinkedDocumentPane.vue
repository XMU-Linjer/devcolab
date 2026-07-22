<template>
  <section class="linked-pane linked-document-pane">
    <header class="document-pane-header">
      <div>
        <strong>{{ document?.title || '选择关联文档' }}</strong>
        <span v-if="document">{{ document.documentType }} · {{ document.reviewStatus }}</span>
      </div>
      <el-tag v-if="document" size="small" :type="readonly ? 'info' : 'primary'">
        {{ readonly ? '只读' : '可编辑' }}
      </el-tag>
    </header>
    <div class="document-editor-scroll">
      <el-skeleton v-if="loading" :rows="8" animated />
      <el-empty v-else-if="!document" description="从左侧选择关联文档" />
      <BlockEditor
        v-else
        ref="blockEditorRef"
        :document-id="document.id"
        :active-block-id="activeBlockId"
        :readonly="readonly"
        :remote-block="remoteBlock"
        :editing-states="editingStates"
        :save-via-collaboration="saveViaCollaboration"
        @select-block="emit('select-block', $event)"
        @blocks-loaded="emit('blocks-loaded', $event)"
        @editing-start="emit('editing-start', $event)"
        @editing-stop="emit('editing-stop', $event)"
      />
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import type { DocumentBlock, DocumentBlockContent } from '@/api/block';
import type { DocumentSummary } from '@/api/document';
import BlockEditor from '@/components/editor/BlockEditor.vue';
import type { EditingState } from '@/composables/useDocumentCollaboration';

defineProps<{
  document: DocumentSummary | null;
  activeBlockId: string | null;
  readonly: boolean;
  loading?: boolean;
  remoteBlock?: DocumentBlock | null;
  editingStates?: EditingState[];
  saveViaCollaboration?: (
    block: DocumentBlock,
    content: DocumentBlockContent,
  ) => Promise<DocumentBlock>;
}>();

const emit = defineEmits<{
  'select-block': [blockId: string];
  'blocks-loaded': [blocks: DocumentBlock[]];
  'editing-start': [blockId: string];
  'editing-stop': [blockId: string];
}>();

const blockEditorRef = ref<InstanceType<typeof BlockEditor> | null>(null);
function focusBlock(blockId: string) {
  blockEditorRef.value?.focusBlock(blockId);
}
defineExpose({ focusBlock });
</script>

<style scoped>
.linked-document-pane { display: grid; min-width: 0; min-height: 0; grid-template-rows: auto minmax(0, 1fr); background: #fff; }
.document-pane-header { display: flex; min-width: 0; min-height: 58px; align-items: center; justify-content: space-between; gap: 10px; padding: 9px 13px; border-bottom: 1px solid #e4e9f1; }
.document-pane-header > div { display: grid; min-width: 0; gap: 3px; }
.document-pane-header strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.document-pane-header span { color: #667085; font-size: 11px; }
.document-editor-scroll { min-width: 0; min-height: 0; overflow: auto; padding: 14px; }
.document-editor-scroll :deep(.block-editor-header) { align-items: flex-start; }
.document-editor-scroll :deep(.section-hint) { display: none; }
.document-editor-scroll :deep(.paragraph-block.is-linked-active) { border-color: #155eef; background: #f4f7ff; box-shadow: 0 0 0 3px #dfeaff; }
</style>
