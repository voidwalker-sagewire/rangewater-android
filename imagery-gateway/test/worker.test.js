import assert from "node:assert/strict";
import test from "node:test";
import { parseTile, tileBounds, upstreamUrl } from "../src/worker.js";

test("accepts valid NAIP tile paths", () => {
  assert.deepEqual(parseTile("/naip/15/7000/13000.jpg"), {
    z: 15,
    x: 7000,
    y: 13000
  });
});

test("rejects paths and coordinates outside the supported tile range", () => {
  assert.equal(parseTile("/naip/14/1/1.jpg"), null);
  assert.equal(parseTile("/naip/21/1/1.jpg"), null);
  assert.equal(parseTile("/naip/15/32768/1.jpg"), null);
  assert.equal(parseTile("/naip/15/1/-1.jpg"), null);
  assert.equal(parseTile("/other/15/1/1.jpg"), null);
});

test("converts the root tile to the full Web Mercator extent", () => {
  const bounds = tileBounds(0, 0, 0);
  assert.ok(Math.abs(bounds[0] + 20037508.342789244) < 0.000001);
  assert.ok(Math.abs(bounds[1] + 20037508.342789244) < 0.000001);
  assert.ok(Math.abs(bounds[2] - 20037508.342789244) < 0.000001);
  assert.ok(Math.abs(bounds[3] - 20037508.342789244) < 0.000001);
});

test("builds a constrained USDA exportImage request", () => {
  const url = upstreamUrl({ z: 15, x: 7000, y: 13000 });
  assert.equal(url.hostname, "apps.geo.fpac.usda.gov");
  assert.equal(url.searchParams.get("f"), "image");
  assert.equal(url.searchParams.get("bboxSR"), "3857");
  assert.equal(url.searchParams.get("imageSR"), "3857");
  assert.equal(url.searchParams.get("size"), "256,256");
  assert.equal(url.searchParams.get("format"), "jpg");
});
