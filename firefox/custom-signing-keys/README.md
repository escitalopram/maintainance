# Custom extension signing keys

Generated for replacing Mozilla's built-in AMO / CAS signing trust anchors.

## Key inventory

| File | Maps to Firefox source | Role |
|------|------------------------|------|
| `production-root.key` / `production-root.pem` | `addons-public.pem`, `content-signature-prod.pem` | Production root CA |
| `production-intermediate-2018.key` / `.pem` | `addons-public-intermediate.pem` | Production intermediate (`signingca1.addons.example.org`) |
| `production-intermediate-legacy.key` / `.pem` | `addons-public-2018-intermediate.pem` | Legacy production intermediate |
| `staging-root.key` / `staging-root.pem` | `addons-stage.pem`, `content-signature-stage.pem` | Staging root CA |
| `staging-intermediate.key` / `.pem` | `addons-stage-intermediate.pem` | Staging intermediate |

## Regenerate keys

```bash
./generate_keys.sh
```

Then re-copy PEM files into `firefox-src/security/manager/ssl/` or re-apply the patch workflow.

## Verify certificates

```bash
openssl x509 -in production-root.pem -text -noout
openssl verify -CAfile production-root.pem production-intermediate-2018.pem
```

## Signing extensions (overview)

Mozilla uses Autograph / internal AMO signing infrastructure in production. For a custom Firefox build you typically:

1. Sign XPIs with a leaf certificate issued under your intermediate CA, **or**
2. Use Mozilla's open-source signing tooling if you adapt it to your CA hierarchy.

A minimal test workflow uses `openssl` to create a leaf cert and sign a manifest, but full XPI COSE/PKCS7 signing matches Mozilla's `autograph` format. For development, you can also set in `about:config`:

- `xpinstall.signatures.required` = `false`
- `extensions.langpacks.signatures.required` = `false`

That bypasses signature verification entirely (not recommended for production).
