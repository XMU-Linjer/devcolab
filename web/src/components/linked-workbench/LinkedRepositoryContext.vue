<template>
  <div class="linked-context">
    <section class="linked-context-section">
      <div class="linked-context-title">
        <span>仓库结构</span><small>{{ filesCount }} 个文件</small>
      </div>
      <el-select
        class="repository-selector"
        :model-value="repositoryId"
        size="small"
        placeholder="选择仓库"
        @update:model-value="emit('select-repository', String($event))"
      >
        <el-option
          v-for="repository in repositories"
          :key="repository.id"
          :label="repository.name"
          :value="repository.id"
        />
      </el-select>
      <el-skeleton v-if="loading" :rows="6" animated />
      <el-tree
        v-else
        ref="repositoryTreeRef"
        class="linked-repository-tree"
        :data="fileTree"
        node-key="key"
        :props="{ label: 'label', children: 'children' }"
        :highlight-current="true"
        :current-node-key="selectedFilePath"
        @node-click="selectFile"
        @node-expand="rememberExpandedNode"
        @node-collapse="forgetExpandedNode"
      >
        <template #default="{ node, data }">
          <span class="linked-tree-node" :title="data.key">
            <component
              :is="data.kind === 'directory' ? (node.expanded ? FolderOpened : Folder) : Document"
              class="repository-node-icon"
            />
            <span>{{ data.label }}</span>
            <small v-if="data.file && fileLinkCounts[data.file.path]">
              {{ fileLinkCounts[data.file.path] }}
            </small>
          </span>
        </template>
      </el-tree>
    </section>

    <section class="linked-context-section related-documents">
      <div class="linked-context-title"><span>关联文档</span><small>{{ documents.length }}</small></div>
      <button
        v-for="document in documents"
        :key="document.id"
        type="button"
        class="related-document"
        :class="{ 'is-active': document.id === selectedDocumentId }"
        :style="{ paddingLeft: `${10 + document.depth * 14}px` }"
        @click="emit('select-document', document.id)"
      >
        <span class="document-glyph">▤</span>
        <span :title="document.title">{{ document.title }}</span>
        <small v-if="document.version">V{{ document.version }}</small>
        <small v-else-if="document.reviewStatus">{{ document.reviewStatus }}</small>
      </button>
      <p v-if="documents.length === 0" class="linked-context-empty">当前上下文暂无关联文档</p>
    </section>

    <section class="linked-context-section context-summary">
      <div class="linked-context-title"><span>当前符号</span></div>
      <strong>{{ activeAnchor?.symbolName || '未选择代码符号' }}</strong>
      <dl v-if="activeAnchor" class="symbol-metrics">
        <dt>关联 Block</dt><dd>{{ linkedBlockCount }}</dd>
        <dt>未解决问题</dt><dd>{{ unresolvedIssueCount }}</dd>
        <dt>最近提交</dt><dd>{{ recentCommitCount }}</dd>
      </dl>
    </section>
  </div>
</template>

<script setup lang="ts">
import { Document, Folder, FolderOpened } from '@element-plus/icons-vue';
import { ElTree } from 'element-plus';
import { nextTick, ref, watch } from 'vue';

import type { GitRepository } from '@/api/git';
import type {
  CodeAnchor,
  LinkedDocumentChoice,
  LinkedFileTreeNode,
} from '@/types/linkedWorkbench';

const props = withDefaults(defineProps<{
  repositories: GitRepository[];
  repositoryId: string;
  fileTree: LinkedFileTreeNode[];
  filesCount: number;
  selectedFilePath: string;
  documents: LinkedDocumentChoice[];
  selectedDocumentId: string;
  activeAnchor: CodeAnchor | null;
  fileLinkCounts?: Record<string, number>;
  linkedBlockCount?: number;
  unresolvedIssueCount?: number;
  recentCommitCount?: number;
  loading?: boolean;
}>(), {
  fileLinkCounts: () => ({}),
  linkedBlockCount: 0,
  unresolvedIssueCount: 0,
  recentCommitCount: 0,
});

const emit = defineEmits<{
  'select-repository': [repositoryId: string];
  'select-file': [path: string];
  'select-document': [documentId: string];
}>();

const repositoryTreeRef = ref<InstanceType<typeof ElTree> | null>(null);
const expandedNodeKeys = new Set<string>();

function rememberExpandedNode(node: LinkedFileTreeNode) {
  if (node.kind === 'directory') expandedNodeKeys.add(node.key);
}

