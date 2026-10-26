# Working offline

Everything below uses only what is already on the machine. Do the online steps once, while a connection is available.

## Building without a connection

1. Online, once: `./gradlew :app:assembleDebug` so every dependency lands in `~/.gradle/caches`.
2. Offline afterwards: `./gradlew --offline :app:assembleDebug`. If a dependency is missing from the cache, Gradle fails with the artifact name instead of hanging; run step 1 again when you are back online.
3. Use JDK 17. Set `sdk.dir` in the git-ignored `local.properties`.
4. Release builds need `GHOST_STORE_PASSWORD`, `GHOST_KEY_ALIAS`, `GHOST_KEY_PASSWORD` (environment, Gradle properties or the git-ignored `keystore.properties`). They are never fetched from the network.

## Protecting local work

- Commit finished work to a local branch often (`git commit`); nothing here needs the network until `git push`.
- Never `git reset --hard`, `git clean -fd` or force-push to recover from a failed sync. Create a backup branch first (`git branch backup/<date>`).
- Installed plugins live in the app's plugin directory and are loaded from disk; the plugin manager works without a connection. Only the store (browse, install, update) needs the network and reports the failure.

## What works without internet

- Editor, file manager, project tree, git (local operations), terminal (shell and an already-installed Debian rootfs), installed plugins, installed themes, Post (requests to local/LAN hosts).
- The Debian rootfs download, the plugin and theme store, update checks and the hosted AI providers need a connection. They fail with an error message; a retry is safe.

## AI

The `aimodel` module speaks the OpenAI-compatible protocol to the endpoint configured per provider. The hosted providers (Claude, ChatGPT, DeepSeek, Gemini, OpenRouter) need the internet. `OPENCODE_BASE_URL` points to `http://127.0.0.1:4096`, a server you run yourself on the device; the IDE does not ship a local model, so AI is only offline if you run such a server.
