#!/usr/bin/env node

const baseUrl = process.env.DEVCOLLAB_NGINX_BASE_URL ?? 'http://localhost:8088';

async function main() {
  const health = await request('/nginx-health');
  assertStatus(health, 200, 'Nginx health');
  const healthText = (await health.text()).trim();
  if (healthText !== 'ok') {
    throw new Error(`Unexpected Nginx health body: ${healthText}`);
  }
  console.log('[nginx-e2e] health PASS');

  const index = await request('/');
  assertStatus(index, 200, 'frontend index');
  assertHtmlContains(await index.text(), 'id="app"', 'frontend index');
  console.log('[nginx-e2e] static frontend PASS');

  const historyFallback = await request('/login');
  assertStatus(historyFallback, 200, 'SPA history fallback');
  assertHtmlContains(await historyFallback.text(), 'id="app"', 'SPA history fallback');
  console.log('[nginx-e2e] SPA fallback PASS');

  const api = await request('/api/v1/auth/me');
  if (api.status !== 401 && api.status !== 403) {
    const body = await api.text();
    throw new Error(`Expected protected Core API to return 401/403 through Nginx, got ${api.status}: ${body}`);
  }
  if (!(api.headers.get('content-type') ?? '').includes('application/json')) {
    throw new Error(`Expected Core API JSON response, got ${api.headers.get('content-type')}`);
  }
  console.log(`[nginx-e2e] Core API proxy PASS status=${api.status}`);
  console.log(`[nginx-e2e] PASS baseUrl=${baseUrl}`);
}

function request(path) {
  return fetch(`${baseUrl}${path}`, { redirect: 'manual' });
}

function assertStatus(response, expected, label) {
  if (response.status !== expected) {
    throw new Error(`${label} expected ${expected}, got ${response.status}`);
  }
}

function assertHtmlContains(html, expected, label) {
  if (!html.includes(expected)) {
    throw new Error(`${label} does not contain ${expected}`);
  }
}

main().catch(error => {
  console.error(`[nginx-e2e] FAIL ${error.message}`);
  process.exitCode = 1;
});
