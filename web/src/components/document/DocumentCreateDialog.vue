<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="420px"
    destroy-on-close
  >
    <el-alert
      v-if="parentDocumentTitle"
      class="dialog-alert"
      :title="`将创建在「${parentDocumentTitle}」下`"
      type="info"
      show-icon
      :closable="false"
    />

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      @keyup.enter="submit"
    >
      <el-form-item label="文档标题" prop="title">
        <el-input
          v-model.trim="form.title"
          maxlength="200"
          placeholder="例如：登录会话设计"
          show-word-limit
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">
        创建
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus';
import { computed, reactive, ref, watch } from 'vue';

interface DocumentForm {
  title: string;
}

defineProps<{
  parentDocumentTitle?: string;
}>();

const visible = defineModel<boolean>({ required: true });
const emit = defineEmits<{
  create: [title: string];
}>();

const formRef = ref<FormInstance>();
const submitting = ref(false);

const form = reactive<DocumentForm>({
  title: '',
});

const rules: FormRules<DocumentForm> = {
  title: [
    { required: true, message: '请输入文档标题', trigger: 'blur' },
    { max: 200, message: '文档标题不能超过 200 个字符', trigger: 'blur' },
  ],
};

const dialogTitle = computed(() => '创建文档');

watch(visible, (isVisible) => {
  if (!isVisible) {
    form.title = '';
    formRef.value?.clearValidate();
  }
});

async function submit() {
  if (submitting.value) {
    return;
  }

  const valid = await formRef.value?.validate();
  if (!valid) {
    return;
  }

  submitting.value = true;
  try {
    emit('create', form.title);
  } finally {
    submitting.value = false;
  }
}
</script>
