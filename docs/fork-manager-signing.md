# Fork Manager signing

Both normal and spoofed release APKs use the same persistent fork certificate.
The kernel accepts that certificate in addition to the original upstream
certificate. APK parsing, package constraints and Manager UID authentication
remain in place. An arbitrary APK or the old debug-signed CI APK is not trusted.

The public pin is in `manager/signing-certificate.json` and
`kernel/manager/fork_manager_certificate.h`. CI verifies the keystore against it
before building, then verifies the APK signature and pin before uploading.
Missing signing secrets cause release builds to fail, not fall back to debug.
PR builds never receive the signing key and do not publish signed artifacts.

The four encrypted Actions secrets are `FORK_MANAGER_KEYSTORE` (base64 JKS),
`FORK_MANAGER_STORE_PASSWORD`, `FORK_MANAGER_KEY_ALIAS` and
`FORK_MANAGER_KEY_PASSWORD`. Keep an offline protected backup of the key.
Never commit the keystore or passwords. Normal APKs can be updated only by APKs
with the same certificate; an old debug-signed install needs to be removed first.

Install a freshly built kernel containing this pin before using the new fork
APK. Updating only the Manager cannot change what an old kernel trusts. For the
Action-Build fork use a fresh build, not a saved FAST base from before this fix.
Upstream signed Managers remain compatible, subject to their real UAPI version.
Only one Manager UID is active at a time; do not install competing Managers to
test recognition. Spoof changes the package ID, not the authentication rules.
