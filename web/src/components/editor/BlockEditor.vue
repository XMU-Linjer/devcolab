<template>
  <section ref="editorRoot" class="block-editor">
    <header class="block-editor-header">
      <div>
        <p class="eyebrow">Tiptap Blocks</p>
        <h3>内容编辑区</h3>
        <p class="section-hint">
          每个业务 Block 使用独立 Tiptap 编辑内核，并继续通过稳定 Block ID 和 version 自动保存、检测冲突。
        </p>
      </div>

      <el-dropdown
        split-button
        type="primary"
        :loading="creating"
        :disabled="readonly"
        @click="handleCreate('PARAGRAPH')"
        @command="handleCreate"
      >
        <el-icon><Plus /></el-icon>
        新增段落
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="HEADING">新增标题</el-dropdown-item>
            <el-dropdown-item command="CODE">新增代码块</el-dropdown-item>
            <el-dropdown-item command="TODO">新增待办</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </header>

    <el-alert
      v-if="readonly"
      class="block-alert"
      title="当前文档处于只读状态，不能编辑内容。"
      type="info"
      show-icon
      :closable="false"
    />

    <el-alert
      v-if="conflictMessage"
      class="block-alert"
      :title="conflictMessage"
      type="warning"
      show-icon
      closable
      @close="conflictMessage = ''"
    >
      <template #default>
        <el-button text type="primary" @click="loadBlocks">刷新内容</el-button>
      </template>
    </el-alert>

    <el-alert
      v-if="errorMessage"
      class="block-alert"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
    >
      <template #default>
        <el-button text type="primary" @click="loadBlocks">重新加载</el-button>
      </template>
    </el-alert>

    <el-skeleton v-if="loading" :rows="6" animated />

    <el-empty
      v-else-if="blocks.length === 0"
      description="这篇文档还没有内容块"
    >
      <el-button
        type="primary"
        :icon="Plus"
        :disabled="readonly"
        @click="handleCreate('PARAGRAPH')"
      >
        创建第一个段落
      </el-button>
    </el-empty>

    <div v-else class="block-list">
      <TiptapBlock
        v-for="(block, index) in blocks"
        :key="block.id"
        :block="block"
        :is-first="index === 0"
        :is-last="index === blocks.length - 1"
        :busy="busyBlockId === block.id"
        :readonly="readonly"
        :editing-users="editingUsersByBlock(block.id)"
        :class="{ 'is-focused': focusedBlockId === block.id }"
        :data-block-id="block.id"
        @save="handleSave"
        @delete="handleDelete"
        @move-up="handleMove(block, index - 1)"
        @move-down="handleMove(block, index + 1)"
        @editing-start="emit('editing-start', block.id)"
        @editing-stop="emit('editing-stop', block.id)"
      />
    </div>
  </section>
</template>

<script setup lang="ts">
import { Plus } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { nextTick, onMounted, ref, watch } from 'vue';

import {
  createBlock,
  deleteBlock,
  listBlocks,
  moveBlock,
  updateBlock,
  type DocumentBlock,
  type DocumentBlockContent,
  type DocumentBlockType,
} from '@/api/block';
import TiptapBlock from '@/components/editor/TiptapBlock.vue';
import {
  CollaborationOperationError,
  type EditingState,
} from '@/composables/useDocumentCollaboration';
import { isConflictError, readableError } from '@/utils/error';

const props = defineProps<{
  documentId: string;
  focusBlockId?: string | null;
  readonly?: boolean;
  remoteBlock?: DocumentBlock | null;
  editingStates?: EditingState[];
  saveViaCollaboration?: (
    block: DocumentBlock,
    content: DocumentBlockContent,
  ) => Promise<DocumentBlock>;
}>();

const emit = defineEmits<{
  'editing-start': [blockId: string];
  'editing-stop': [blockId: string];
}>();

const blocks = ref<DocumentBlock[]>([]);
const editorRoot = ref<HTMLElement | null>(null);
const loading = ref(false);
const creating = ref(false);
const busyBlockId = ref<string | null>(null);
const focusedBlockId = ref<string | null>(null);
const conflictMessage = ref('');
const errorMessage = ref('');

const initialText: Record<DocumentBlockType, string> = {
  PARAGRAPH: '新的段落',
  HEADING: '新的标题',
  CODE: '// 输入代码',
  TODO: '待办事项',
};

