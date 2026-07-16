<template>
  <section ref="editorRoot" class="block-editor">
    <header class="block-editor-header">
      <div>
        <p class="eyebrow">Blocks</p>
        <h3>内容编辑区</h3>
        <p class="section-hint">
          MVP 使用稳定 Block ID + version 做自动保存和冲突检测；后续可平滑升级为 Tiptap 顶层 Node。
        </p>
      </div>
      <el-button
        type="primary"
        :icon="Plus"
        :loading="creating"
        :disabled="readonly"
        @click="handleCreate"
      >
        新增段落
      </el-button>
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
      :closable="true"
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
        @click="handleCreate"
      >
        创建第一个段落
      </el-button>
    </el-empty>

    <div v-else class="block-list">
      <ParagraphBlock
        v-for="(block, index) in blocks"
        :key="block.id"
        :block="block"
        :is-first="index === 0"
        :is-last="index === blocks.length - 1"
        :busy="busyBlockId === block.id"
        :readonly="readonly"
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
} from '@/api/block';
import { CollaborationOperationError } from '@/composables/useDocumentCollaboration';
import { isConflictError, readableError } from '@/utils/error';

const props = defineProps<{
  documentId: string;
  focusBlockId?: string | null;
  readonly?: boolean;
  remoteBlock?: DocumentBlock | null;
  saveViaCollaboration?: (
    block: DocumentBlock,
    text: string,
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
    if (!block || block.documentId !== props.documentId) {
      return;
    }
    replaceBlock(block);
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
  target.scrollIntoView({
    behavior: 'smooth',
    block: 'center',
  });

  window.setTimeout(() => {
    if (focusedBlockId.value === props.focusBlockId) {
      focusedBlockId.value = null;
    }
  }, 2200);
}

async function handleCreate() {
  if (props.readonly) {
    return;
  }

  creating.value = true;

  try {
    const block = await createBlock(props.documentId, {
      type: 'PARAGRAPH',
      content: {
        text: '新的段落',
      },
    });
    blocks.value = [...blocks.value, block];
    ElMessage.success('段落已创建');
  } catch (error) {
    ElMessage.error(readableError(error, '段落创建失败'));
  } finally {
    creating.value = false;
  }
}

async function handleSave(block: DocumentBlock, text: string) {
  if (props.readonly) {
    return;
  }

  busyBlockId.value = block.id;
  conflictMessage.value = '';

  try {
    const updated = props.saveViaCollaboration
      ? await props.saveViaCollaboration(block, text)
      : await updateBlock(props.documentId, block.id, {
          content: {
            text,
          },
          expectedVersion: block.version,
        });
    replaceBlock(updated);
  } catch (error) {
    const message = readableError(error, '段落保存失败');
    if (
      isConflictError(error)
      || (
        error instanceof CollaborationOperationError
        && error.status === 'CONFLICT'
      )
    ) {
      conflictMessage.value = '当前段落已被其他操作修改，请刷新内容后再继续编辑。';
    }
    ElMessage.error(message);
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
      '删除后该段落会从当前文档移除，此操作暂不支持撤销。',
      '删除段落',
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
      .map((item, index) => ({
        ...item,
        sortOrder: index,
      }));
    ElMessage.success('段落已删除');
  } catch (error) {
    ElMessage.error(readableError(error, '段落删除失败'));
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
    ElMessage.error(readableError(error, '段落排序失败'));
  } finally {
    busyBlockId.value = null;
  }
}

function replaceBlock(block: DocumentBlock) {
  blocks.value = blocks.value.map((item) => (
    item.id === block.id ? block : item
  ));
}
</script>
