#!/usr/bin/env node

const baseUrl = process.env.DEVCOLLAB_WORKER_ACTUATOR_URL ?? 'http://localhost:8082/actuator';

const requiredMetrics = [
  'devcollab_worker_event_projection_failed_total',
  'devcollab_worker_kafka_retry_total',
  'devcollab_worker_kafka_dlq_total',
  'spring_kafka_listener_seconds_count',
];

async function main() {
  const health = await getJson(`${baseUrl}/health`);
  if (health.status !== 'UP') {
    throw new Error(`Expected health UP, got ${JSON.stringify(health)}`);
  }
  console.log(`[worker-observe-e2e] health=${health.status}`);

  const prometheus = await getText(`${baseUrl}/prometheus`);
  for (const metric of requiredMetrics) {
    const lines = prometheus
      .split(/\r?\n/)
      .filter(line => line.startsWith(metric));
    if (lines.length === 0) {
      throw new Error(`Missing metric ${metric}`);
    }
    console.log(`[worker-observe-e2e] metric ${metric}`);
    for (const line of lines.slice(0, 3)) {
      console.log(`  ${line}`);
    }
  }

  if (!prometheus.includes('exception="IllegalArgumentException"')) {
    throw new Error('Expected Spring Kafka listener metric to expose IllegalArgumentException');
  }
  console.log('[worker-observe-e2e] exception=IllegalArgumentException');
  console.log('[worker-observe-e2e] PASS');
}

async function getJson(url) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`GET ${url} failed: ${response.status}`);
  }
  return response.json();
}

async function getText(url) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`GET ${url} failed: ${response.status}`);
  }
  return response.text();
}

main().catch(error => {
  console.error(`[worker-observe-e2e] FAIL ${error.message}`);
  process.exitCode = 1;
});
