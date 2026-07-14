<template>
  <section class="version-history-panel">
    <div class="panel-title-row">
      <div>
        <p class="eyebrow">Versions</p>
        <h3>版本历史</h3>
      </div>
      <el-tag size="small" effect="plain">{{ versions.length }} 个版本</el-tag>
    </div>

    <el-skeleton v-if="loading" :rows="4" animated />
    <el-empty v-else-if="versions.length === 0" description="暂无发布版本" />

    <div v-else class="version-list">
      <button
        v-for="version in versions"
        :key="version.id"
        class="version-item version-history-button"
        type="button"
        @click="selectVersion(version)"
      >
        <div>
          <strong>v{{ version.versionNo }} · {{ version.title }}</strong>
          <p>发布人：{{ shortId(version.publishedBy) }}</p>
          <p>{{ formatTime(version.publishedAt) }}</p>
        </div>
        <el-tag
          size="small"
          :type="version.status === 'CURRENT' ? 'success' : 'info'"
          effect="light"
        >
          {{ version.status === 'CURRENT' ? '当前' : '已替代' }}
        </el-tag>
      </button>
    </div>

    <el-dialog
      v-model="detailVisible"
      title="版本快照"
      width="720px"
      destroy-on-close
    >
      <el-skeleton v-if="detailLoading" :rows="5" animated />
      <div v-else-if="selectedVersion" class="version-detail">
        <div class="version-detail-header">
          <div>
            <p class="eyebrow">Snapshot</p>
            <h3>v{{ selectedVersion.versionNo }} · {{ selectedVersion.title }}</h3>
          </div>
          <el-tag effect="light">{{ formatTime(selectedVersion.publishedAt) }}</el-tag>
        </div>

        <div v-if="snapshotBlocks.length > 0" class="snapshot-block-list">
          <article
            v-for="block in snapshotBlocks"
            :key="block.id"
            class="snapshot-block-item"
          >
            <el-tag size="small" effect="plain">{{ block.type }}</el-tag>
            <p>{{ block.text }}</p>
          </article>
        </div>
        <el-empty v-else description="该版本没有内容 Block" />
      </div>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';

import {
  getDocumentVersion,
  type DocumentVersion,
} from '@/api/document';
import { readableError } from '@/utils/error';

const props = defineProps<{
  documentId: string;
  versions: DocumentVersion[];
  loading?: boolean;
}>();

const detailVisible = ref(false);
const detailLoading = ref(false);
const selectedVersion = ref<DocumentVersion | null>(null);

const snapshotBlocks = computed(() => {
  if (!selectedVersion.value) {
    return [];
  }

  try {
    const snapshot = JSON.parse(selectedVersion.value.snapshotPayload) as {
      blocks?: Array<{
        id: string;
        type: string;
        text: string;
      }>;
    };
    return Array.isArray(snapshot.blocks) ? snapshot.blocks : [];
  } catch {
    return [];
  }
});

async function selectVersion(version: DocumentVersion) {
  detailVisible.value = true;
  detailLoading.value = true;
  selectedVersion.value = null;

  try {
    selectedVersion.value = await getDocumentVersion(props.documentId, version.id);
  } catch (error) {
    ElMessage.error(readableError(error, '版本快照加载失败'));
    detailVisible.value = false;
  } finally {
    detailLoading.value = false;
  }
}

function shortId(value: string) {
  return value.slice(0, 8);
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
}
</script>
