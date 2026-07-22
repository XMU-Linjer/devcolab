<template>
  <div class="linked-context">
    <section class="linked-context-section">
      <div class="linked-context-title">
        <span>仓库代码</span><small>{{ filesCount }}</small>
      </div>
      <el-select
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
        class="linked-repository-tree"
        :data="fileTree"
        node-key="key"
        :props="{ label: 'label', children: 'children' }"
        :highlight-current="true"
        :current-node-key="selectedFilePath"
        @node-click="selectFile"
      >
        <template #default="{ data }">
          <span class="linked-tree-node" :title="data.key">
            <span>{{ data.label }}</span>
            <small v-if="data.file">{{ data.file.language || 'File' }}</small>
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
        <span>▤</span><span :title="document.title">{{ document.title }}</span>
      </button>
    </section>

    <section class="linked-context-section context-summary">
      <div class="linked-context-title"><span>当前符号</span></div>
      <strong>{{ activeAnchor?.symbolName || '尚未选择关联范围' }}</strong>
      <span v-if="activeAnchor">L{{ activeAnchor.startLine }}–{{ activeAnchor.endLine }}</span>
      <button type="button" class="drift-entry" @click="emit('open-drift')">
        <span>漂移审查</span><strong>{{ driftCount }}</strong>
      </button>
    </section>
  </div>
</template>

<script setup lang="ts">
import type { GitRepository } from '@/api/git';
import type {
  CodeAnchor,
  LinkedDocumentChoice,
  LinkedFileTreeNode,
} from '@/types/linkedWorkbench';

defineProps<{
  repositories: GitRepository[];
  repositoryId: string;
  fileTree: LinkedFileTreeNode[];
  filesCount: number;
  selectedFilePath: string;
  documents: LinkedDocumentChoice[];
  selectedDocumentId: string;
  activeAnchor: CodeAnchor | null;
  driftCount: number;
  loading?: boolean;
}>();

const emit = defineEmits<{
  'select-repository': [repositoryId: string];
  'select-file': [path: string];
  'select-document': [documentId: string];
  'open-drift': [];
}>();

function selectFile(node: LinkedFileTreeNode) {
  if (node.file) emit('select-file', node.file.path);
}
</script>

<style scoped>
.linked-context { display: grid; min-height: 0; gap: 14px; }
.linked-context-section { display: grid; min-height: 0; gap: 8px; }
.linked-context-section + .linked-context-section { padding-top: 12px; border-top: 1px solid #e5eaf2; }
.linked-context-title { display: flex; align-items: center; justify-content: space-between; color: #667085; font-size: 12px; font-weight: 700; letter-spacing: .04em; }
.linked-context-title small { color: #98a2b3; }
.linked-repository-tree { max-height: min(42vh, 390px); overflow: auto; background: transparent; }
.linked-tree-node { display: flex; min-width: 0; width: 100%; align-items: center; justify-content: space-between; gap: 8px; }
.linked-tree-node > span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.linked-tree-node small { flex: 0 0 auto; color: #98a2b3; font-size: 10px; }
.related-documents { gap: 3px; }
.related-document { display: flex; min-width: 0; align-items: center; gap: 7px; border: 0; border-radius: 6px; padding-block: 7px; background: transparent; color: #475467; text-align: left; cursor: pointer; }
.related-document span:last-child { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.related-document:hover, .related-document.is-active { background: #eaf1ff; color: #175cd3; }
.context-summary { color: #667085; font-size: 12px; }
.context-summary strong { color: #101828; }
.drift-entry { display: flex; align-items: center; justify-content: space-between; border: 1px solid #f7d79b; border-radius: 7px; padding: 8px 10px; background: #fff8e7; color: #9a6700; cursor: pointer; }
</style>