function forgetExpandedNode(node: LinkedFileTreeNode) {
  expandedNodeKeys.delete(node.key);
}

async function restoreSelectedFilePosition() {
  await nextTick();
  const tree = repositoryTreeRef.value;
  if (!tree) return;

  for (const key of expandedNodeKeys) {
    const expandedNode = tree.getNode(key);
    if (expandedNode) expandedNode.expanded = true;
  }

  if (!props.selectedFilePath) return;
  const selectedNode = tree.getNode(props.selectedFilePath);
  if (!selectedNode) return;

  let parent = selectedNode.parent;
  while (parent && parent.level > 0) {
    parent.expanded = true;
    expandedNodeKeys.add(String(parent.key));
    parent = parent.parent;
  }

  tree.setCurrentKey(props.selectedFilePath);
  await nextTick();
  const currentElement = (tree.$el as HTMLElement).querySelector<HTMLElement>(
    '.el-tree-node.is-current > .el-tree-node__content',
  );
  if (typeof currentElement?.scrollIntoView === 'function') {
    currentElement.scrollIntoView({
      block: 'nearest',
      inline: 'nearest',
      behavior: 'auto',
    });
  }
}

function selectFile(node: LinkedFileTreeNode) {
  if (node.kind === 'file' && node.file) {
    emit('select-file', node.file.path);
    return;
  }
  void restoreSelectedFilePosition();
}

watch(
  [() => props.selectedFilePath, () => props.fileTree],
  () => { void restoreSelectedFilePosition(); },
  { immediate: true, flush: 'post' },
);
</script>

<style scoped>
.linked-context { display: grid; min-width: 0; gap: 18px; }
.linked-context-section { display: grid; min-width: 0; gap: 7px; }
.linked-context-title { display: flex; align-items: center; justify-content: space-between; color: #667085; font-size: 12px; font-weight: 700; letter-spacing: .04em; }
.linked-context-title small { color: #98a2b3; }
.repository-selector { width: 100%; }
.linked-repository-tree { overflow: visible; background: transparent; scroll-behavior: auto; }
.linked-repository-tree :deep(.el-tree-node__content) { min-width: 0; height: 32px; border-radius: 7px; transition: background-color 60ms linear; }
.linked-repository-tree :deep(.el-tree-node__content:hover) { background: #f5f7fb; }
.linked-repository-tree :deep(.el-tree-node:focus-visible > .el-tree-node__content) { outline: 2px solid #84adff; outline-offset: -2px; }
.linked-repository-tree :deep(.el-tree-node__expand-icon),
.linked-repository-tree :deep(.el-tree-node__children),
.linked-repository-tree :deep(.collapse-transition),
.linked-repository-tree :deep(.el-collapse-transition-enter-active),
.linked-repository-tree :deep(.el-collapse-transition-leave-active) { transition: none !important; }
.linked-repository-tree :deep(.el-tree--highlight-current .el-tree-node.is-current > .el-tree-node__content),
.linked-repository-tree :deep(.el-tree-node.is-current > .el-tree-node__content) { background: #edf2ff; color: #315ee8; font-weight: 700; }
.linked-tree-node { display: flex; min-width: 0; width: 100%; align-items: center; justify-content: space-between; gap: 8px; }
.linked-tree-node > span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.linked-tree-node small { flex: 0 0 auto; color: #98a2b3; font-size: 10px; }
.repository-node-icon { flex: 0 0 auto; width: 14px; color: #667085; }
.related-documents { gap: 3px; }
.related-document { display: flex; min-width: 0; align-items: center; gap: 7px; border: 0; border-radius: 7px; padding-block: 8px; padding-right: 9px; background: transparent; color: #475467; text-align: left; cursor: pointer; }
.related-document span:nth-child(2) { min-width: 0; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.related-document small { flex: 0 0 auto; color: #98a2b3; font-size: 11px; }
.related-document:hover, .related-document.is-active { background: #eaf1ff; color: #175cd3; }
.document-glyph { flex: 0 0 auto; }
.linked-context-empty { margin: 2px 8px; color: #98a2b3; font-size: 11px; line-height: 1.5; }
.context-summary { color: #667085; font-size: 12px; }
.context-summary strong { color: #101828; }
.symbol-metrics { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 7px 12px; margin: 2px 0 0 18px; }
.symbol-metrics dt { color: #667085; }
.symbol-metrics dd { margin: 0; color: #475467; font-variant-numeric: tabular-nums; }
</style>
