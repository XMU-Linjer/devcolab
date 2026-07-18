#!/usr/bin/env node

import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { performance } from 'node:perf_hooks';

const options = parseOptions(process.argv.slice(2));
const baseUrl = options['base-url'] ?? 'http://localhost:8080';
const frontendUrl = options['frontend-url'] ?? 'http://localhost:5173';
const blockCount = positiveInteger(options.blocks ?? '50', '--blocks');
const output = options.output ?? 'tools/benchmark/.editor-benchmark-seed.json';
const suffix = Date.now().toString();
const password = 'EditorBenchmark@123456';
const username = `editor_bench_${suffix}`;

const startedAt = performance.now();
const auth = await request('/api/v1/auth/register', {
  method: 'POST',
  body: {
    username,
    displayName: `Editor Benchmark ${suffix}`,
    password,
  },
});

const workspace = await request('/api/v1/workspaces', {
  method: 'POST',
  accessToken: auth.accessToken,
  body: { name: `Editor Benchmark Workspace ${suffix}` },
});
const document = await request(`/api/v1/workspaces/${workspace.id}/documents`, {
  method: 'POST',
  accessToken: auth.accessToken,
  body: {
    title: `Editor Benchmark ${blockCount} Blocks ${suffix}`,
    documentType: 'REQUIREMENT',
  },
});

const types = ['PARAGRAPH', 'HEADING', 'CODE', 'TODO'];
for (let index = 0; index < blockCount; index += 1) {
  const type = types[index % types.length];
  await request(`/api/v1/documents/${document.id}/blocks`, {
    method: 'POST',
    accessToken: auth.accessToken,
    body: blockPayload(type, index + 1),
  });
  if ((index + 1) % 10 === 0 || index + 1 === blockCount) {
    console.log(`[editor-benchmark] blocks=${index + 1}/${blockCount}`);
  }
}

const result = {
  generatedAt: new Date().toISOString(),
  baseUrl,
  frontendUrl,
  username,
  password,
  workspaceId: workspace.id,
  documentId: document.id,
  blockCount,
  seedDurationMs: Number((performance.now() - startedAt).toFixed(2)),
  route: `${frontendUrl}/w/${workspace.id}/docs/${document.id}`,
};

await mkdir(dirname(resolve(output)), { recursive: true });
await writeFile(resolve(output), `${JSON.stringify(result, null, 2)}\n`, 'utf8');
console.log(`[editor-benchmark] seedDurationMs=${result.seedDurationMs}`);
console.log(`[editor-benchmark] route=${result.route}`);
console.log(`[editor-benchmark] wrote ${output}`);

function blockPayload(type, index) {
  const text = `${type} benchmark block ${index}`;
  const node = {
    PARAGRAPH: { type: 'paragraph', content: [{ type: 'text', text }] },
    HEADING: {
      type: 'heading',
      attrs: { level: (index % 3) + 1 },
      content: [{ type: 'text', text }],
    },
    CODE: {
      type: 'codeBlock',
      content: [{ type: 'text', text: `const block${index} = ${index};\nconsole.log(block${index});` }],
    },
    TODO: {
      type: 'taskList',
      content: [{
        type: 'taskItem',
        attrs: { checked: index % 2 === 0 },
        content: [{ type: 'paragraph', content: [{ type: 'text', text }] }],
      }],
    },
  }[type];
  return {
    type,
    content: {
      schemaVersion: 1,
      document: { type: 'doc', content: [node] },
    },
  };
}

async function request(path, { method = 'GET', body, accessToken } = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: {
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...(body ? { 'Content-Type': 'application/json' } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;
  if (!response.ok) {
    throw new Error(`${method} ${path} failed: ${response.status} ${text}`);
  }
  return payload;
}

function parseOptions(args) {
  const parsed = {};
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    if (!argument.startsWith('--')) {
      throw new Error(`Unexpected argument: ${argument}`);
    }
    const key = argument.slice(2);
    const value = args[index + 1];
    if (!value || value.startsWith('--')) {
      throw new Error(`Missing value for ${argument}`);
    }
    parsed[key] = value;
    index += 1;
  }
  return parsed;
}

function positiveInteger(value, name) {
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed <= 0) {
    throw new Error(`${name} must be a positive integer`);
  }
  return parsed;
}
