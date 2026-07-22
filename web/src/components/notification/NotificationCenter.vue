<template>
  <el-popover
    v-model:visible="visible"
    placement="bottom-end"
    width="380"
    trigger="click"
    @show="loadNotifications"
  >
    <template #reference>
      <el-badge
        :value="unreadCount"
        :hidden="unreadCount === 0"
        :max="99"
        class="notification-badge"
      >
        <el-button :icon="Bell" circle aria-label="通知" />
      </el-badge>
    </template>

    <section class="notification-panel">
      <header class="notification-header">
        <div>
          <h3>通知中心</h3>
          <p>评审、发布和协作事件会在这里提醒你。</p>
        </div>
        <el-button
          text
          type="primary"
          :loading="loading"
          @click="loadNotifications"
        >
          刷新
        </el-button>
      </header>

      <el-skeleton v-if="loading" :rows="4" animated />

      <el-alert
        v-else-if="errorMessage"
        :title="errorMessage"
        type="error"
        show-icon
        :closable="false"
      />

      <el-empty
        v-else-if="notifications.length === 0"
        description="暂无未读通知"
      />

      <div v-else class="notification-list">
        <article
          v-for="item in notifications"
          :key="item.id"
          class="notification-item"
          :class="{ 'is-unread': item.unread }"
        >
          <div class="notification-item-main" @click="openNotification(item)">
            <div class="notification-item-title">
              <el-tag size="small" effect="light">
                {{ typeText(item.type) }}
              </el-tag>
              <strong>{{ item.title }}</strong>
            </div>
            <p v-if="item.content">{{ item.content }}</p>
            <span class="notification-item-time">{{ formatTime(item.createdAt) }}</span>
          </div>

          <el-button
            v-if="item.unread"
            text
            type="primary"
            :loading="readingId === item.id"
            @click.stop="handleMarkRead(item)"
          >
            已读
          </el-button>
        </article>
      </div>
    </section>
  </el-popover>
</template>

<script setup lang="ts">
import { Bell } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import {
  listNotifications,
  markNotificationRead,
  type NotificationItem,
} from '@/api/notification';
import { readableError } from '@/utils/error';

const router = useRouter();

const visible = ref(false);
const loading = ref(false);
const errorMessage = ref('');
const notifications = ref<NotificationItem[]>([]);
const readingId = ref<string | null>(null);
let timer: number | undefined;

const unreadCount = computed(
  () => notifications.value.filter((item) => item.unread).length,
);

onMounted(() => {
  void loadNotifications();
  timer = window.setInterval(() => {
    void loadNotifications(false);
  }, 30_000);
});

onBeforeUnmount(() => {
  if (timer) {
    window.clearInterval(timer);
  }
});

async function loadNotifications(showLoading = true) {
  if (showLoading) {
    loading.value = true;
  }
  errorMessage.value = '';
  try {
    notifications.value = await listNotifications({
      unreadOnly: true,
      limit: 20,
    });
  } catch (error) {
    errorMessage.value = readableError(error, '通知加载失败');
  } finally {
    loading.value = false;
  }
}

async function handleMarkRead(item: NotificationItem) {
  readingId.value = item.id;
  try {
    const updated = await markNotificationRead(item.id);
    notifications.value = notifications.value.filter(
      (notification) => notification.id !== updated.id,
    );
  } catch (error) {
    ElMessage.error(readableError(error, '标记已读失败'));
  } finally {
    readingId.value = null;
  }
}

async function openNotification(item: NotificationItem) {
  if (item.unread) {
    await handleMarkRead(item);
  }
  visible.value = false;

  await router.push({
    name: 'workspace-code',
    params: {
      workspaceId: item.workspaceId,
    },
    query: item.documentId ? { documentId: item.documentId } : undefined,
  });
}

function typeText(type: string) {
  const mapping: Record<string, string> = {
    DOCUMENT_REVIEW_SUBMITTED: '待评审',
    DOCUMENT_REVIEW_APPROVED: '已发布',
    DOCUMENT_REVIEW_REJECTED: '已驳回',
  };
  return mapping[type] ?? '通知';
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}
</script>
