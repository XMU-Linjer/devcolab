<template>
  <main class="auth-page">
    <section class="auth-panel">
      <div class="auth-brand">
        <span class="brand-mark">D</span>
        <span>DevCollab</span>
      </div>

      <div class="auth-heading">
        <p class="eyebrow">Knowledge Core</p>
        <h1>登录工作台</h1>
        <p>进入你的协作文档空间，继续整理需求、接口和工程知识。</p>
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
            placeholder="请输入用户名"
            size="large"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            autocomplete="current-password"
            placeholder="请输入密码"
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
          登录
        </el-button>
      </el-form>

      <p class="auth-switch">
        还没有账号？
        <RouterLink to="/register">去注册</RouterLink>
      </p>
    </section>
  </main>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage } from 'element-plus';
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useAuthStore } from '@/stores/auth';
import { readableError } from '@/utils/error';

interface LoginForm {
  username: string;
  password: string;
}

const authStore = useAuthStore();
const route = useRoute();
const router = useRouter();
const formRef = ref<FormInstance>();
const submitting = ref(false);

const form = reactive<LoginForm>({
  username: '',
  password: '',
});

const rules: FormRules<LoginForm> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { max: 32, message: '用户名不能超过 32 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { max: 72, message: '密码不能超过 72 个字符', trigger: 'blur' },
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
    await authStore.login(form);
    ElMessage.success('登录成功');
    const redirect = typeof route.query.redirect === 'string'
      ? route.query.redirect
      : '/';
    await router.push(redirect);
  } catch (error) {
    ElMessage.error(readableError(error, '登录失败，请检查用户名和密码'));
  } finally {
    submitting.value = false;
  }
}
</script>
