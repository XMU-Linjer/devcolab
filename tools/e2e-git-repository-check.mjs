#!/usr/bin/env node

import { existsSync } from 'node:fs';
import { resolve } from 'node:path';

const coreBaseUrl = process.env.DEVCOLLAB_CORE_BASE_URL ?? 'http://localhost:8080';
const dataRoot = resolve(process.env.DEVCOLLAB_GIT_DATA_ROOT ?? '.data/git-repositories');
const suffix = Date.now().toString();
const waitTimeoutMs = Number(process.env.DEVCOLLAB_E2E_WAIT_TIMEOUT_MS ?? '120000');

async function main() {
  const user = await api(null, '/api/v1/auth/register', {
    method: 'POST',
    body: {
      username: `git_e2e_${suffix}`,
      displayName: 'Git Repository E2E',
      password: 'Password123!',
    },
  });
  const workspace = await api(user.accessToken, '/api/v1/workspaces', {
    method: 'POST', body: { name: `Git E2E ${suffix}` },
  });
  const repository = await api(
    user.accessToken,
    `/api/v1/workspaces/${workspace.id}/git/repositories`,
    {
      method: 'POST',
      body: {
        name: 'Octocat Hello World',
        provider: 'GITHUB',
        remoteUrl: 'https://github.com/octocat/Hello-World.git',
        defaultBranch: 'master',
      },
    },
  );
  console.log(`[git-repository-e2e] queued workspace=${workspace.id} repository=${repository.id}`);

  const ready = await waitForReady(user.accessToken, workspace.id, repository.id);
  const files = await api(
    user.accessToken,
    `/api/v1/workspaces/${workspace.id}/git/repositories/${repository.id}/files`,
  );
  const changes = await api(
    user.accessToken,
    `/api/v1/workspaces/${workspace.id}/git/repositories/${repository.id}/changes`,
  );
  if (files.length === 0) throw new Error('Expected synchronized repository files');
  if (changes.length === 0) throw new Error('Expected synchronized Git log');

  const repositoryDirectory = resolve(
    dataRoot, workspace.id, repository.id, 'repository',
  );
  if (!existsSync(repositoryDirectory)) {
    throw new Error(`Clone directory does not exist: ${repositoryDirectory}`);
  }
  console.log(`[git-repository-e2e] READY head=${ready.lastSyncedCommit} files=${files.length} commits=${changes.length}`);

  await api(
    user.accessToken,
    `/api/v1/workspaces/${workspace.id}/git/repositories/${repository.id}`,
    { method: 'DELETE' },
  );
  await waitFor('clone directory deletion', () => !existsSync(repositoryDirectory));
  console.log(`[git-repository-e2e] PASS deleted=${repositoryDirectory}`);
}

async function waitForReady(token, workspaceId, repositoryId) {
  return waitFor('repository READY', async () => {
    const repositories = await api(token, `/api/v1/workspaces/${workspaceId}/git/repositories`);
    const repository = repositories.find(item => item.id === repositoryId);
    if (!repository) throw new Error('Repository disappeared before synchronization completed');
    if (repository.syncStatus === 'FAILED') {
      throw new Error(`Repository synchronization failed: ${repository.lastSyncError}`);
    }
    return repository.syncStatus === 'READY' ? repository : null;
  });
}

async function waitFor(label, probe) {
  const deadline = Date.now() + waitTimeoutMs;
  while (Date.now() < deadline) {
    const value = await probe();
    if (value) return value;
    await new Promise(resolvePromise => setTimeout(resolvePromise, 1000));
  }
  throw new Error(`Timed out waiting for ${label}`);
}

async function api(accessToken, path, options = {}) {
  const response = await fetch(`${coreBaseUrl}${path}`, {
    method: options.method ?? 'GET',
    headers: {
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
    },
    body: options.body ? JSON.stringify(options.body) : undefined,
  });
  if (!response.ok) {
    throw new Error(`${options.method ?? 'GET'} ${path} failed: ${response.status} ${await response.text()}`);
  }
  return response.status === 204 ? null : response.json();
}

main().catch(error => {
  console.error(`[git-repository-e2e] FAIL ${error.message}`);
  process.exitCode = 1;
});
