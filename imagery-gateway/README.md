# RangeWater imagery gateway

This Cloudflare Worker exposes cached USDA NAIP imagery as validated XYZ tiles:

`https://imagery.sagewire.dev/naip/{z}/{x}/{y}.jpg`

It accepts zoom levels 15–20, converts XYZ coordinates to EPSG:3857 bounds, requests
a 256×256 JPEG from USDA FPAC, and caches valid image responses. The Android app keeps
USGS imagery underneath the detail layer, so a gateway or upstream failure leaves a
visible lower-resolution map rather than a black screen.

## Test and deploy

1. Run `npm install` and `npm test` in this directory.
2. Authenticate Wrangler with the Cloudflare account that owns `sagewire.dev`.
3. Run `npm run deploy`.
4. Confirm `https://imagery.sagewire.dev/health` returns a JSON status of `ok`.
5. Test a zoom-15 tile before installing the Android acceptance build.

The worker contains no USDA credentials and only forwards fixed, validated parameters
to the approved NAIP ImageServer endpoint.
