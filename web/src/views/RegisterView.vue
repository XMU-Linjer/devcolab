<template>
  <main class="auth-page">
    <section class="auth-panel">
      <div class="auth-brand">
        <span class="brand-mark">D</span>
        <span>DevCollab</span>
      </div>

      <div class="auth-heading">
        <p class="eyebrow">Knowledge Core</p>
        <h1>创建账号</h1>
        <p>注册后会自动进入工作台，后续工作区和文档都归属于当前账号。</p>
      </div>

      <el-form
        ref="formRef"
        class="auth-form"
        :model="form"
        :rules="rules"
        label-position="top"
        @keyup.enter="submit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model.trim="form.username"
            autocomplete="username"
            placeholder="4-32 位字母、数字或下划线"
            size="large"
          />
        </el-form-item>

        <el-form-item label="显示名称" prop="displayName">
          <el-input
            v-model.trim="form.displayName"
            autocomplete="name"
            placeholder="请输入显示名称"
            size="large"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            autocomplete="new-password"
            placeholder="至少 8 位"
            show-password
            size="large"
            type="password"
          />
        </el-form-item>

        <el-button
          class="auth-submit"
          type="primary"
          size="large"
          :loading="submitting"
          @click="submit"
        >
          注册并进入
        </el-button>
      </el-form>

      <p class="auth-switch">
        已经有账号？
        <RouterLink to="/login">去登录</RouterLink>
      </p>
    </section>
  </main>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage } from 'element-plus';
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { useAuthStore } from '@/stores/auth';
import { readableError } from '@/utils/error';

interface RegisterForm {
  username: string;
  displayName: string;
  password: string;
}

const authStore = useAuthStore();
const router = useRouter();
const formRef = ref<FormInstance>();
const submitting = ref(false);

const form = reactive<RegisterForm>({
  username: '',
  displayName: '',
  password: '',
});

const rules: FormRules<RegisterForm> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 32, message: '用户名长度必须在 4 到 32 个字符之间', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9_]+$/,
      message: '用户名只能包含字母、数字和下划线',
      trigger: 'blur',
    },
  ],
  displayName: [
    { required: true, message: '请输入显示名称', trigger: 'blur' },
    { max: 50, message: '显示名称不能超过 50 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 72, message: '密码长度必须在 8 到 72 个字符之间', trigger: 'blur' },
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
    await authStore.register(form);
    ElMessage.success('注册成功');
    await router.push('/');
  } catch (error) {
    ElMessage.error(readableError(error, '注册失败，请稍后再试'));
  } finally {
    submitting.value = false;
  }
}
</script>
