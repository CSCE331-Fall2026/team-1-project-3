// Regression for #79: kitchen refreshes and polls must never overlap or answer out of order. Run with `npm test`.
import { test, after } from "node:test";
import assert from "node:assert/strict";
import { createServer } from "vite";

const vite = await createServer({ server: { middlewareMode: true }, appType: "custom", logLevel: "silent" });
after(() => vite.close());
const { createLoader } = await vite.ssrLoadModule("/src/Kitchen.jsx");

// A load whose responses the test resolves by hand, in any order.
function manualLoad() {
  const calls = [];
  const load = (signal) => new Promise((resolve, reject) => {
    signal.addEventListener("abort", () => reject(signal.reason)); // what fetch does
    calls.push({ resolve, reject, signal });
  });
  return { calls, load };
}
const tick = () => new Promise((r) => setImmediate(r));

test("reloads during a request do not start a second one; one follow-up runs after it", async () => {
  const { calls, load } = manualLoad();
  const results = [];
  const l = createLoader(load, (err, data) => results.push(err ? `err:${err.message}` : data));

  l.reload();
  l.reload(); // poll tick
  l.reload(); // manual refresh
  assert.equal(calls.length, 1, "no overlapping requests");

  calls[0].resolve("old");
  await tick();
  assert.equal(calls.length, 2, "queued reloads collapse into exactly one follow-up");
  calls[1].resolve("new");
  await tick();
  assert.deepEqual(results, ["old", "new"], "the newest request answers last");
  assert.equal(calls.length, 2);
});

test("stop aborts the request in flight and drops late results and queued reloads", async () => {
  const { calls, load } = manualLoad();
  const results = [];
  const l = createLoader(load, (err, data) => results.push(err ? "err" : data));

  l.reload();
  l.reload(); // queued
  l.stop(); // tab switch / unmount
  assert.equal(calls[0].signal.aborted, true);
  await tick();
  l.reload();
  assert.equal(calls.length, 1, "nothing runs after stop");
  assert.deepEqual(results, [], "the abort is not reported as an error");
});

test("a failed load reports the error and the next reload still works", async () => {
  const { calls, load } = manualLoad();
  const results = [];
  const l = createLoader(load, (err, data) => results.push(err ? `err:${err.message}` : data));

  l.reload();
  calls[0].reject(new Error("Failed to fetch"));
  await tick();
  l.reload();
  calls[1].resolve("ok");
  await tick();
  assert.deepEqual(results, ["err:Failed to fetch", "ok"]);
});

test("a failure never retries by itself and drops reloads queued behind it", async () => {
  const { calls, load } = manualLoad();
  const results = [];
  const l = createLoader(load, (err) => results.push(err ? "err" : "ok"));

  l.reload();
  l.reload(); // poll tick queued behind the failing request
  calls[0].reject(new Error("500"));
  await tick();
  await tick();
  assert.equal(calls.length, 1, "no immediate retry: the next poll tick retries");
  assert.deepEqual(results, ["err"]);
});

test("a hung request times out, is reported, and frees the loader for the next poll", async () => {
  const { calls, load } = manualLoad();
  const results = [];
  const l = createLoader(load, (err, data) => results.push(err ? err.name : data), 20);

  l.reload();
  l.reload(); // queued behind the hung request
  await new Promise((r) => setTimeout(r, 60));
  assert.equal(calls[0].signal.aborted, true, "timed-out request is aborted");
  assert.deepEqual(results, ["TimeoutError"]);
  assert.equal(calls.length, 1, "no burst after the timeout");

  l.reload(); // next poll tick
  assert.equal(calls.length, 2);
  calls[1].resolve("ok");
  await tick();
  assert.deepEqual(results, ["TimeoutError", "ok"]);
  l.stop();
});

test("a load that resolves after its timeout is reported as a timeout, not as data", async () => {
  // api() turns an abort during the body read into a null "success" (res.json().catch(() => null)).
  let resolveLate;
  const load = () => new Promise((resolve) => { resolveLate = resolve; });
  const results = [];
  const l = createLoader(load, (err, data) => results.push(err ? err.name : data), 20);

  l.reload();
  await new Promise((r) => setTimeout(r, 40));
  resolveLate(null);
  await tick();
  assert.deepEqual(results, ["TimeoutError"]);
  l.stop();
});
