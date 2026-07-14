<template>
  <section class="review-issue-panel">
    <div class="panel-title-row">
      <div>
        <p class="eyebrow">Review Issues</p>
        <h3>评审问题</h3>
        <p class="section-hint">
          Issue 绑定发布版本，用来记录评审发现的问题和处理结果。
        </p>
      </div>
      <el-button
        type="primary"
        size="small"
        :disabled="!versionId"
        @click="dialogVisible = true"
      >
        创建 Issue
      </el-button>
    </div>

    <el-alert
      v-if="!versionId"
      title="当前文档还没有发布版本，暂不能创建评审 Issue。"
      type="info"
      show-icon
      :closable="false"
    />

    <template v-else>
      <div class="review-issue-filters">
        <el-select v-model="statusFilter" clearable placeholder="状态" size="small">
          <el-option label="OPEN" value="OPEN" />
          <el-option label="RESOLVED" value="RESOLVED" />
          <el-option label="ACCEPTED" value="ACCEPTED" />
          <el-option label="REJECTED" value="REJECTED" />
        </el-select>
        <el-select v-model="typeFilter" clearable placeholder="类型" size="small">
          <el-option
            v-for="option in typeOptions"
            :key="option"
            :label="typeText(option)"
            :value="option"
          />
        </el-select>
        <el-select v-model="severityFilter" clearable placeholder="严重度" size="small">
          <el-option label="LOW" value="LOW" />
          <el-option label="MEDIUM" value="MEDIUM" />
          <el-option label="HIGH" value="HIGH" />
          <el-option label="BLOCKER" value="BLOCKER" />
        </el-select>
      </div>

      <el-skeleton v-if="loading" :rows="4" animated />
      <el-empty
        v-else-if="filteredIssues.length === 0"
        description="暂无符合条件的 Issue"
      />

      <div v-else class="review-issue-list">
        <article
          v-for="issue in filteredIssues"
          :key="issue.id"
          class="review-issue-item"
        >
          <div class="review-issue-header">
            <strong>{{ issue.title }}</strong>
            <el-tag :type="statusTagType(issue.status)" size="small">
              {{ issue.status }}
            </el-tag>
          </div>
          <p v-if="issue.description">{{ issue.description }}</p>
          <div class="review-issue-meta">
            <el-tag size="small" effect="plain">{{ typeText(issue.type) }}</el-tag>
            <el-tag
              size="small"
              :type="severityTagType(issue.severity)"
              effect="light"
            >
              {{ issue.severity }}
            </el-tag>
            <span>{{ formatTime(issue.createdAt) }}</span>
          </div>
          <div v-if="issue.status === 'OPEN'" class="review-issue-actions">
            <el-button
              text
              type="success"
              @click="changeStatus(issue.id, 'RESOLVED')"
            >
              标记解决
            </el-button>
            <el-button text @click="changeStatus(issue.id, 'ACCEPTED')">
              接受
            </el-button>
            <el-button text type="danger" @click="changeStatus(issue.id, 'REJECTED')">
              拒绝
            </el-button>
          </div>
        </article>
      </div>
    </template>

    <el-dialog
      v-model="dialogVisible"
      title="创建 Review Issue"
      width="520px"
      destroy-on-close
    >
      <el-form label-position="top">
        <el-form-item label="类型">
          <el-select v-model="form.type" class="full-width">
            <el-option
              v-for="option in typeOptions"
              :key="option"
              :label="typeText(option)"
              :value="option"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="严重度">
          <el-select v-model="form.severity" class="full-width">
            <el-option label="LOW" value="LOW" />
            <el-option label="MEDIUM" value="MEDIUM" />
            <el-option label="HIGH" value="HIGH" />
            <el-option label="BLOCKER" value="BLOCKER" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="form.title" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 8 }"
            maxlength="5000"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="createIssue">
          创建
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';

import {
  createReviewIssue,
  listReviewIssues,
  updateReviewIssueStatus,
  type ReviewIssue,
  type ReviewIssueSeverity,
  type ReviewIssueStatus,
  type ReviewIssueType,
} from '@/api/reviewIssue';
import { readableError } from '@/utils/error';

