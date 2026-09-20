const USDA_EXPORT_ENDPOINT =
  "https://apps.geo.fpac.usda.gov/geo-imagery/rest/services/naip/conus_naip/ImageServer/exportImage";
const WEB_MERCATOR_HALF_WORLD = 20037508.342789244;
const MIN_ZOOM = 15;
const MAX_ZOOM = 20;
const TILE_SIZE = 256;

export function tileBounds(z, x, y) {
  const tileSpan = (WEB_MERCATOR_HALF_WORLD * 2) / 2 ** z;
  const minX = -WEB_MERCATOR_HALF_WORLD + x * tileSpan;
  const maxX = minX + tileSpan;
  const maxY = WEB_MERCATOR_HALF_WORLD - y * tileSpan;
  const minY = maxY - tileSpan;
  return [minX, minY, maxX, maxY];
}

export function parseTile(pathname) {
  const match = pathname.match(/^\/naip\/(\d+)\/(\d+)\/(\d+)\.jpg$/);
  if (!match) return null;

  const [, zText, xText, yText] = match;
  const z = Number(zText);
  const x = Number(xText);
  const y = Number(yText);
  const tileCount = 2 ** z;
  if (
    !Number.isSafeInteger(z) ||
    !Number.isSafeInteger(x) ||
    !Number.isSafeInteger(y) ||
    z < MIN_ZOOM ||
    z > MAX_ZOOM ||
    x < 0 ||
    y < 0 ||
    x >= tileCount ||
    y >= tileCount
  ) {
    return null;
  }
  return { z, x, y };
}

export function upstreamUrl(tile) {
  const bbox = tileBounds(tile.z, tile.x, tile.y).join(",");
  const url = new URL(USDA_EXPORT_ENDPOINT);
  url.search = new URLSearchParams({
    f: "image",
    bbox,
    bboxSR: "3857",
    imageSR: "3857",
    size: `${TILE_SIZE},${TILE_SIZE}`,
    format: "jpg"
  }).toString();
  return url;
}

function json(body, status, extraHeaders = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      ...extraHeaders
    }
  });
}

async function fetchUpstream(url) {
  let lastError;
  for (let attempt = 0; attempt < 2; attempt += 1) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 15000);
    try {
      const response = await fetch(url, {
        headers: {
          accept: "image/jpeg,image/*",
          "user-agent": "RangeWater-Imagery-Gateway/1.0"
        },
        signal: controller.signal
      });
      if (response.ok || response.status < 500 || attempt === 1) return response;
      await response.body?.cancel();
    } catch (error) {
      lastError = error;
      if (attempt === 1) throw error;
    } finally {
      clearTimeout(timeout);
    }
  }
  throw lastError ?? new Error("USDA imagery request failed");
}

async function handleTile(request, tile, context) {
  const cache = caches.default;
  const cacheKey = new Request(new URL(request.url).origin + new URL(request.url).pathname);
  const cached = await cache.match(cacheKey);
  if (cached) return cached;

  let upstream;
  try {
    upstream = await fetchUpstream(upstreamUrl(tile));
  } catch (error) {
    return json(
      { error: "USDA imagery is temporarily unavailable" },
      504,
      { "retry-after": "30" }
    );
  }

  const contentType = upstream.headers.get("content-type") ?? "";
  if (!upstream.ok || !contentType.toLowerCase().startsWith("image/")) {
    await upstream.body?.cancel();
    return json(
      { error: "USDA imagery returned an invalid response" },
      502,
      { "retry-after": "30" }
    );
  }

  const response = new Response(upstream.body, {
    status: 200,
    headers: {
      "content-type": contentType,
      "cache-control": "public, max-age=86400, s-maxage=2592000, stale-while-revalidate=86400",
      "access-control-allow-origin": "*",
      "x-content-type-options": "nosniff",
      "x-rangewater-imagery": "USDA-NAIP"
    }
  });
  context.waitUntil(cache.put(cacheKey, response.clone()));
  return response;
}

export default {
  async fetch(request, environment, context) {
    const url = new URL(request.url);
    if (request.method !== "GET" && request.method !== "HEAD") {
      return json({ error: "Method not allowed" }, 405, { allow: "GET, HEAD" });
    }
    if (url.pathname === "/health") {
      return json({ service: "rangewater-imagery-gateway", status: "ok" }, 200);
    }

    const tile = parseTile(url.pathname);
    if (!tile) {
      return json({ error: "Not found" }, 404);
    }
    return handleTile(request, tile, context);
  }
};
