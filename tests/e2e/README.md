# Quickstart E2E

Maestro flows that drive the Quickstart sample through real authentication against a real
ThunderID server on an Android emulator. The flows live in [`flows/`](./flows); the
scripts alongside them get a server into the right state to run against.

## Running locally

```bash
# Everything: start a server, provision it, build and install the sample, run the flows
./run-e2e.sh

# Iterate faster once the server is up and the sample is installed
./run-e2e.sh --skip-server --skip-build

# Run a single flow
./run-e2e.sh flows/signin.yaml
```

On Windows, use the PowerShell twin, which takes the same stages:

```powershell
.\run-e2e.ps1
.\run-e2e.ps1 -SkipServer -SkipBuild
.\run-e2e.ps1 flows\signin.yaml
```

Every stage is idempotent. A server that is already serving on `:8090` is reused rather than
restarted, and provisioning re-applies cleanly over an existing application and user.

## What gets provisioned

| Resource | Value |
|---|---|
| Application | `019e5b10-1001-7a2b-9c3d-4e5f60718293` (`Mobile Quickstart E2E App`) |
| Test user | `e2e_mobile_user` / `TestPassword@123` |

The application is declared in
[`samples/quickstart/thunderid-config/thunderid-config.yaml`](../../samples/quickstart/thunderid-config/thunderid-config.yaml),
alongside the sample it configures. The sign-up flow
registers an additional throwaway user per run, named `e2e_signup_<timestamp>`.

## Things worth knowing

**The application must be `type: mobile` with `attestation.devMode: true`.** A mobile application
normally has to prove its binary identity through platform attestation before it can initiate a
flow directly, and `/flow/execute` rejects it with `FES-1016` otherwise. Play Integrity needs real
Play Services and a registered signing certificate, so the check cannot be satisfied on the
emulator these flows run on. `devMode` is test-only and must never be enabled on a real tenant.

**`POST /import` silently drops the `attestation` block.** It reports the import as successful and
then stores `attestation: null`, which is why `run-e2e.sh` re-applies it over
`PUT /applications/{id}`. Once the import path preserves it, that step can be removed.

**Sign-up does not sign the user in.** The registration flow completes without issuing an
assertion, so the app returns to the landing screen with the account created but no session. The
sign-up flow therefore signs in afterwards with the credentials it just registered, which is also
what proves the new account actually works.

**Every flow starts from a known state.** Tokens live in `EncryptedSharedPreferences`, which
`clearState: true` does clear, but a flow that fails part way through can still leave the app
mid-session. Each flow therefore starts with the `ensure-signed-out` subflow rather than assuming
a clean device.

**The emulator reaches the host at `10.0.2.2`, not `localhost`.** The server listens on the host
loopback, so the sample's base URL is `https://10.0.2.2:8090`. The debug build sets
`allowInsecureConnections`, which is what lets it accept the server's self-signed certificate.

**Maestro only sees `testTag` because the SDK opts in.** Compose keeps test tags inside its own
semantics tree; the SDK applies `testTagsAsResourceId` at the root of its flow-rendering
components so they surface as resource IDs in the platform accessibility tree.

**`npx thunderid` cannot be used in CI.** It renders an interactive TUI and aborts with
`bubbletea: could not open TTY` whenever stdout is not a terminal. `run-e2e.sh` downloads the
release directly and calls the distribution's own `setup.sh` and `start.sh`, which take the same
arguments non-interactively.