const props = defineProps<{
  documentId: string;
  versionId: string | null;
}>();

const typeOptions: ReviewIssueType[] = [
  'REQUIREMENT_GAP',
  'API_CONTRACT',
  'SECURITY',
  'PERFORMANCE',
  'CONSISTENCY',
  'STYLE',
  'OTHER',
];

const issues = ref<ReviewIssue[]>([]);
const loading = ref(false);
const creating = ref(false);
const dialogVisible = ref(false);
const statusFilter = ref<ReviewIssueStatus | ''>('');
const typeFilter = ref<ReviewIssueType | ''>('');
const severityFilter = ref<ReviewIssueSeverity | ''>('');

const form = reactive<{
  type: ReviewIssueType;
  severity: ReviewIssueSeverity;
  title: string;
  description: string;
}>({
  type: 'REQUIREMENT_GAP',
  severity: 'MEDIUM',
  title: '',
  description: '',
});

const filteredIssues = computed(() => issues.value.filter((issue) => {
  if (statusFilter.value && issue.status !== statusFilter.value) {
    return false;
  }
  if (typeFilter.value && issue.type !== typeFilter.value) {
    return false;
  }
  if (severityFilter.value && issue.severity !== severityFilter.value) {
    return false;
  }
  return true;
}));

watch(
  () => [props.documentId, props.versionId],
  () => {
    void loadIssues();
  },
  { immediate: true },
);

async function loadIssues() {
  if (!props.versionId) {
    issues.value = [];
    return;
  }

  loading.value = true;
  try {
    issues.value = await listReviewIssues(props.documentId, props.versionId);
  } catch (error) {
    ElMessage.error(readableError(error, 'Review Issue 加载失败'));
  } finally {
    loading.value = false;
  }
}

async function createIssue() {
  if (!props.versionId) {
    return;
  }

  const title = form.title.trim();
  if (!title) {
    ElMessage.warning('请输入 Issue 标题');
    return;
  }

  creating.value = true;
  try {
    const issue = await createReviewIssue(props.documentId, props.versionId, {
      type: form.type,
      severity: form.severity,
      title,
      description: form.description.trim() || null,
    });
    issues.value = [issue, ...issues.value];
    dialogVisible.value = false;
    form.title = '';
    form.description = '';
    ElMessage.success('Review Issue 已创建');
  } catch (error) {
    ElMessage.error(readableError(error, 'Review Issue 创建失败'));
  } finally {
    creating.value = false;
  }
}

async function changeStatus(issueId: string, status: ReviewIssueStatus) {
  try {
    const updated = await updateReviewIssueStatus(props.documentId, issueId, status);
    issues.value = issues.value.map((issue) => (
      issue.id === updated.id ? updated : issue
    ));
    ElMessage.success('Issue 状态已更新');
  } catch (error) {
    ElMessage.error(readableError(error, 'Issue 状态更新失败'));
  }
}

function typeText(type: ReviewIssueType) {
  const map: Record<ReviewIssueType, string> = {
    REQUIREMENT_GAP: '需求缺口',
    API_CONTRACT: 'API 契约',
    SECURITY: '安全',
    PERFORMANCE: '性能',
    CONSISTENCY: '一致性',
    STYLE: '样式/格式',
    OTHER: '其他',
  };
  return map[type];
}

function statusTagType(status: ReviewIssueStatus) {
  const map: Record<
    ReviewIssueStatus,
    'primary' | 'success' | 'warning' | 'info' | 'danger'
  > = {
    OPEN: 'warning',
    RESOLVED: 'success',
    ACCEPTED: 'primary',
    REJECTED: 'danger',
  };
  return map[status];
}

function severityTagType(severity: ReviewIssueSeverity) {
  const map: Record<
    ReviewIssueSeverity,
    'primary' | 'success' | 'warning' | 'info' | 'danger'
  > = {
    LOW: 'info',
    MEDIUM: 'primary',
    HIGH: 'warning',
    BLOCKER: 'danger',
  };
  return map[severity];
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
}
</script>
