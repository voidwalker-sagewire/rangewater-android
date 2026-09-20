# RangeWater imagery gateway

This Cloudflare Worker exposes cached USDA NAIP imagery as validated XYZ tiles.
The deployed `workers.dev` endpoint is:

`https://rangewater-imagery-gateway.voidwalker.workers.dev/naip/{z}/{x}/{y}.jpg`

It accepts zoom levels 15–20, converts XYZ coordinates to EPSG:3857 bounds, requests
a 256×256 JPEG from USDA FPAC, and caches valid image responses. The Android app keeps
USGS imagery underneath the detail layer, so a gateway or upstream failure leaves a
visible lower-resolution map rather than a black screen.

## Test and deploy

1. Import the repository into Cloudflare Workers Builds from GitHub.
2. Set the root directory to `imagery-gateway`.
3. Deploy the Worker.
4. Confirm `https://rangewater-imagery-gateway.voidwalker.workers.dev/health`
   returns a JSON status of `ok`.
5. Rebuild Android and test zooms 15 through 20 on the target devices.

The worker contains no USDA credentials and only forwards fixed, validated parameters
to the approved NAIP ImageServer endpoint.
