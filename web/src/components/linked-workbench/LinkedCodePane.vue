<template>
  <section class="linked-pane linked-code-pane">
    <header class="linked-pane-header">
      <div><strong :title="path">{{ path || '选择源码文件' }}</strong><span>{{ language || 'Text' }} · {{ lines.length }} 行</span></div>
      <el-tag v-if="activeAnchor" size="small" effect="plain">L{{ activeAnchor.startLine }}–{{ activeAnchor.endLine }}</el-tag>
    </header>
    <el-skeleton v-if="loading" :rows="12" animated />
    <el-empty v-else-if="!content" description="从左侧仓库树选择可读源码" />
    <div v-else ref="scrollRoot" class="code-lines" role="list" aria-label="源码行">
      <button
        v-for="(line, index) in lines"
        :key="index"
        :ref="element => bindLine(index + 1, element)"
        type="button"
        class="code-line"
        :class="lineClass(index + 1)"
        :aria-label="lineLabel(index + 1)"
        @click="activateLine(index + 1)"
      >
        <span class="line-number">{{ index + 1 }}</span>
        <span class="line-marker">{{ markerFor(index + 1) }}</span>
        <code>{{ line || ' ' }}</code>
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { ComponentPublicInstance } from 'vue';
import type { CodeAnchor, CodeDocumentLink, EngineeringIssue } from '@/types/linkedWorkbench';

const props = defineProps<{
  content: string;
  path: string;
  language?: string | null;
  anchors: CodeAnchor[];
  links: CodeDocumentLink[];
  issues: EngineeringIssue[];
  activeLinkId: string | null;
  loading?: boolean;
}>();

const emit = defineEmits<{ activate: [linkId: string] }>();
const lineElements = new Map<number, HTMLElement>();
const lines = computed(() => props.content.split(/\r?\n/));
const activeLink = computed(() => props.links.find(link => link.id === props.activeLinkId) ?? null);
const activeAnchor = computed(() => props.anchors.find(anchor => anchor.id === activeLink.value?.codeAnchorId) ?? null);

function anchorForLine(line: number) {
  return props.anchors.find(anchor => line >= anchor.startLine && line <= anchor.endLine);
}

function linkForLine(line: number) {
  const anchor = anchorForLine(line);
  return props.links.find(link => link.codeAnchorId === anchor?.id);
}

function activateLine(line: number) {
  const link = linkForLine(line);
  if (link) emit('activate', link.id);
}

function lineClass(line: number) {
  const anchor = anchorForLine(line);
  const link = linkForLine(line);
  return {
    'is-linked': Boolean(link),
    'is-active': link?.id === props.activeLinkId,
    'is-drifted': anchor?.status === 'DRIFTED' || anchor?.status === 'BROKEN',
  };
}

function markerFor(line: number) {
  const link = linkForLine(line);
  if (!link) return '';
  if (props.issues.some(issue => issue.linkId === link.id)) return '!';
  return '↔';
}

function lineLabel(line: number) {
  const link = linkForLine(line);
  return link ? `第 ${line} 行，已关联，点击同步定位` : `第 ${line} 行`;
}

function bindLine(line: number, element: Element | ComponentPublicInstance | null) {
  if (element instanceof HTMLElement) lineElements.set(line, element);
  else lineElements.delete(line);
}

function focusAnchor(anchorId: string) {
  const anchor = props.anchors.find(item => item.id === anchorId);
  if (!anchor) return;
  lineElements.get(anchor.startLine)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
}

defineExpose({ focusAnchor });
</script>

<style scoped>
.linked-pane { min-width: 0; min-height: 0; background: #fff; }
.linked-code-pane { display: grid; grid-template-rows: auto minmax(0, 1fr); }
.linked-pane-header { display: flex; min-width: 0; min-height: 58px; align-items: center; justify-content: space-between; gap: 12px; padding: 9px 13px; border-bottom: 1px solid #e4e9f1; }
.linked-pane-header > div { display: grid; min-width: 0; gap: 3px; }
.linked-pane-header strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.linked-pane-header span { color: #667085; font-size: 11px; }
.code-lines { min-width: 0; overflow: auto; padding: 8px 0 24px; background: #fbfcfe; }
.code-line { display: grid; width: 100%; min-width: max-content; grid-template-columns: 48px 22px minmax(0, 1fr); border: 0; border-left: 3px solid transparent; padding: 0 14px 0 0; background: transparent; color: #344054; text-align: left; cursor: default; }
.code-line code { min-height: 22px; font: 12px/22px Consolas, 'Cascadia Code', monospace; white-space: pre; }
.line-number { padding-right: 10px; color: #98a2b3; font: 11px/22px Consolas, monospace; text-align: right; user-select: none; }
.line-marker { color: #528bff; font: 700 12px/22px sans-serif; text-align: center; }
.code-line.is-linked { border-left-color: #b8ccff; background: #f4f7ff; cursor: pointer; }
.code-line.is-linked:hover { background: #eaf1ff; }
.code-line.is-active { border-left-color: #155eef; background: #dfeaff; box-shadow: inset 0 1px #c4d7ff, inset 0 -1px #c4d7ff; }
.code-line.is-drifted .line-marker { color: #d97706; }
</style>
