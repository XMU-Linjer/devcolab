#!/usr/bin/env node

const baseUrl = process.env.DEVCOLLAB_BASE_URL ?? 'http://localhost:8088';
const repositoryUrl = process.env.DEVCOLLAB_E2E_REPOSITORY_URL
  ?? 'https://github.com/XMU-Linjer/devcolab.git';
const repositoryBranch = process.env.DEVCOLLAB_E2E_REPOSITORY_BRANCH ?? 'main';
const timeoutMs = Number(process.env.DEVCOLLAB_E2E_AGENT_TIMEOUT_MS ?? '2400000');
const suffix = Date.now().toString();
const testPassword = `ProjectAgent-${suffix}!Aa1`;

async function main() {
  const auth = await api('/api/v1/auth/register', {
    method: 'POST',
    body: {
      username: `project_agent_${suffix}`,
      displayName: 'Project Agent E2E',
      password: testPassword,
    },
  });
  const workspace = await api('/api/v1/workspaces', {
    method: 'POST',
    token: auth.accessToken,
    body: { name: `Project Agent E2E ${suffix}` },
  });
  const repository = await api(
    `/api/v1/workspaces/${workspace.id}/git/repositories`,
    {
      method: 'POST',
      token: auth.accessToken,
      body: {
        name: 'DevCollab',
        provider: 'GITHUB',
        remoteUrl: repositoryUrl,
        defaultBranch: repositoryBranch,
      },
    },
  );
  console.log(
    `[project-agent-e2e] workspace=${workspace.id} repository=${repository.id}`,
  );

  const synchronizedRepository = await waitFor(
    'repository READY',
    async () => {
      const repositories = await api(
        `/api/v1/workspaces/${workspace.id}/git/repositories`,
        { token: auth.accessToken },
      );
      const current = repositories.find((item) => item.id === repository.id);
      if (current?.syncStatus === 'FAILED') {
        throw new Error(`Repository synchronization failed: ${current.lastSyncError}`);
      }
      return current?.syncStatus === 'READY' ? current : null;
    },
    300_000,
  );
  console.log(
    `[project-agent-e2e] repository=READY revision=${synchronizedRepository.lastSyncedCommit}`,
  );

  const queued = await api('/agent-api/api/v1/agent-jobs', {
    method: 'POST',
    token: auth.accessToken,
    body: {
      workspaceId: workspace.id,
      repositoryId: repository.id,
      scope: { type: 'PROJECT_INITIALIZATION' },
      userInstruction: '扫描整个项目，按真实工程职责划分语义模块，并为每个模块生成正式简体中文工程文档。',
    },
  });
  console.log(`[project-agent-e2e] job=${queued.jobId} accepted=${queued.status}`);

  let previousProgress = '';
  const job = await waitFor(
    'Agent job terminal state',
    async () => {
      const current = await api(
        `/agent-api/api/v1/agent-jobs/${queued.jobId}`,
        { token: auth.accessToken },
      );
      const progress = [
        current.status,
        current.currentPhase,
        current.plannerStatus,
        current.plannedUnitCount,
        current.pendingUnitCount,
        current.runningUnitCount,
        current.completedUnitCount,
        current.failedUnitCount,
        current.reviewSubmittedUnitCount,
        current.noChangeUnitCount,
      ].join('|');
      if (progress !== previousProgress) {
        console.log(`[project-agent-e2e] ${progress}`);
        previousProgress = progress;
      }
      return ['COMPLETED', 'PARTIALLY_COMPLETED', 'FAILED', 'CANCELLED']
        .includes(current.status)
        ? current
        : null;
    },
    timeoutMs,
    3_000,
  );
  const units = await api(
    `/agent-api/api/v1/agent-jobs/${queued.jobId}/units?offset=0&limit=100`,
    { token: auth.accessToken },
  );

  console.log(
    `[project-agent-e2e] final=${job.status} result=${job.result} reviews=${job.reviewRequestIds.length} units=${units.total}`,
  );
  for (const unit of units.units) {
    console.log(
      `[project-agent-e2e] unit=${unit.displayName} status=${unit.status} primary=${unit.primaryFiles.length} supporting=${unit.supportingFiles.length}`,
    );
  }
  console.log(
    `[project-agent-e2e] ids workspace=${workspace.id} repository=${repository.id} job=${queued.jobId}`,
  );

  if (job.status === 'FAILED') {
    throw new Error(`Agent job failed: ${job.errorCode ?? ''} ${job.errorMessage ?? ''}`);
  }
  if (job.reviewRequestIds.length < 2) {
    throw new Error(`Expected multiple reviews, got ${job.reviewRequestIds.length}`);
  }
}

async function waitFor(label, probe, deadlineMs, intervalMs = 2_000) {
  const deadline = Date.now() + deadlineMs;
  while (Date.now() < deadline) {
    const value = await probe();
    if (value) return value;
    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }
  throw new Error(`Timed out waiting for ${label}`);
}

async function api(path, options = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    method: options.method ?? 'GET',
    headers: {
      ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}),
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
    },
    body: options.body ? JSON.stringify(options.body) : undefined,
  });
  if (!response.ok) {
    throw new Error(
      `${options.method ?? 'GET'} ${path} failed: ${response.status} ${(await response.text()).slice(0, 300)}`,
    );
  }
  return response.status === 204 ? null : response.json();
}

main().catch((error) => {
  console.error(`[project-agent-e2e] FAIL ${error.message}`);
  process.exitCode = 1;
});