function initialContent(type: DocumentBlockType): DocumentBlockContent {
  const text = initialText[type];
  return {
    text,
    schemaVersion: 1,
    document: {
      type: 'doc',
      content: [{
        type: 'paragraph',
        content: [{ type: 'text', text }],
      }],
    },
  };
}

onMounted(() => {
  void loadBlocks();
});

watch(
  () => props.documentId,
  () => {
    void loadBlocks();
  },
);

watch(
  () => props.focusBlockId,
  () => {
    void focusRequestedBlock();
  },
);

watch(
  () => props.remoteBlock,
  (block) => {
    if (block?.documentId === props.documentId) {
      replaceBlock(block);
    }
  },
);

async function loadBlocks() {
  loading.value = true;
  conflictMessage.value = '';
  errorMessage.value = '';
  try {
    blocks.value = await listBlocks(props.documentId);
    await focusRequestedBlock();
  } catch (error) {
    errorMessage.value = readableError(error, '内容块加载失败');
  } finally {
    loading.value = false;
  }
}

async function focusRequestedBlock() {
  if (!props.focusBlockId) {
    focusedBlockId.value = null;
    return;
  }
  await nextTick();
  const target = editorRoot.value?.querySelector<HTMLElement>(
    `[data-block-id="${props.focusBlockId}"]`,
  );
  if (!target) {
    return;
  }
  focusedBlockId.value = props.focusBlockId;
  target.scrollIntoView({ behavior: 'smooth', block: 'center' });
  window.setTimeout(() => {
    if (focusedBlockId.value === props.focusBlockId) {
      focusedBlockId.value = null;
    }
  }, 2200);
}

async function handleCreate(type: DocumentBlockType) {
  if (props.readonly) {
    return;
  }
  creating.value = true;
  try {
    const block = await createBlock(props.documentId, {
      type,
      content: initialContent(type),
    });
    blocks.value = [...blocks.value, block];
    await nextTick();
    editorRoot.value
      ?.querySelector<HTMLElement>(`[data-block-id="${block.id}"] .tiptap-content`)
      ?.focus();
    ElMessage.success('内容块已创建');
  } catch (error) {
    ElMessage.error(readableError(error, '内容块创建失败'));
  } finally {
    creating.value = false;
  }
}

async function handleSave(block: DocumentBlock, content: DocumentBlockContent) {
  if (props.readonly) {
    return;
  }
  busyBlockId.value = block.id;
  conflictMessage.value = '';
  try {
    const updated = props.saveViaCollaboration
      ? await props.saveViaCollaboration(block, content)
      : await updateBlock(props.documentId, block.id, {
          content,
          expectedVersion: block.version,
        });
    replaceBlock(updated);
  } catch (error) {
    if (
      isConflictError(error)
      || (error instanceof CollaborationOperationError && error.status === 'CONFLICT')
    ) {
      conflictMessage.value = '当前内容块已被其他成员修改，请刷新内容后再继续编辑。';
    }
    ElMessage.error(readableError(error, '内容块保存失败'));
  } finally {
    busyBlockId.value = null;
  }
}

async function handleDelete(block: DocumentBlock) {
  if (props.readonly) {
    return;
  }
  try {
    await ElMessageBox.confirm(
      '删除后该内容块会从当前文档移除，此操作暂不支持撤销。',
      '删除内容块',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    );
  } catch {
    return;
  }

  busyBlockId.value = block.id;
  try {
    await deleteBlock(props.documentId, block.id);
    blocks.value = blocks.value
      .filter((item) => item.id !== block.id)
      .map((item, index) => ({ ...item, sortOrder: index }));
    ElMessage.success('内容块已删除');
  } catch (error) {
    ElMessage.error(readableError(error, '内容块删除失败'));
  } finally {
    busyBlockId.value = null;
  }
}

async function handleMove(block: DocumentBlock, targetIndex: number) {
  if (props.readonly || targetIndex < 0 || targetIndex >= blocks.value.length) {
    return;
  }
  busyBlockId.value = block.id;
  try {
    blocks.value = await moveBlock(props.documentId, block.id, targetIndex);
  } catch (error) {
    ElMessage.error(readableError(error, '内容块排序失败'));
  } finally {
    busyBlockId.value = null;
  }
}

function replaceBlock(block: DocumentBlock) {
  blocks.value = blocks.value.map((item) => (
    item.id === block.id ? block : item
  ));
}

function editingUsersByBlock(blockId: string) {
  return (props.editingStates ?? []).filter((state) => state.blockId === blockId);
}
</script>
