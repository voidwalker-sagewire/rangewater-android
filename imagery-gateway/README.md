# RangeWater imagery gateway

This Cloudflare Worker exposes cached USDA NAIP imagery as validated XYZ tiles.
Cloudflare assigns the final `workers.dev` hostname during the first deployment:

`https://rangewater-imagery-gateway.<account-subdomain>.workers.dev/naip/{z}/{x}/{y}.jpg`

It accepts zoom levels 15–20, converts XYZ coordinates to EPSG:3857 bounds, requests
a 256×256 JPEG from USDA FPAC, and caches valid image responses. The Android app keeps
USGS imagery underneath the detail layer, so a gateway or upstream failure leaves a
visible lower-resolution map rather than a black screen.

## Test and deploy

1. Import the repository into Cloudflare Workers Builds from GitHub.
2. Set the root directory to `imagery-gateway`.
3. Deploy the Worker and copy the assigned `workers.dev` hostname.
4. Confirm `<workers.dev hostname>/health` returns a JSON status of `ok`.
5. Put that hostname into Android `MapConfig.kt`, rebuild, and test a zoom-15 tile.

The worker contains no USDA credentials and only forwards fixed, validated parameters
to the approved NAIP ImageServer endpoint.
