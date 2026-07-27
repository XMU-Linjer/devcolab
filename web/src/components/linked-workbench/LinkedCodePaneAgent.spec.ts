import { flushPromises, mount } from '@vue/test-utils';
import { ElMessage } from 'element-plus';
import { defineComponent } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { createAgentRun, getAgentRun, type AgentRunStatus } from '@/api/agent';
import LinkedCodePane from './LinkedCodePane.vue';

vi.mock('@/api/agent', () => ({
  createAgentRun: vi.fn(),
  getAgentRun: vi.fn(),
  readableAgentError: (_error: unknown, fallback: string) => fallback,
}));

const ElButtonStub = defineComponent({
  inheritAttrs: false,
  props: {
    disabled: Boolean,
    loading: Boolean,
  },
  emits: ['click'],
  template: `
    <button
      v-bind="$attrs"
      :disabled="disabled"
      :data-loading="String(loading)"
      @click="$emit('click')"
    ><slot /></button>
  `,
});
const ElDialogStub = defineComponent({
  props: {
    modelValue: Boolean,
    title: String,
  },
  emits: ['update:modelValue', 'closed'],
  template: `
    <section v-if="modelValue" role="dialog">
      <h2>{{ title }}</h2>
      <slot />
      <slot name="footer" />
    </section>
  `,
});
const ElInputStub = defineComponent({
  inheritAttrs: false,
  props: {
    modelValue: String,
  },
  emits: ['update:modelValue'],
  template: `
    <textarea
      v-bind="$attrs"
      :value="modelValue"
      @input="$emit('update:modelValue', $event.target.value)"
    />
  `,
});

const baseProps = {
  workspaceId: 'workspace-1',
  repositoryId: 'repository-1',
  content: 'class Example {}',
  path: 'src/Example.java',
  language: 'Java',
  anchors: [],
  links: [],
  issues: [],
  activeLinkId: null,
  sourceLoaded: true,
};

function mountPane(overrides: Record<string, unknown> = {}) {
  return mount(LinkedCodePane, {
    props: {
      ...baseProps,
      ...overrides,
    },
    global: {
      stubs: {
        ElButton: ElButtonStub,
        ElDialog: ElDialogStub,
        ElInput: ElInputStub,
        ElTag: true,
        ElSkeleton: true,
        ElEmpty: true,
      },
    },
  });
}

async function startCheck(wrapper: ReturnType<typeof mountPane>) {
  await wrapper.get('[data-testid="agent-check-button"]').trigger('click');
  await wrapper.get('[data-testid="agent-start-button"]').trigger('click');
  await flushPromises();
}

describe('LinkedCodePane Agent check', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.mocked(createAgentRun).mockResolvedValue({
      runId: 'run-1',
      status: 'QUEUED',
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('disables Agent check until a real source file is loaded', () => {
    const wrapper = mountPane({
      path: '',
      content: '',
      sourceLoaded: false,
    });

    expect(wrapper.get('[data-testid="agent-check-button"]').attributes('disabled')).toBeDefined();
  });

  it('submits only the current file and stops polling on NO_CHANGE', async () => {
    vi.spyOn(ElMessage, 'success').mockReturnValue({ close: vi.fn() });
    vi.mocked(getAgentRun)
      .mockResolvedValueOnce({
        ...agentRun('PLANNING'),
      })
      .mockResolvedValueOnce({
        ...agentRun('NO_CHANGE'),
        decision: 'NO_CHANGE',
      });
    const wrapper = mountPane();

    await wrapper.get('[data-testid="agent-check-button"]').trigger('click');
    await wrapper.get('[data-testid="agent-instruction-input"]').setValue('核对接口说明');
    await wrapper.get('[data-testid="agent-start-button"]').trigger('click');
    await flushPromises();

    expect(createAgentRun).toHaveBeenCalledWith({
      workspaceId: 'workspace-1',
      repositoryId: 'repository-1',
      selectedPaths: ['src/Example.java'],
      userInstruction: '核对接口说明',
    });
    expect(getAgentRun).toHaveBeenCalledTimes(1);
    expect(vi.getTimerCount()).toBe(1);

    await vi.advanceTimersByTimeAsync(1800);
    await flushPromises();

    expect(getAgentRun).toHaveBeenCalledTimes(2);
    expect(vi.getTimerCount()).toBe(0);
    expect(wrapper.get('[data-testid="agent-check-button"]').text()).toContain('Agent 检查');
  });

  it('stops polling and exposes the existing review navigation on REVIEW_SUBMITTED', async () => {
    vi.mocked(getAgentRun).mockResolvedValue({
      ...agentRun('REVIEW_SUBMITTED'),
      decision: 'SUBMIT_REVIEW',
      changeRequestId: 'change-1',
    });
    const wrapper = mountPane();

    await startCheck(wrapper);

    expect(vi.getTimerCount()).toBe(0);
    expect(wrapper.get('[data-testid="agent-review-button"]').text()).toContain('查看评审');
    await wrapper.get('[data-testid="agent-review-button"]').trigger('click');
    expect(wrapper.emitted('open-agent-review')).toEqual([['change-1']]);
  });

  it('stops polling and keeps the entry reusable after FAILED', async () => {
    vi.spyOn(ElMessage, 'error').mockReturnValue({ close: vi.fn() });
    vi.mocked(getAgentRun).mockResolvedValue({
      ...agentRun('FAILED'),
      errorCode: 'MODEL_PROVIDER_ERROR',
      errorMessage: 'Agent 服务暂时不可用',
    });
    const wrapper = mountPane();

    await startCheck(wrapper);

    expect(vi.getTimerCount()).toBe(0);
    expect(wrapper.get('[data-testid="agent-check-button"]').attributes('disabled')).toBeUndefined();
    expect(ElMessage.error).toHaveBeenCalledWith('Agent 服务暂时不可用');
  });

  it('clears a pending poll when the component is unmounted', async () => {
    vi.mocked(getAgentRun).mockResolvedValue(agentRun('PLANNING'));
    const wrapper = mountPane();

    await startCheck(wrapper);
    expect(vi.getTimerCount()).toBe(1);

    wrapper.unmount();
    expect(vi.getTimerCount()).toBe(0);
  });
});

function agentRun(status: AgentRunStatus) {
  return {
    runId: 'run-1',
    status,
    workspaceId: 'workspace-1',
    repositoryId: 'repository-1',
    selectedPaths: ['src/Example.java'],
    currentNode: 'test',
    decision: null,
    summary: null,
    changeRequestId: null,
    errorCode: null,
    errorMessage: null,
    createdAt: '2026-07-28T00:00:00Z',
    updatedAt: '2026-07-28T00:00:00Z',
  };
}
