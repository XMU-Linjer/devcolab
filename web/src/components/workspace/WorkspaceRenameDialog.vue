<template>
  <el-dialog
    v-model="visible"
    title="重命名工作区"
    width="470px"
    :teleported="false"
    :close-on-click-modal="!loading"
    :close-on-press-escape="!loading"
    :show-close="!loading"
    @opened="focusInput"
    @closed="handleClosed"
  >
    <label class="workspace-rename-label" for="workspace-rename-name">
      工作区名称
    </label>
    <el-input
      id="workspace-rename-name"
      ref="nameInput"
      v-model="draftName"
      :disabled="loading"
      maxlength="100"
      show-word-limit
      autofocus
      autocomplete="off"
      @keyup.enter="submit"
    />

    <template #footer>
      <el-button :disabled="loading" @click="visible = false">
        取消
      </el-button>
      <el-button
        type="primary"
        :disabled="!canSave"
        :loading="loading"
        @click="submit"
      >
        保存
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';

const props = defineProps<{
  workspaceName: string;
  loading: boolean;
}>();

const visible = defineModel<boolean>({ required: true });
const emit = defineEmits<{
  confirm: [name: string];
  closed: [];
}>();

const draftName = ref('');
const nameInput = ref<{ focus: () => void } | null>(null);
const normalizedName = computed(() => draftName.value.trim());
const canSave = computed(
  () => !props.loading
    && normalizedName.value.length > 0
    && normalizedName.value !== props.workspaceName,
);

watch(
  visible,
  async (isVisible) => {
    if (isVisible) {
      draftName.value = props.workspaceName;
      await nextTick();
      focusInput();
      return;
    }
    reset();
  },
  { immediate: true },
);

function submit() {
  if (!canSave.value) {
    return;
  }
  emit('confirm', normalizedName.value);
}

function focusInput() {
  nameInput.value?.focus();
}

function reset() {
  draftName.value = '';
}

function handleClosed() {
  reset();
  emit('closed');
}
</script>

<style scoped>
.workspace-rename-label {
  display: block;
  margin: 0 0 7px;
  color: #344159;
  font-size: 13px;
}
</style>
