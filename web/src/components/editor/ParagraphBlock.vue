<template>
  <article class="paragraph-block" :class="{ 'is-dirty': dirty }">
    <div class="block-toolbar">
      <div class="block-identity">
        <span class="block-index">#{{ block.sortOrder + 1 }}</span>
        <span class="block-version">版本 {{ block.version }}</span>
        <el-tag size="small" effect="plain">{{ block.type }}</el-tag>
      </div>

      <div v-if="editingUsers.length > 0" class="block-editing-users">
        <span
          v-for="user in editingUsers"
          :key="`${user.userId}-${user.startedAt}`"
          class="block-editing-pill"
        >
          <span class="block-editing-avatar">
            {{ user.username.slice(0, 1).toUpperCase() }}
          </span>
          {{ user.username }} 正在编辑
        </span>
      </div>

      <div class="block-actions">
        <el-tooltip content="上移">
          <el-button
            :icon="ArrowUp"
            circle
            size="small"
            :disabled="isFirst || busy || readonly"
            @click="emit('move-up', block)"
          />
        </el-tooltip>
        <el-tooltip content="下移">
          <el-button
            :icon="ArrowDown"
            circle
            size="small"
            :disabled="isLast || busy || readonly"
            @click="emit('move-down', block)"
          />
        </el-tooltip>
        <el-tooltip content="删除">
          <el-button
            :icon="Delete"
            circle
            size="small"
            type="danger"
            :disabled="busy || readonly"
            @click="emit('delete', block)"
          />
        </el-tooltip>
      </div>
    </div>

    <el-input
      v-model="draft"
      type="textarea"
      :autosize="{ minRows: 3, maxRows: 14 }"
      placeholder="输入段落内容，离开输入框后自动保存"
      :disabled="busy || readonly"
      @focus="handleFocus"
      @blur="handleBlur"
    />

    <div class="block-footer">
      <span :class="statusClass">
        {{ statusText }}
      </span>
      <el-button
        text
        type="primary"
        :loading="busy"
        :disabled="!dirty || readonly"
        @click="save"
      >
        保存
      </el-button>
    </div>
  </article>
</template>

<script setup lang="ts">
import { ArrowDown, ArrowUp, Delete } from '@element-plus/icons-vue';
import { computed, ref, watch } from 'vue';

import type { DocumentBlock } from '@/api/block';
import type { EditingState } from '@/composables/useDocumentCollaboration';

const props = withDefaults(defineProps<{
  block: DocumentBlock;
  isFirst: boolean;
  isLast: boolean;
  busy?: boolean;
  readonly?: boolean;
  editingUsers?: EditingState[];
}>(), {
  editingUsers: () => [],
});

const emit = defineEmits<{
  save: [block: DocumentBlock, text: string];
  delete: [block: DocumentBlock];
  'move-up': [block: DocumentBlock];
  'move-down': [block: DocumentBlock];
  'editing-start': [block: DocumentBlock];
  'editing-stop': [block: DocumentBlock];
}>();

const draft = ref(props.block.content.text);

const dirty = computed(() => draft.value !== props.block.content.text);
const statusText = computed(() => {
  if (props.readonly) {
    return '只读';
  }
  if (props.busy) {
    return '保存中...';
  }
  return dirty.value ? '编辑中，离开输入框后保存' : '已保存';
});
const statusClass = computed(() => (dirty.value ? 'text-warning' : 'text-muted'));

watch(
  () => props.block.content.text,
  (text) => {
    draft.value = text;
  },
);

function handleFocus() {
  if (props.busy || props.readonly) {
    return;
  }
  emit('editing-start', props.block);
}

function handleBlur() {
  save();
  if (props.readonly) {
    return;
  }
  emit('editing-stop', props.block);
}

function save() {
  if (!dirty.value || props.busy || props.readonly) {
    return;
  }

  emit('save', props.block, draft.value);
}
</script>
