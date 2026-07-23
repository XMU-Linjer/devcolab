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

      <el-form-item label="GitHub 仓库地址" prop="repositoryUrl">
        <el-input
          v-model.trim="form.repositoryUrl"
          placeholder="例如：https://github.com/octocat/Hello-World.git"
        />
      </el-form-item>

      <el-form-item label="分支名称" prop="branch">
        <el-input
          v-model.trim="form.branch"
          maxlength="200"
          placeholder="例如：main"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">
        取消
      </el-button>

      <el-button
        type="primary"
        :loading="submitting"
        @click="submit"
      >
        创建并导入
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus';
import { reactive, ref, watch } from 'vue';

interface WorkspaceForm {
  name: string;
  repositoryUrl: string;
  branch: string;
}

const visible = defineModel<boolean>({
  required: true,
});

const emit = defineEmits<{
  create: [payload: WorkspaceForm];
}>();

const formRef = ref<FormInstance>();
const submitting = ref(false);

const form = reactive<WorkspaceForm>({
  name: '',
  repositoryUrl: '',
  branch: 'main',
});

const rules: FormRules<WorkspaceForm> = {
  name: [
    {
      required: true,
      message: '请输入工作区名称',
      trigger: 'blur',
    },
    {
      max: 100,
      message: '工作区名称不能超过 100 个字符',
      trigger: 'blur',
    },
  ],

  repositoryUrl: [
    {
      required: true,
      message: '请输入 GitHub 仓库地址',
      trigger: 'blur',
    },
  ],

  branch: [
    {
      required: true,
      message: '请输入分支名称',
      trigger: 'blur',
    },
    {
      max: 200,
      message: '分支名称不能超过 200 个字符',
      trigger: 'blur',
    },
  ],
};

watch(visible, (isVisible) => {
  if (!isVisible) {
    form.name = '';
    form.repositoryUrl = '';
    form.branch = 'main';
    formRef.value?.clearValidate();
  }
});

async function submit() {
  if (submitting.value) {
    return;
  }

  let valid = false;

  try {
    valid = (await formRef.value?.validate()) ?? false;
  } catch {
    return;
  }

  if (!valid) {
    return;
  }

  submitting.value = true;

  try {
    emit('create', {
      name: form.name,
      repositoryUrl: form.repositoryUrl,
      branch: form.branch,
    });
  } finally {
    submitting.value = false;
  }
}
</script>