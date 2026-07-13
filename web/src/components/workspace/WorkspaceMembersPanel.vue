<template>
  <section class="members-panel">
    <div class="members-panel-header">
      <div>
        <h3>成员管理</h3>
        <p class="section-hint">
          普通成员可以查看成员列表；管理员可以邀请成员、调整角色和移除成员。
        </p>
      </div>
      <el-button
        v-if="canManageMembers"
        type="primary"
        :icon="Plus"
        @click="inviteDialogVisible = true"
      >
        邀请成员
      </el-button>
    </div>

    <el-alert
      v-if="errorMessage"
      class="workspace-alert"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
    >
      <template #default>
        <el-button text type="primary" @click="loadMembers">重新加载</el-button>
      </template>
    </el-alert>

    <el-skeleton v-if="loading" :rows="4" animated />

    <el-empty
      v-else-if="members.length === 0"
      description="暂无成员"
    />

    <el-table v-else :data="members" class="members-table">
      <el-table-column label="成员">
        <template #default="{ row }: { row: WorkspaceMember }">
          <div class="member-identity">
            <span class="member-avatar">{{ avatarText(row) }}</span>
            <div>
              <strong>{{ row.displayName || row.username }}</strong>
              <p>@{{ row.username }}</p>
            </div>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="角色" width="190">
        <template #default="{ row }: { row: WorkspaceMember }">
          <el-select
            v-if="canManageMembers"
            :model-value="row.role"
            size="small"
            :disabled="busyUserId === row.userId"
            @change="(role: WorkspaceRole) => handleRoleChange(row, role)"
          >
            <el-option label="管理员" value="ADMIN" />
            <el-option label="普通用户" value="MEMBER" />
          </el-select>
          <el-tag v-else effect="light">
            {{ roleText(row.role) }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="加入时间" width="190">
        <template #default="{ row }: { row: WorkspaceMember }">
          {{ formatTime(row.joinedAt) }}
        </template>
      </el-table-column>

      <el-table-column
        v-if="canManageMembers"
        label="操作"
        width="110"
        align="right"
      >
        <template #default="{ row }: { row: WorkspaceMember }">
          <el-button
            text
            type="danger"
            :loading="busyUserId === row.userId"
            @click="handleRemove(row)"
          >
            移除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="inviteDialogVisible"
      title="邀请成员"
      width="420px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @keyup.enter="handleInvite"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model.trim="form.username"
            maxlength="32"
            placeholder="输入已注册用户的用户名"
          />
        </el-form-item>

        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" class="full-width">
            <el-option label="普通用户" value="MEMBER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="inviteDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="inviting"
          @click="handleInvite"
        >
          邀请
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { Plus } from '@element-plus/icons-vue';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, reactive, ref, watch } from 'vue';

import {
  inviteWorkspaceMember,
  listWorkspaceMembers,
  removeWorkspaceMember,
  updateWorkspaceMemberRole,
  type WorkspaceMember,
} from '@/api/member';
import type { WorkspaceRole } from '@/api/workspace';
import { readableError } from '@/utils/error';

const props = defineProps<{
  workspaceId: string;
  currentUserRole: WorkspaceRole;
}>();

const members = ref<WorkspaceMember[]>([]);
const loading = ref(false);
const inviting = ref(false);
const busyUserId = ref<string | null>(null);
const errorMessage = ref('');
const inviteDialogVisible = ref(false);
const formRef = ref<FormInstance>();

const form = reactive({
  username: '',
  role: 'MEMBER' as WorkspaceRole,
});

const rules: FormRules<typeof form> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 32, message: '用户名长度必须在 4 到 32 个字符之间', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9_]+$/,
      message: '用户名只能包含字母、数字和下划线',
      trigger: 'blur',
    },
  ],
  role: [
    { required: true, message: '请选择成员角色', trigger: 'change' },
  ],
};

const canManageMembers = computed(() => props.currentUserRole === 'ADMIN');

onMounted(() => {
  void loadMembers();
});

watch(
  () => props.workspaceId,
  () => {
    void loadMembers();
  },
);

watch(inviteDialogVisible, (visible) => {
  if (!visible) {
    form.username = '';
    form.role = 'MEMBER';
    formRef.value?.clearValidate();
  }
});

async function loadMembers() {
  loading.value = true;
  errorMessage.value = '';

  try {
    members.value = await listWorkspaceMembers(props.workspaceId);
  } catch (error) {
    errorMessage.value = readableError(error, '成员列表加载失败');
  } finally {
    loading.value = false;
  }
}

async function handleInvite() {
  if (inviting.value) {
    return;
  }

  const valid = await formRef.value?.validate();
  if (!valid) {
    return;
  }

  inviting.value = true;
  try {
    const member = await inviteWorkspaceMember(props.workspaceId, {
      username: form.username,
      role: form.role,
    });
    members.value = sortMembers([...members.value, member]);
    inviteDialogVisible.value = false;
    ElMessage.success('成员邀请成功');
  } catch (error) {
    ElMessage.error(readableError(error, '成员邀请失败'));
  } finally {
    inviting.value = false;
  }
}

async function handleRoleChange(
  member: WorkspaceMember,
  role: WorkspaceRole,
) {
  if (member.role === role) {
    return;
  }

  busyUserId.value = member.userId;
  try {
    const updated = await updateWorkspaceMemberRole(
      props.workspaceId,
      member.userId,
      { role },
    );
    members.value = sortMembers(members.value.map((item) => (
      item.userId === updated.userId ? updated : item
    )));
    ElMessage.success('成员角色已更新');
  } catch (error) {
    ElMessage.error(readableError(error, '成员角色更新失败'));
  } finally {
    busyUserId.value = null;
  }
}

async function handleRemove(member: WorkspaceMember) {
  try {
    await ElMessageBox.confirm(
      `确定要将 ${member.displayName || member.username} 移出当前工作区吗？`,
      '移除成员',
      {
        confirmButtonText: '移除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    );
  } catch {
    return;
  }

  busyUserId.value = member.userId;
  try {
    await removeWorkspaceMember(props.workspaceId, member.userId);
    members.value = members.value.filter(
      (item) => item.userId !== member.userId,
    );
    ElMessage.success('成员已移除');
  } catch (error) {
    ElMessage.error(readableError(error, '成员移除失败'));
  } finally {
    busyUserId.value = null;
  }
}

function sortMembers(value: WorkspaceMember[]) {
  return [...value].sort((left, right) => {
    if (left.role !== right.role) {
      return left.role === 'ADMIN' ? -1 : 1;
    }
    return left.username.localeCompare(right.username);
  });
}

function roleText(role: WorkspaceRole) {
  return role === 'ADMIN' ? '管理员' : '普通用户';
}

function avatarText(member: WorkspaceMember) {
  return (member.displayName || member.username).slice(0, 1).toUpperCase();
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
}
</script>
