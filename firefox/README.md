# Firefox Custom Extension Signing Key Patch

This directory contains a sparse clone of Mozilla Firefox source (focused on `security/manager/ssl`) and a patch that replaces Mozilla's built-in extension signing root certificates with a custom key hierarchy.

## Directory layout

| Path | Description |
|------|-------------|
| `firefox-src/` | Sparse git clone of [mozilla-firefox/firefox](https://github.com/mozilla-firefox/firefox) with custom certificates applied |
| `custom-signing-keys/` | Generated private keys and PEM certificates (keep private keys secure) |
| `replace-extension-signing-keys.patch` | Unified diff against upstream `main` |

## Certificates replaced

Production (add-on verification):

- `security/manager/ssl/addons-public.pem` — root CA
- `security/manager/ssl/addons-public-intermediate.pem` — intermediate (post-2018 CN)
- `security/manager/ssl/addons-public-2018-intermediate.pem` — legacy intermediate (pre-2018 CN)
- `security/manager/ssl/content-signature-prod.pem` — content signature root (same key as addons-public)

Staging:

- `security/manager/ssl/addons-stage.pem`
- `security/manager/ssl/addons-stage-intermediate.pem`
- `security/manager/ssl/content-signature-stage.pem`

`content-signature-local.pem` is unchanged (local dev ECDSA cert).

## Apply the patch to a full Firefox tree

```bash
git clone --depth 1 https://github.com/mozilla-firefox/firefox.git firefox
cd firefox
git apply /path/to/replace-extension-signing-keys.patch
```

Or copy certificate PEM files from `custom-signing-keys/` into `security/manager/ssl/` using the mapping in `custom-signing-keys/README.md`.

## Build notes

After applying the patch, build Firefox normally (`./mach bootstrap` then `./mach build`). The build system converts PEM files to C headers via `gen_cert_header.py` in `moz.build`.

## Signing extensions

Extensions signed by Mozilla AMO will **not** install in this build. You must sign extensions with the custom intermediate private keys in `custom-signing-keys/`.

See `custom-signing-keys/README.md` for key inventory and signing guidance.

## Security warning

Private keys in `custom-signing-keys/` grant the ability to sign installable extensions. Do not distribute private keys publicly. For production use, protect keys with hardware security modules or equivalent.
