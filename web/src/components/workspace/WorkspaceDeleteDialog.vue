<template>
  <el-dialog
    v-model="visible"
    title="删除工作区"
    width="470px"
    :teleported="false"
    :close-on-click-modal="!loading"
    :close-on-press-escape="!loading"
    :show-close="!loading"
    @closed="reset"
  >
    <p class="workspace-delete-description">
      你将删除工作区 <strong>{{ workspaceName }}</strong>。删除后，工作区、
      关联文档、仓库索引和本地克隆目录都不再可用。
    </p>

    <el-alert
      title="这是不可逆操作。请输入工作区名称进行确认。"
      type="error"
      :closable="false"
      show-icon
    />

    <label class="workspace-delete-label" for="workspace-delete-confirmation">
      输入工作区名称
    </label>
    <el-input
      id="workspace-delete-confirmation"
      v-model="confirmation"
      :disabled="loading"
      placeholder="请输入完整名称"
      autocomplete="off"
      @keyup.enter="submit"
    />

    <template #footer>
      <el-button :disabled="loading" @click="visible = false">
        取消
      </el-button>
      <el-button
        type="danger"
        :disabled="!canDelete"
        :loading="loading"
        @click="submit"
      >
        确认删除
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';

const props = defineProps<{
  workspaceName: string;
  loading: boolean;
}>();

const visible = defineModel<boolean>({ required: true });
const emit = defineEmits<{
  confirm: [];
}>();

const confirmation = ref('');
const canDelete = computed(
  () => !props.loading
    && confirmation.value === props.workspaceName,
);

watch(visible, (isVisible) => {
  if (!isVisible) {
    reset();
  }
});

function submit() {
  if (!canDelete.value) {
    return;
  }
  emit('confirm');
}

function reset() {
  confirmation.value = '';
}
</script>

<style scoped>
.workspace-delete-description {
  margin: 0 0 16px;
  color: #6f7d91;
  font-size: 14px;
  line-height: 1.65;
}

.workspace-delete-label {
  display: block;
  margin: 16px 0 7px;
  color: #344159;
  font-size: 13px;
}
</style>
