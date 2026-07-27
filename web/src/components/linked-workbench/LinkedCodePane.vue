<template>
  <section class="linked-pane linked-code-pane">
    <header class="linked-pane-header">
      <div><strong :title="path">{{ path || '选择源码文件' }}</strong><span>{{ language || 'Text' }} · {{ lines.length }} 行</span></div>
      <div class="linked-pane-actions">
        <el-tag v-if="isAgentRunning" size="small" effect="plain">{{ agentStatusLabel }}</el-tag>
        <el-tag v-else-if="agentStatus === 'REVIEW_SUBMITTED'" size="small" type="success" effect="plain">
          已生成评审建议
        </el-tag>
        <el-button
          v-if="agentStatus === 'REVIEW_SUBMITTED'"
          data-testid="agent-review-button"
          size="small"
          link
          type="primary"
          @click="emit('open-agent-review', changeRequestId)"
        >
          查看评审
        </el-button>
        <el-button
          data-testid="agent-check-button"
          size="small"
          plain
          :disabled="!canInspect"
          :loading="isAgentRunning"
          @click="openAgentDialog"
        >
          {{ isAgentRunning ? agentStatusLabel : 'Agent 检查' }}
        </el-button>
        <el-tag v-if="activeAnchor" size="small" effect="plain">L{{ activeAnchor.startLine }}–{{ activeAnchor.endLine }}</el-tag>
      </div>
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
    <el-dialog
      v-model="agentDialogOpen"
      title="Agent 检查当前文件"
      width="440px"
      append-to-body
      destroy-on-close
      @closed="resetAgentDialog"
    >
      <div class="agent-dialog-content">
        <p><strong>文件：</strong>{{ path }}</p>
        <el-input
          data-testid="agent-instruction-input"
          v-model="userInstruction"
          type="textarea"
          :rows="4"
          maxlength="2000"
          show-word-limit
          autofocus
          placeholder="补充说明（可选）"
        />
      </div>
      <template #footer>
        <el-button :disabled="creatingRun" @click="agentDialogOpen = false">取消</el-button>
        <el-button
          data-testid="agent-start-button"
          type="primary"
          :loading="creatingRun"
          :disabled="creatingRun"
          @click="startAgentCheck"
        >
          开始检查
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus';
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import type { ComponentPublicInstance } from 'vue';
import {
  createAgentRun,
  getAgentRun,
  readableAgentError,
  type AgentRunStatus,
} from '@/api/agent';
import type { CodeAnchor, CodeDocumentLink, EngineeringIssue } from '@/types/linkedWorkbench';

const props = defineProps<{
  workspaceId: string;
  repositoryId: string;
  content: string;
  path: string;
  language?: string | null;
  anchors: CodeAnchor[];
  links: CodeDocumentLink[];
  issues: EngineeringIssue[];
  activeLinkId: string | null;
  loading?: boolean;
  sourceLoaded?: boolean;
}>();

const emit = defineEmits<{
  activate: [linkId: string];
  'open-agent-review': [changeRequestId: string | null];
}>();
const lineElements = new Map<number, HTMLElement>();
const lines = computed(() => props.content.split(/\r?\n/));
const activeLink = computed(() => props.links.find(link => link.id === props.activeLinkId) ?? null);
const activeAnchor = computed(() => props.anchors.find(anchor => anchor.id === activeLink.value?.codeAnchorId) ?? null);
const agentDialogOpen = ref(false);
const creatingRun = ref(false);
const userInstruction = ref('');
const runId = ref<string | null>(null);
const agentStatus = ref<AgentRunStatus | null>(null);
const changeRequestId = ref<string | null>(null);
let pollTimer: number | null = null;

const terminalStatuses = new Set<AgentRunStatus>(['REVIEW_SUBMITTED', 'NO_CHANGE', 'FAILED']);
const statusLabels: Record<AgentRunStatus, string> = {
  QUEUED: '排队中',
  BUILDING_CONTEXT: '正在读取代码和关联文档',
  PLANNING: 'Agent 正在分析',
  VALIDATING: '正在校验建议',
  REPAIRING_PLAN: '正在修正建议',
  SUBMITTING_REVIEW: '正在提交评审',
  REVIEW_SUBMITTED: '已生成评审建议',
  NO_CHANGE: '文档无需更新',
  FAILED: 'Agent 检查失败',
};
const isAgentRunning = computed(() => creatingRun.value || Boolean(
  agentStatus.value && !terminalStatuses.has(agentStatus.value),
));
const agentStatusLabel = computed(() => agentStatus.value ? statusLabels[agentStatus.value] : '正在启动');
const canInspect = computed(() => Boolean(
  props.sourceLoaded
  && props.path
  && props.workspaceId
  && props.repositoryId
  && !isAgentRunning.value,
));

watch(
  () => [props.workspaceId, props.repositoryId, props.path],
  () => resetAgentRun(),
);
onBeforeUnmount(() => stopPolling());

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

function openAgentDialog() {
  if (canInspect.value) agentDialogOpen.value = true;
}

function resetAgentDialog() {
  userInstruction.value = '';
}

async function startAgentCheck() {
  if (!canInspect.value || creatingRun.value) return;
  creatingRun.value = true;
  try {
    const queued = await createAgentRun({
      workspaceId: props.workspaceId,
      repositoryId: props.repositoryId,
      selectedPaths: [props.path],
      userInstruction: userInstruction.value.trim() || null,
    });
    runId.value = queued.runId;
    agentStatus.value = queued.status;
    changeRequestId.value = null;
    agentDialogOpen.value = false;
    await pollAgentRun();
  } catch (error) {
    ElMessage.error(readableAgentError(error, 'Agent 检查启动失败'));
  } finally {
    creatingRun.value = false;
  }
}

async function pollAgentRun() {
  if (!runId.value) return;
  try {
    const run = await getAgentRun(runId.value);
    agentStatus.value = run.status;
    changeRequestId.value = run.changeRequestId;
    if (run.status === 'NO_CHANGE') {
      stopPolling();
      runId.value = null;
      ElMessage.success('当前代码与相关文档一致，无需更新。');
      return;
    }
    if (run.status === 'REVIEW_SUBMITTED') {
      stopPolling();
      runId.value = null;
      return;
    }
    if (run.status === 'FAILED') {
      stopPolling();
      runId.value = null;
      ElMessage.error(run.errorMessage || 'Agent 检查失败');
      return;
    }
    schedulePoll();
  } catch (error) {
    stopPolling();
    runId.value = null;
    agentStatus.value = 'FAILED';
    ElMessage.error(readableAgentError(error, 'Agent 状态读取失败'));
  }
}

function schedulePoll() {
  stopPolling();
  pollTimer = window.setTimeout(() => void pollAgentRun(), 1800);
}

function stopPolling() {
  if (pollTimer !== null) {
    window.clearTimeout(pollTimer);
    pollTimer = null;
  }
}

function resetAgentRun() {
  stopPolling();
  runId.value = null;
  agentStatus.value = null;
  changeRequestId.value = null;
  agentDialogOpen.value = false;
  resetAgentDialog();
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
.linked-pane-header > .linked-pane-actions { display: flex; flex: 0 0 auto; grid-auto-flow: column; align-items: center; gap: 7px; }
.linked-pane-header strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.linked-pane-header span { color: #667085; font-size: 11px; }
.agent-dialog-content { display: grid; gap: 14px; }
.agent-dialog-content p { overflow-wrap: anywhere; margin: 0; color: #475467; }
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
