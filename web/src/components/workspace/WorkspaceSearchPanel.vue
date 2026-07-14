<template>
  <section class="workspace-search">
    <div class="workspace-search-header">
      <div>
        <h2>工作区搜索</h2>
        <p class="section-hint">
          当前先从 PostgreSQL 搜索文档标题和 Block 正文，后续可平滑升级到 Elasticsearch。
        </p>
      </div>
      <el-tag effect="light">MVP</el-tag>
    </div>

    <el-input
      v-model="keyword"
      class="workspace-search-input"
      clearable
      placeholder="搜索文档标题或正文内容"
      @keyup.enter="handleSearch"
      @clear="clearResults"
    >
      <template #append>
        <el-button :icon="Search" :loading="searching" @click="handleSearch">
          搜索
        </el-button>
      </template>
    </el-input>

    <el-radio-group
      v-model="scope"
      class="workspace-search-scope"
      size="small"
      @change="handleScopeChange"
    >
      <el-radio-button value="ALL">全部</el-radio-button>
      <el-radio-button value="TITLE">只搜标题</el-radio-button>
      <el-radio-button value="CONTENT">只搜正文</el-radio-button>
    </el-radio-group>

    <el-alert
      v-if="errorMessage"
      class="workspace-search-alert"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
    />

    <div v-if="searched" class="workspace-search-results">
      <el-empty
        v-if="!searching && results.length === 0"
        description="没有匹配的文档或正文"
      />

      <button
        v-for="result in results"
        :key="`${result.type}-${result.documentId}-${result.blockId || 'title'}`"
        class="workspace-search-result"
        type="button"
        @click="openSearchResult(result)"
      >
        <div class="workspace-search-result-main">
          <span class="workspace-search-title">{{ result.documentTitle }}</span>
          <span class="workspace-search-snippet">
            <template
              v-for="segment in highlightedSegments(result)"
              :key="`${result.type}-${result.documentId}-${result.blockId || 'title'}-${segment.start}`"
            >
              <mark v-if="segment.highlighted">{{ segment.text }}</mark>
              <span v-else>{{ segment.text }}</span>
            </template>
          </span>
        </div>
        <div class="workspace-search-result-meta">
          <el-tag size="small" effect="plain">
            {{ typeText(result.type) }}
          </el-tag>
          <span>{{ formatTime(result.updatedAt) }}</span>
        </div>
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { Search } from '@element-plus/icons-vue';
import { ref } from 'vue';

import {
  searchWorkspace,
  type SearchHit,
  type SearchHitType,
  type SearchScope,
} from '@/api/search';
import { readableError } from '@/utils/error';

const props = defineProps<{
  workspaceId: string;
}>();

const emit = defineEmits<{
  (event: 'open-document', documentId: string, blockId: string | null): void;
}>();

const keyword = ref('');
const scope = ref<SearchScope>('ALL');
const results = ref<SearchHit[]>([]);
const searching = ref(false);
const searched = ref(false);
const errorMessage = ref('');

async function handleSearch() {
  const trimmedKeyword = keyword.value.trim();
  if (!trimmedKeyword) {
    clearResults();
    return;
  }

  searching.value = true;
  searched.value = true;
  errorMessage.value = '';

  try {
    results.value = await searchWorkspace(
      props.workspaceId,
      trimmedKeyword,
      scope.value,
    );
  } catch (error) {
    errorMessage.value = readableError(error, '搜索失败，请稍后重试');
  } finally {
    searching.value = false;
  }
}

function handleScopeChange() {
  if (searched.value && keyword.value.trim()) {
    void handleSearch();
  }
}

function clearResults() {
  results.value = [];
  searched.value = false;
  errorMessage.value = '';
}

function typeText(type: SearchHitType) {
  return type === 'DOCUMENT_TITLE' ? '标题命中' : '正文命中';
}

function openSearchResult(result: SearchHit) {
  emit('open-document', result.documentId, result.blockId);
}

function highlightedSegments(result: SearchHit) {
  const ranges = result.highlights ?? [];
  if (ranges.length === 0) {
    return [
      {
        start: 0,
        text: result.snippet,
        highlighted: false,
      },
    ];
  }

  const segments: Array<{
    start: number;
    text: string;
    highlighted: boolean;
  }> = [];
  let cursor = 0;

  for (const range of ranges) {
    const start = Math.max(0, Math.min(range.start, result.snippet.length));
    const end = Math.max(start, Math.min(range.end, result.snippet.length));
    if (cursor < start) {
      segments.push({
        start: cursor,
        text: result.snippet.slice(cursor, start),
        highlighted: false,
      });
    }
    if (start < end) {
      segments.push({
        start,
        text: result.snippet.slice(start, end),
        highlighted: true,
      });
    }
    cursor = end;
  }

  if (cursor < result.snippet.length) {
    segments.push({
      start: cursor,
      text: result.snippet.slice(cursor),
      highlighted: false,
    });
  }

  return segments;
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
}
</script>
