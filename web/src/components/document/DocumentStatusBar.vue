<template>
  <section class="document-status-bar">
    <div class="document-status-main">
      <el-tag :type="statusTagType(document.reviewStatus)" effect="light">
        {{ statusText(document.reviewStatus) }}
      </el-tag>
      <el-tag effect="plain">{{ documentTypeText(document.documentType) }}</el-tag>
      <span class="document-status-version">
        当前版本：{{ currentVersionText }}
      </span>
      <span class="document-status-version">
        最新发布：{{ latestPublishedVersionText }}
      </span>
    </div>

    <div class="document-status-actions">
      <el-button
        v-if="document.reviewStatus === 'DRAFT' || document.reviewStatus === 'REJECTED'"
        type="primary"
        :loading="busyAction === 'submit'"
        @click="emit('submit')"
      >
        提交评审
      </el-button>

      <template v-if="document.reviewStatus === 'IN_REVIEW' && canReview">
        <el-button
          type="success"
          :loading="busyAction === 'approve'"
          @click="emit('approve')"
        >
          通过
        </el-button>
        <el-button
          type="danger"
          plain
          :loading="busyAction === 'reject'"
          @click="emit('reject')"
        >
          驳回
        </el-button>
      </template>

      <el-button
        v-if="document.reviewStatus === 'PUBLISHED'"
        type="warning"
        plain
        :loading="busyAction === 'submit'"
        @click="emit('submit')"
      >
        创建新修订
      </el-button>

      <el-tag v-if="document.reviewStatus === 'SUPERSEDED'" type="info">
        只读历史版本
      </el-tag>
      <el-tag v-if="document.reviewStatus === 'DEPRECATED'" type="danger">
        已废弃，不可编辑
      </el-tag>

      <el-button
        v-if="document.reviewStatus !== 'DEPRECATED'"
        type="danger"
        text
        :loading="busyAction === 'deprecate'"
        @click="emit('deprecate')"
      >
        废弃
      </el-button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';

import type {
  DocumentReviewStatus,
  DocumentSummary,
  DocumentType,
  DocumentVersion,
} from '@/api/document';

const props = defineProps<{
  document: DocumentSummary;
  versions: DocumentVersion[];
  canReview: boolean;
  busyAction?: 'submit' | 'approve' | 'reject' | 'deprecate' | null;
}>();

const emit = defineEmits<{
  submit: [];
  approve: [];
  reject: [];
  deprecate: [];
}>();

const currentVersion = computed(() => (
  props.versions.find((version) => version.status === 'CURRENT')
  ?? props.versions[0]
  ?? null
));

const latestPublishedVersion = computed(() => props.versions[0] ?? null);

const currentVersionText = computed(() => (
  currentVersion.value ? `v${currentVersion.value.versionNo}` : '暂无'
));

const latestPublishedVersionText = computed(() => (
  latestPublishedVersion.value
    ? `v${latestPublishedVersion.value.versionNo}`
    : '暂无'
));

function statusText(status: DocumentReviewStatus) {
  const map: Record<DocumentReviewStatus, string> = {
    DRAFT: '草稿',
    IN_REVIEW: '评审中',
    PUBLISHED: '已发布',
    REJECTED: '已驳回',
    SUPERSEDED: '已被替代',
    DEPRECATED: '已废弃',
  };
  return map[status];
}

function statusTagType(status: DocumentReviewStatus) {
  const map: Record<
    DocumentReviewStatus,
    'primary' | 'success' | 'warning' | 'info' | 'danger'
  > = {
    DRAFT: 'info',
    IN_REVIEW: 'warning',
    PUBLISHED: 'success',
    REJECTED: 'danger',
    SUPERSEDED: 'info',
    DEPRECATED: 'danger',
  };
  return map[status];
}

function documentTypeText(type: DocumentType) {
  const map: Record<DocumentType, string> = {
    REQUIREMENT: '需求',
    API: 'API',
    ARCHITECTURE: '架构',
    DATABASE: '数据库',
    FRONTEND: '前端',
    BACKEND: '后端',
    TEST: '测试',
    DEPLOYMENT: '部署',
    ADR: '架构决策',
  };
  return map[type];
}
</script>
