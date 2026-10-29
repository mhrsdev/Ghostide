package ir.hanzodev1375.ghostide.vibe;

/** The kinds of model provider the Vibe Coding agent can be connected to. */
public enum VibeProviderType {
  /** Anthropic Messages API, authenticated with an API key. */
  ANTHROPIC(true),
  /** Any server that speaks the OpenAI chat-completions protocol, authenticated with an API key. */
  OPENAI_COMPATIBLE(true),
  /** A ChatGPT account signed in through the OpenAI device-code login; no API key is stored here. */
  CHATGPT_ACCOUNT(false);

  private final boolean needsSecret;

  VibeProviderType(boolean needsSecret) {
    this.needsSecret = needsSecret;
  }

  /** True when the provider is authenticated with an API key kept in the secret store. */
  public boolean needsSecret() {
    return needsSecret;
  }
}
