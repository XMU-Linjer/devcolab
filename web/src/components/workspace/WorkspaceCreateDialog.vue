<template>
  <el-dialog
    v-model="visible"
    title="创建工作区"
    width="420px"
    destroy-on-close
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      @keyup.enter="submit"
    >
      <el-form-item label="工作区名称" prop="name">
        <el-input
          v-model.trim="form.name"
          maxlength="100"
          placeholder="例如：实验室知识库"
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
import { reactive, ref } from 'vue';

interface WorkspaceForm {
  name: string;
}

const visible = defineModel<boolean>({ required: true });
const emit = defineEmits<{
  create: [name: string];
}>();

const formRef = ref<FormInstance>();
const submitting = ref(false);

const form = reactive<WorkspaceForm>({
  name: '',
});

const rules: FormRules<WorkspaceForm> = {
  name: [
    { required: true, message: '请输入工作区名称', trigger: 'blur' },
    { max: 100, message: '工作区名称不能超过 100 个字符', trigger: 'blur' },
  ],
};

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
    emit('create', form.name);
    form.name = '';
  } finally {
    submitting.value = false;
  }
}
</script>

