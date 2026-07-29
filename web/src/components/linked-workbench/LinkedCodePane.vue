<template>
  <section class="linked-pane linked-code-pane">
    <header class="linked-pane-header">
      <div><strong :title="path">{{ path || '选择源码文件' }}</strong><span>{{ language || 'Text' }} · {{ lines.length }} 行</span></div>
      <div class="linked-pane-actions">
        <el-tag v-if="isAgentRunning" size="small" effect="plain">{{ agentStatusLabel }}</el-tag>
        <el-tag v-else-if="agentStatus === 'COMPLETED' && changeRequestId" size="small" type="success" effect="plain">
          已生成评审建议
        </el-tag>
        <el-button
          v-if="agentStatus === 'COMPLETED' && changeRequestId"
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
        <el-tag v-if="activeAnchor" size="small" effect="plain">
          {{ activeAnchorLabel }}
        </el-tag>
        <el-tag v-if="rangeWarning" size="small" type="warning" effect="plain">
          {{ rangeWarning }}
        </el-tag>
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
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import type { ComponentPublicInstance } from 'vue';
import {
  createAgentJob,
  getAgentJob,
  readableAgentError,
  type AgentJobPhase,
  type AgentJobStatus,
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
const activeAnchorLabel = computed(() => {
  const anchor = activeAnchor.value;
  if (!anchor) return '';
  if (activeLink.value?.bindingDisplayState === 'weak') {
    if (anchor.anchorKind === 'SYMBOL') return '符号关联（无行范围）';
    if (activeLink.value.blockId) return '文件 → 段落';
    return '文件 → 文档';
  }
  if (hasLineRange(anchor)) {
    const symbol = anchor.anchorKind === 'SYMBOL' && anchor.symbolName
      ? `${anchor.symbolName} · `
      : '';
    return `${symbol}L${anchor.startLine}–${anchor.endLine}`;
  }
  if (anchor.anchorKind === 'SYMBOL') return anchor.symbolName || '符号级关联';
  return anchor.revision === null ? '旧文件级 Binding' : '文件级关联';
});
const rangeWarning = computed(() => {
  const anchor = activeAnchor.value;
  if (!anchor || anchor.startLine === null || anchor.endLine === null) return '';
  return anchor.startLine > lines.value.length || anchor.endLine < 1
    ? '关联范围已超出当前文件'
    : '';
});
const agentDialogOpen = ref(false);
const creatingRun = ref(false);
const userInstruction = ref('');
const jobId = ref<string | null>(null);
const agentStatus = ref<AgentJobStatus | null>(null);
const agentPhase = ref<AgentJobPhase | null>(null);
const changeRequestId = ref<string | null>(null);
let pollTimer: number | null = null;

const terminalStatuses = new Set<AgentJobStatus>([
  'READY_FOR_ANALYSIS', 'COMPLETED', 'PARTIALLY_COMPLETED', 'FAILED', 'CANCELLED',
]);
const statusLabels: Record<AgentJobStatus, string> = {
  QUEUED: '排队中',
  RUNNING: 'Agent 正在处理',
  READY_FOR_ANALYSIS: '项目结构分析完成',
  COMPLETED: 'Agent 检查完成',
  PARTIALLY_COMPLETED: 'Agent 部分完成',
  FAILED: 'Agent 检查失败',
  CANCELLED: 'Agent 检查已取消',
};
const phaseLabels: Record<AgentJobPhase, string> = {
  LOADING_CONTEXT: '正在读取代码和关联文档',
  MODEL_RUNNING: 'Agent 正在分析',
  VALIDATING: '正在校验建议',
  REPAIRING: '正在修正建议',
  SUBMITTING_REVIEW: '正在提交评审',
  DISCOVERING_FILES: '正在发现文件',
  CLASSIFYING_FILES: '正在分类文件',
  LOADING_CODE_METADATA: '正在读取代码结构',
  LOADING_BINDINGS: '正在读取已有 Binding',
  BUILDING_SEMANTIC_GRAPH: '正在构建语义关系',
  BUILDING_ANALYSIS_UNITS: '正在构建语义模块',
  READY_FOR_ANALYSIS: '项目结构分析完成',
  PLANNING_UNITS: 'DeepSeek 正在划分语义模块',
  VALIDATING_UNIT_PLAN: '正在校验语义模块计划',
  EXECUTING_UNITS: '正在生成正式文档',
  COMPLETED: '项目处理完成',
};
const isAgentRunning = computed(() => creatingRun.value || Boolean(
  agentStatus.value && !terminalStatuses.has(agentStatus.value),
));
const agentStatusLabel = computed(() => (
  agentPhase.value
    ? phaseLabels[agentPhase.value]
    : agentStatus.value
      ? statusLabels[agentStatus.value]
      : '正在启动'
));
const canInspect = computed(() => Boolean(
  props.sourceLoaded
  && props.path
  && props.workspaceId
  && props.repositoryId
  && !isAgentRunning.value,
));

watch(
  () => [props.workspaceId, props.repositoryId, props.path],
  () => restoreAgentJob(),
);
onBeforeUnmount(() => stopPolling());
onMounted(() => restoreAgentJob());

function anchorsForLine(line: number) {
  return props.anchors.filter(anchor => (
    anchor.filePath === props.path
    && anchor.startLine !== null
    && anchor.endLine !== null
    && line >= anchor.startLine
    && line <= anchor.endLine
  ));
}

function linksForLine(line: number) {
  const anchorIds = new Set(anchorsForLine(line).map(anchor => anchor.id));
  return props.links.filter(link => anchorIds.has(link.codeAnchorId));
}

function linkForLine(line: number) {
  const candidates = linksForLine(line);
  return candidates.find(link => link.id === props.activeLinkId) ?? candidates[0];
}

function activateLine(line: number) {
  const link = linkForLine(line);
  if (link) emit('activate', link.id);
}

function lineClass(line: number) {
  const anchors = anchorsForLine(line);
  const links = linksForLine(line);
  const active = links.some(link => link.id === props.activeLinkId);
  return {
    'is-linked': links.length > 0,
    'is-active': active,
    'is-drifted': anchors.some(anchor => anchor.status !== 'VALID'),
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
    const queued = await createAgentJob({
      workspaceId: props.workspaceId,
      repositoryId: props.repositoryId,
      scope: {
        type: 'CURRENT_FILE',
        filePath: props.path,
      },
      userInstruction: userInstruction.value.trim() || null,
    });
    jobId.value = queued.jobId;
    agentStatus.value = queued.status;
    agentPhase.value = null;
    changeRequestId.value = null;
    localStorage.setItem(activeJobStorageKey(), queued.jobId);
    agentDialogOpen.value = false;
    ElMessage.success('Agent 已在后台开始处理，可以关闭当前窗口。');
    await pollAgentJob();
  } catch (error) {
    ElMessage.error(readableAgentError(error, 'Agent 检查启动失败'));
  } finally {
    creatingRun.value = false;
  }
}

async function pollAgentJob() {
  if (!jobId.value) return;
  try {
    const job = await getAgentJob(jobId.value);
    agentStatus.value = job.status;
    agentPhase.value = job.phase;
    changeRequestId.value = job.reviewRequestIds[0] ?? null;
    if (job.status === 'COMPLETED' && job.result === 'NO_CHANGE') {
      stopPolling();
      clearActiveJob();
      ElMessage.success('当前代码与相关文档一致，无需更新。');
      return;
    }
    if (job.status === 'COMPLETED' && job.result === 'REVIEW_SUBMITTED') {
      stopPolling();
      clearActiveJob();
      return;
    }
    if (job.status === 'FAILED' || job.status === 'CANCELLED') {
      stopPolling();
      clearActiveJob();
      ElMessage.error(job.errorMessage || 'Agent 检查失败');
      return;
    }
    schedulePoll();
  } catch (error) {
    stopPolling();
    clearActiveJob();
    agentStatus.value = 'FAILED';
    ElMessage.error(readableAgentError(error, 'Agent 状态读取失败'));
  }
}

function schedulePoll() {
  stopPolling();
  pollTimer = window.setTimeout(() => void pollAgentJob(), 5000);
}

function stopPolling() {
  if (pollTimer !== null) {
    window.clearTimeout(pollTimer);
    pollTimer = null;
  }
}

function restoreAgentJob() {
  stopPolling();
  jobId.value = localStorage.getItem(activeJobStorageKey());
  agentStatus.value = null;
  agentPhase.value = null;
  changeRequestId.value = null;
  agentDialogOpen.value = false;
  resetAgentDialog();
  if (jobId.value) void pollAgentJob();
}

function activeJobStorageKey() {
  return `devcollab.agent.active-job:${props.workspaceId}:${props.repositoryId}:${props.path}`;
}

function clearActiveJob() {
  localStorage.removeItem(activeJobStorageKey());
  jobId.value = null;
}

function focusAnchor(anchorId: string) {
  const anchor = props.anchors.find(item => item.id === anchorId);
  if (!anchor || anchor.filePath !== props.path || anchor.startLine === null) return;
  const clampedLine = Math.min(Math.max(anchor.startLine, 1), lines.value.length);
  lineElements.get(clampedLine)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
}

function hasLineRange(anchor: CodeAnchor) {
  return anchor.startLine !== null && anchor.endLine !== null;
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
.code-line code { min-height: 23px; font: 13px/23px 'Cascadia Code', Consolas, monospace; white-space: pre; }
.line-number { padding-right: 10px; color: #98a2b3; font: 11px/23px Consolas, monospace; text-align: right; user-select: none; }
.line-marker { color: #528bff; font: 700 12px/23px sans-serif; text-align: center; }
.code-line.is-linked { border-left-color: #b8ccff; background: #f4f7ff; cursor: pointer; }
.code-line.is-linked:hover { background: #eaf1ff; }
.code-line.is-active { border-left-color: #155eef; background: #dfeaff; box-shadow: inset 0 1px #c4d7ff, inset 0 -1px #c4d7ff; }
.code-line.is-drifted .line-marker { color: #d97706; }
</style>
