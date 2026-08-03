const baseUrl = process.env.BASE_URL ?? "http://localhost:8080";
const requestPath = process.env.LOAD_TEST_PATH ?? "/api/v1/payments/cursor?size=20";
const requestCount = positiveInteger("REQUESTS", 100);
const concurrency = positiveInteger("CONCURRENCY", 10);
const expectedStatus = positiveInteger("EXPECTED_STATUS", 200);

const targetUrl = new URL(requestPath, baseUrl).toString();
const results = [];
let nextRequest = 0;

async function runRequest() {
  const startedAt = performance.now();
  try {
    const response = await fetch(targetUrl, {
      headers: { Accept: "application/json" },
      signal: AbortSignal.timeout(10_000),
    });
    await response.arrayBuffer();
    results.push({
      status: response.status,
      durationMs: performance.now() - startedAt,
    });
  } catch (error) {
    results.push({
      status: 0,
      durationMs: performance.now() - startedAt,
      error: error instanceof Error ? error.message : String(error),
    });
  }
}

async function worker() {
  while (nextRequest < requestCount) {
    nextRequest += 1;
    await runRequest();
  }
}

await Promise.all(
  Array.from({ length: Math.min(concurrency, requestCount) }, () => worker()),
);

const durations = results.map((result) => result.durationMs).sort((a, b) => a - b);
const successful = results.filter((result) => result.status === expectedStatus);
const failures = results.length - successful.length;

console.log(`target=${targetUrl}`);
console.log(`requests=${requestCount} concurrency=${concurrency}`);
console.log(`expectedStatus=${expectedStatus} success=${successful.length} failure=${failures}`);
console.log(`failureRate=${((failures / requestCount) * 100).toFixed(2)}%`);
console.log(`latencyMs min=${format(durations[0])} avg=${format(average(durations))} p50=${format(percentile(durations, 0.5))} p95=${format(percentile(durations, 0.95))} max=${format(durations.at(-1))}`);

const statusCounts = results.reduce((counts, result) => {
  const status = String(result.status);
  counts[status] = (counts[status] ?? 0) + 1;
  return counts;
}, {});
console.log(`statusCounts=${JSON.stringify(statusCounts)}`);

if (failures > 0) {
  const firstFailure = results.find((result) => result.status !== expectedStatus);
  if (firstFailure?.error) {
    console.log(`firstFailure=${firstFailure.error}`);
  }
  process.exitCode = 1;
}

function positiveInteger(name, fallback) {
  const value = Number.parseInt(process.env[name] ?? String(fallback), 10);
  if (!Number.isInteger(value) || value < 1) {
    throw new Error(`${name} must be a positive integer`);
  }
  return value;
}

function average(values) {
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

function percentile(sortedValues, ratio) {
  const index = Math.min(sortedValues.length - 1, Math.ceil(sortedValues.length * ratio) - 1);
  return sortedValues[index];
}

function format(value) {
  return value.toFixed(2);
}
