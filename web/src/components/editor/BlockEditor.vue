<template>
  <section class="block-editor">
    <header class="block-editor-header">
      <div>
        <p class="eyebrow">Blocks</p>
        <h3>内容块</h3>
        <p class="section-hint">
          当前 MVP 支持段落块的新增、编辑、删除和排序；保存时会携带版本号做并发校验。
        </p>
      </div>
      <el-button
        type="primary"
        :icon="Plus"
        :loading="creating"
        @click="handleCreate"
      >
        新增段落
      </el-button>
    </header>

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
      <el-button type="primary" :icon="Plus" @click="handleCreate">
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
        @save="handleSave"
        @delete="handleDelete"
        @move-up="handleMove(block, index - 1)"
        @move-down="handleMove(block, index + 1)"
      />
    </div>
  </section>
</template>

<script setup lang="ts">
import { Plus } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { onMounted, ref, watch } from 'vue';

import {
  createBlock,
  deleteBlock,
  listBlocks,
  moveBlock,
  updateBlock,
  type DocumentBlock,
} from '@/api/block';
import { isConflictError, readableError } from '@/utils/error';

const props = defineProps<{
  documentId: string;
}>();

const blocks = ref<DocumentBlock[]>([]);
const loading = ref(false);
const creating = ref(false);
const busyBlockId = ref<string | null>(null);
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

async function loadBlocks() {
  loading.value = true;
  conflictMessage.value = '';
  errorMessage.value = '';

  try {
    blocks.value = await listBlocks(props.documentId);
  } catch (error) {
    errorMessage.value = readableError(error, '内容块加载失败');
  } finally {
    loading.value = false;
  }
}

async function handleCreate() {
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
  busyBlockId.value = block.id;
  conflictMessage.value = '';

  try {
    const updated = await updateBlock(props.documentId, block.id, {
      content: {
        text,
      },
      expectedVersion: block.version,
    });
    replaceBlock(updated);
    ElMessage.success('已保存');
  } catch (error) {
    const message = readableError(error, '段落保存失败');
    if (isConflictError(error)) {
      conflictMessage.value = '当前段落已被其他操作修改，请刷新内容后再继续编辑。';
    }
    ElMessage.error(message);
  } finally {
    busyBlockId.value = null;
  }
}

async function handleDelete(block: DocumentBlock) {
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
  if (targetIndex < 0 || targetIndex >= blocks.value.length) {
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
