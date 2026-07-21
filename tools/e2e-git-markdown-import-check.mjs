#!/usr/bin/env node

const coreBaseUrl = process.env.DEVCOLLAB_CORE_BASE_URL ?? 'http://localhost:8080';
const suffix = Date.now().toString();
const timeoutMs = Number(process.env.DEVCOLLAB_E2E_WAIT_TIMEOUT_MS ?? '120000');

async function main() {
  const user = await api(null, '/api/v1/auth/register', {
    method: 'POST',
    body: {
      username: `markdown_import_${suffix}`,
      displayName: 'Markdown Import E2E',
      password: 'Password123!',
    },
  });
  const workspace = await api(user.accessToken, '/api/v1/workspaces', {
    method: 'POST', body: { name: `Markdown Import ${suffix}` },
  });
  const repository = await api(
    user.accessToken,
    `/api/v1/workspaces/${workspace.id}/git/repositories`,
    {
      method: 'POST',
      body: {
        name: 'Spring PetClinic Markdown',
        provider: 'GITHUB',
        remoteUrl: 'https://github.com/spring-projects/spring-petclinic.git',
        defaultBranch: 'main',
      },
    },
  );

  await waitForReady(user.accessToken, workspace.id, repository.id);
  const first = await api(
    user.accessToken,
    `/api/v1/workspaces/${workspace.id}/git/repositories/${repository.id}/documents/import`,
    { method: 'POST' },
  );
  if (first.importedDocuments < 1) {
    throw new Error(`Expected imported Markdown, result=${JSON.stringify(first)}`);
  }

  const tree = await api(
    user.accessToken,
    `/api/v1/workspaces/${workspace.id}/documents/tree`,
  );
  const importedDocument = flatten(tree).find(node => /spring petclinic/i.test(node.title));
  if (!importedDocument) throw new Error('Imported README is absent from document tree');

  const blocks = await api(
    user.accessToken,
    `/api/v1/documents/${importedDocument.id}/blocks`,
  );
  if (!blocks.some(block => /spring petclinic/i.test(block.content.text))) {
    throw new Error('Imported Markdown body is absent from document Blocks');
  }

  const second = await api(
    user.accessToken,
    `/api/v1/workspaces/${workspace.id}/git/repositories/${repository.id}/documents/import`,
    { method: 'POST' },
  );
  if (second.importedDocuments !== 0 || second.skippedDocuments < 1) {
    throw new Error(`Import is not idempotent, result=${JSON.stringify(second)}`);
  }

  console.log(
    `[git-markdown-import-e2e] PASS workspace=${workspace.id} `
      + `document=${importedDocument.id} blocks=${blocks.length} `
      + `secondImportSkipped=${second.skippedDocuments}`,
  );
}

function flatten(nodes) {
  return nodes.flatMap(node => [node, ...flatten(node.children ?? [])]);
}

async function waitForReady(token, workspaceId, repositoryId) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const repositories = await api(
      token,
      `/api/v1/workspaces/${workspaceId}/git/repositories`,
    );
    const repository = repositories.find(item => item.id === repositoryId);
    if (repository?.syncStatus === 'FAILED') {
      throw new Error(`Repository sync failed: ${repository.lastSyncError}`);
    }
    if (repository?.syncStatus === 'READY') return;
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  throw new Error('Timed out waiting for repository READY');
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
    throw new Error(
      `${options.method ?? 'GET'} ${path} failed: `
        + `${response.status} ${await response.text()}`,
    );
  }
  return response.status === 204 ? null : response.json();
}

main().catch(error => {
  console.error(`[git-markdown-import-e2e] FAIL ${error.message}`);
  process.exitCode = 1;
});
