# Vibe Coding backend

The Vibe Coding feature lets an AI agent write code in the user's project, using a model provider
the user chooses. This document describes the **backend** in the package
`ir.hanzodev1375.ghostide.vibe` (module `app`): how the agent is installed and run, how the screen
talks to it, what is protected and what the limits are. The screen itself is documented where it
lives.

## What it is

The agent is [Hermes Agent](https://github.com/NousResearch/hermes-agent) by Nous Research, a
Python program. Ghost IDE does not contain it and does not re-implement it. On the user's request
the IDE installs a **pinned** release into the Debian rootfs that the terminal feature already
provides (proot), starts its local HTTP API server on `127.0.0.1`, and drives it over HTTP and
Server-Sent Events from Java. The agent runs shell commands and edits files in the project itself;
the IDE only shows what it does and asks the user before risky commands.

## Architecture

```
 UI (separate work)                       app module, package ...ghostide.vibe
 ------------------                       -----------------------------------------------------------
 settings screen  --- VibeProviderStore   (provider type, base URL, model: private prefs;
                                           API key: AES-GCM key in Android Keystore -> noBackup file)
 setup / status   --- HermesRuntime       (singleton: state machine, install, start/stop, startRun)
                          |  HermesLifecycle   pure state machine, single-flight, generations
                          |  HermesBackend     interface -> ProotHermesBackend (the only Android part)
                          |      ProotLauncher + ProotCommand   proot command line (same flags as the terminal)
                          |      HermesInstallSpec + InstallProgressParser   apt + venv + pip, progress
                          |      HermesConfigWriter   config.yaml (no secrets), approvals = manual
                          |      ProotHermesServer    child process, masked ring-buffer log, exit callback
 chat screen      --- HermesRunSession    (one run: events, approve, stop, steer; reconnect; batching)
                          |  EventBatcher      coalesces text chunks, delivers on the main thread
                          |  HermesApiClient   OkHttp, bearer key, loopback only, precise exceptions
                          |      SseParser + VibeEventDecoder   -> VibeEvent (sealed, immutable)
 login screen     --- ChatGptAccountLogin (device-code login driven through `hermes auth add`)
```

Data flow of one prompt:

1. `HermesRuntime.startRun(prompt, sessionId, instructions, history, listener)` creates a
   `HermesRunSession` and returns at once.
2. A background thread sends `POST /v1/runs` and gets a `run_id`.
3. The same thread reads `GET /v1/runs/{id}/events` (SSE). Each frame becomes a `VibeEvent`.
4. `EventBatcher` merges consecutive text chunks and posts at most about 20 batches per second to the
   main thread: `Listener.onEvents(List<VibeEvent>)`.
5. When the agent wants to run a flagged command, an `ApprovalRequested` event arrives and the run
   waits. The UI shows the command and calls `session.approve(request, decision)`
   (`POST /v1/runs/{id}/approval`). No answer for 10 minutes means Hermes denies the command.
6. A terminal event (`RunCompleted`, `RunFailed`, `RunCancelled`, `RunInterrupted`) ends the stream
   and `Listener.onFinished(outcome, cause)` is the last callback.

Process lifetime: `HermesRuntime` lives in the application scope (not in an Activity). The server
process lives until `stop()`, a crash, or the system killing the app. States:

```
NOT_INSTALLED --install--> INSTALLING --ok--> INSTALLED --start--> STARTING --healthy--> RUNNING
FAILED can follow INSTALLING / STARTING / RUNNING (process died); stop() from STARTING/RUNNING -> INSTALLED
```

`install()` and `start()` are single-flight. `start(otherFolder)` restarts the server because Hermes
has one working directory per server process (there is no per-run directory in its API).

## Public API for the screen

Everything here may be called from the main thread unless noted; callbacks arrive on the main thread.

| Class | Use |
|---|---|
| `VibeProviderType` | `ANTHROPIC`, `OPENAI_COMPATIBLE`, `CHATGPT_ACCOUNT`; `needsSecret()` |
| `VibeProviderConfig.create(type, baseUrl, model)` | validates; throws `VibeConfigException` with `getProblem()` |
| `VibeProviderStore.get(ctx)` | `load()`, `save(config, secret)` (blocking), `hasSecret(type)` (blocking), `clearSecret`, `clear` |
| `HermesRuntime.get(ctx)` | `getSnapshot()`, `addListener/removeListener`, `isRootfsInstalled()`, `install()`, `cancelInstall()`, `start(File)`, `stop()`, `uninstall()`, `startRun(...)`, `supportsSteering()`, `beginChatGptLogin(listener)` |
| `HermesRuntime.Snapshot` | `getState()`, `isInstalled()`, `getInstallStep()`, `getInstallPercent()`, `getFailureReason()`, `getFailureMessage()`, `getLogTail()`, `getWorkspace()` |
| `HermesRunSession` | `approve(request, decision)`, `stop()`, `steer(text)`, `close()`, `getRunId()`, `getSessionId()`, `newSessionId()` |
| `VibeEvent` | sealed interface of records: `MessageDelta`, `MessageInterim`, `Reasoning`, `ToolStarted`, `ToolCompleted`, `ApprovalRequested`, `ApprovalResolved`, `RunSteered`, `Subagent`, `RunQueued`, `ReplayTruncated`, `RunCompleted`, `RunFailed`, `RunCancelled`, `RunInterrupted`, `Unknown`, `Malformed` |
| `VibeTurn` | earlier messages of a conversation, passed as history |
| `ChatGptAccountLogin` | `Listener.onCodeReady(url, code)`, `onSucceeded()`, `onFailed(failure, detail)`; `cancel()`; `TERMINAL_COMMAND` |
| `HermesApiClient` | low level, blocking, background thread only; normally not needed |

Rules the screen must follow:

* Install needs the Debian rootfs (`isRootfsInstalled()`); otherwise `install()` returns false and the
  state is `FAILED/ROOTFS_MISSING`. Install and every provider call need internet; nothing else in the
  IDE does.
* Save the provider (`VibeProviderStore.save`, off the main thread) before `start(folder)`. Missing
  configuration gives `FAILED/NOT_CONFIGURED`.
* The project folder must be under shared storage (`/storage/emulated/0`) or the app's private data
  directory, the two places proot exposes. Otherwise `FAILED/INVALID_WORKSPACE`.
* `RunCompleted.output()` is the authoritative final answer. Streamed text can be incomplete after a
  reconnect on Hermes 0.19.
* Hermes 0.19 does not remember a conversation by id: pass the transcript you keep as `history`
  (`VibeTurn`). Newer builds load it by `sessionId` and treat explicit history as authoritative, so
  passing it is always right.
* Remove listeners in `onStop`/`onDestroy`; `HermesRuntime` holds them strongly. Call
  `HermesRunSession.close()` when the screen goes away (this does not stop the run; call `stop()`
  first for that).
* A foreground service is **not** started here. Android may kill the app (and with it the server)
  when it is in the background; the screen should show `FAILED/PROCESS_EXITED` and offer to restart.
  If needed, keep the process alive with the existing `TerminalSessionService` mechanism.

## The screens (package `ghostide.vibe.ui`)

* `VibeCodingActivity` (not exported) is opened for a project folder with
  `VibeCodingActivity.createIntent(context, folder)`; the file manager's tool menu has an entry
  for the folder it shows. The editor has no project-root concept, so it has no entry.
  `VibeProviderActivity` (not exported, window marked secure) chooses Anthropic, an
  OpenAI-compatible endpoint or a ChatGPT account.
* `VibeChatViewModel` owns the transcript, the active run and the readiness of the agent. The
  screens never touch `HermesRuntime` directly: they use the small `VibeGateway` interface
  (`RuntimeVibeGateway` is the real one), which is the only test seam.
* **Leaving the screen.** Back while a run is active asks first. Confirming stops the run, closes the
  session and stops the local server (`onCleared`); rotation changes nothing and keeps the run.
  While the screen is only stopped (another app in front) the run keeps updating the transcript in
  the view model, which holds no Activity or View. Approvals that arrive meanwhile wait for the
  user (Hermes denies them after 10 minutes); no notification is posted.
* **Auto start.** Once a provider is saved and Hermes is installed the screen starts the server for
  the project by itself, unless the user stopped it; a failure is never restarted automatically.
* **Transcript.** One JSON file per project in `noBackupFilesDir/vibe/chats`, at most 300 rows and
  1.5 MB, written atomically off the main thread, never containing keys. A run cut short by a killed
  process is marked when the file is read again. Every prompt carries the transcript as `history`.
* **Files.** Events do not name the files a tool touched, so none are listed. The activity returns
  `RESULT_OK` when a tool finished or a run ended, and the file manager refreshes its list; an open
  editor already notices disk changes when it resumes.
* Steering is not wired: the pinned Hermes cannot steer, and `RunSteered` only shows as a notice.

## Security model

* **Loopback only, per-install key.** The server binds `127.0.0.1` on a random free port. The bearer
  key is 256 random bits created on first use and stored like the provider key. `HermesApiClient`
  refuses any non-loopback or https URL, so the key can never be sent elsewhere.
* **Secrets never touch argv, config files or logs.** The Hermes `config.yaml` has no key. The
  launching `bash` reads `NAME=value` lines from its standard input, exports them, and `exec`s
  Hermes with stdin closed. Hermes resolves them from the environment (`API_SERVER_KEY`,
  `ANTHROPIC_API_KEY`, or the named provider's `key_env: GHOST_VIBE_API_KEY`). Verified: no `.env` is
  needed. Server output is kept in a ring buffer with every known secret masked.
* **Keystore.** Provider keys and the local API key are encrypted with an AES-256-GCM key that lives in
  the Android Keystore (not extractable) and stored in `noBackupFilesDir/vibe/secrets.json`
  (owner-only). An entry that cannot be decrypted is dropped and the user is asked again.
  Hermes itself stores the ChatGPT tokens it obtains in `auth.json` inside its private home in the
  app's data directory.
* **Approvals stay on.** `approvals.mode: manual` is written explicitly (Hermes' documented default is
  `smart`, where a second model may approve commands). Unanswered approvals are denied after 10
  minutes. The UI never auto-approves; `session`/`always` choices exist in the API but offering them
  is a UI decision.
* **No new exported component and no new permission.** Nothing is added to the manifest. Cleartext
  traffic to loopback is already allowed by the existing application setting.
* **Sandbox.** The agent runs inside the proot rootfs, but it can read and write everything the
  rootfs exposes: the project, shared storage (`/sdcard`) and the app's private data directory
  (`/ghostide`). It is not a security boundary against a malicious model; approvals are.
* **Ownership of orphans.** `hermes gateway run --replace` is used so a server left by a killed app
  process (same private Hermes home) is taken over instead of blocking the start.
* The ChatGPT login only ever opens an `https://auth.openai.com/` URL, taken from a strict pattern
  match on the process output.

## Providers

| Type | Hermes configuration | Secret |
|---|---|---|
| Anthropic | `model.provider: anthropic`, optional `model.base_url` | `ANTHROPIC_API_KEY` |
| OpenAI-compatible | named provider `ghost-vibe` with `api` and `key_env` | `GHOST_VIBE_API_KEY` |
| ChatGPT account | `model.provider: openai-codex` | none; `hermes auth add openai-codex` |

ChatGPT account login: `HermesRuntime.beginChatGptLogin(listener)` runs `hermes auth add
openai-codex --type oauth --no-browser`, which prints a verification URL and a code. The screen
opens the URL and shows the code; the helper waits for the user (Hermes allows 15 minutes). The
output format of that command is not a stable API; if it changes the helper fails with
`CODE_NOT_FOUND` and the user can run `ChatGptAccountLogin.TERMINAL_COMMAND` in a Debian terminal.

## How to enable (for the screen)

1. Make sure the Debian rootfs is installed (terminal feature).
2. Let the user pick provider, base URL, model and key; `VibeProviderConfig.create` then
   `VibeProviderStore.save` on a background thread. For ChatGPT, run the login.
3. `HermesRuntime.install()` and show `Snapshot.getInstallStep()/getInstallPercent()`.
4. `HermesRuntime.start(projectFolder)`; wait for `RUNNING`.
5. `startRun(...)`.

What gets installed (`HermesInstallSpec`): `apt install python3 python3-venv ca-certificates git
ripgrep`, a venv at `/opt/ghost-hermes/venv`, and `pip install --only-binary :all:
hermes-agent==0.19.0 aiohttp==3.14.1 anthropic==0.87.0`. Hermes state lives in
`/root/.ghost-hermes`. Rough footprint: 350-450 MB of disk, 80-100 MB download.

## Limits and known risks

* **Nothing here was run on a device or under real proot.** The state machine, client, parsers and
  event model were tested on the JVM, and the client, session, config and launch script were run
  against real Hermes processes on x86-64 Linux (see "Tests"). Install through apt/pip inside proot,
  proot argument handling, memory use, start time and Android process killing are unverified.
* **Memory and kills.** The Python server plus tool processes can be heavy. Android 12+ may
  terminate the process tree (phantom process killer) or the low-memory killer may remove it; the
  state becomes `FAILED/PROCESS_EXITED` with a log tail.
* **Start time.** Expect tens of seconds to minutes on a phone (180 s health timeout).
* **Hermes 0.19 limitations** (the pinned build): no event ids and no replay, a dropped event
  connection cannot be resumed losslessly (the session falls back to polling the run status and
  detects a stalled stream through the status), no steering, no server-side conversation memory by
  session id, approval events carry no prompt id.
* **Orphans.** If the app process dies without `stop()`, the proot child may live on until the next
  start replaces it (not verified on Android).
* **One working directory per server.** Switching projects restarts the server. Hermes'
  `config.yaml` is regenerated at every start (manual edits are lost); its state, sessions and
  `auth.json` are kept.
* **Network.** Installation and all providers need internet. Offline, install fails with
  `INSTALL_FAILED` (log tail shows the apt/pip error) and runs fail with `RunFailed`.
* **Cost and data.** The agent sends project content to the chosen provider; usage is billed by it.

## License

Hermes Agent is MIT licensed (copyright Nous Research). It is **not bundled** in the APK or the
repository: it is downloaded from PyPI into the user's rootfs at runtime, together with its own
dependencies and their licenses. The IDE only talks to its documented HTTP API. Keep the upstream
license text available if the install flow ever shows third-party notices.

## Tests

Unit tests (JVM) were written for: SSE parser (chunk splits, CR/LF/CRLF, comments, multi-line data,
malformed lines, BOM, UTF-8 split across reads, unexpected end of stream, oversized lines),
event decoding with payloads captured from real Hermes 0.19.0 and a 0.22 source build, the HTTP
client against a scripted local server (every error kind, cancellation, resume, history),
`HermesRunSession` (batching, approval, reconnect, polling fallback, stall detection, close),
`HermesLifecycle` (single-flight, cancel, restart, crash, timeout, listeners), configuration and
secret store, proot command line, install progress, device-code parser, process wrapper. A further
test starts real Hermes servers with the production launch script and configuration against a
local mock model provider. Their sources are not part of the repository build; the scaffolding
needed to run them (JUnit dependency, `returnDefaultValues`) was removed before commit.
