package ir.hanzodev1375.ghostide.vibe;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

/**
 * Blocking HTTP client for the local Hermes API server ({@code /v1/runs} family).
 *
 * <p>Every method performs network I/O and must be called off the main thread. The client only
 * talks to a loopback address because the bearer key travels in cleartext; any other host is
 * rejected at construction. It is stateless and thread-safe.
 *
 * <p>Wire details were verified against Hermes 0.19.0 (no event ids, no steering) and against the
 * 0.22 source build (event ids, replay, steering, canonical approvals); both are supported.
 */
public final class HermesApiClient {

  /** The user's answer to an approval request. */
  public enum Decision {
    /** Allow this one command. */
    ONCE("once"),
    /** Allow this kind of command for the rest of the session. */
    SESSION("session"),
    /** Allow it permanently. */
    ALWAYS("always"),
    /** Refuse; the agent is told the command was blocked. */
    DENY("deny");

    private final String wireValue;

    Decision(String wireValue) {
      this.wireValue = wireValue;
    }

    /** The value Hermes expects in the {@code choice} field. */
    public String wireValue() {
      return wireValue;
    }
  }

  /** Result of {@link #startRun}. */
  public static final class StartedRun {
    private final String runId;
    private final String status;

    StartedRun(String runId, String status) {
      this.runId = runId;
      this.status = status;
    }

    @NonNull
    public String getRunId() {
      return runId;
    }

    /** Server status word, normally "started". */
    @NonNull
    public String getStatus() {
      return status;
    }
  }

  /** Snapshot of a run as returned by {@code GET /v1/runs/{id}}. */
  public static final class RunStatus {
    private final String runId;
    private final String status;
    private final String sessionId;
    private final String output;
    private final String error;
    private final VibeUsage usage;

    RunStatus(
        String runId,
        String status,
        String sessionId,
        String output,
        String error,
        VibeUsage usage) {
      this.runId = runId;
      this.status = status;
      this.sessionId = sessionId;
      this.output = output;
      this.error = error;
      this.usage = usage;
    }

    @NonNull
    public String getRunId() {
      return runId;
    }

    /** One of queued, running, waiting_for_approval, stopping, completed, failed, cancelled... */
    @NonNull
    public String getStatus() {
      return status;
    }

    @NonNull
    public String getSessionId() {
      return sessionId;
    }

    /** Final answer once completed, otherwise possibly a diagnostic text. */
    @NonNull
    public String getOutput() {
      return output;
    }

    @NonNull
    public String getError() {
      return error;
    }

    @NonNull
    public VibeUsage getUsage() {
      return usage;
    }

    /** True once the run can no longer change. */
    public boolean isTerminal() {
      return "completed".equals(status)
          || "failed".equals(status)
          || "cancelled".equals(status)
          || "interrupted".equals(status);
    }

    /** Converts a terminal status into the matching terminal event. */
    @NonNull
    VibeEvent toTerminalEvent() {
      switch (status) {
        case "completed":
          return new VibeEvent.RunCompleted(runId, -1, output, usage);
        case "cancelled":
          return new VibeEvent.RunCancelled(runId, -1);
        case "interrupted":
          return new VibeEvent.RunInterrupted(runId, -1, error);
        default:
          return new VibeEvent.RunFailed(runId, -1, error.isEmpty() ? output : error);
      }
    }
  }

  /** Feature flags and endpoints advertised by {@code GET /v1/capabilities}. */
  public static final class Capabilities {
    private final Set<String> features;
    private final Set<String> endpoints;

    Capabilities(Set<String> features, Set<String> endpoints) {
      this.features = Collections.unmodifiableSet(features);
      this.endpoints = Collections.unmodifiableSet(endpoints);
    }

    /** True if the named feature flag is advertised as enabled. */
    public boolean hasFeature(@NonNull String name) {
      return features.contains(name);
    }

    /** True if the named endpoint (for example {@code run_steer}) is advertised. */
    public boolean hasEndpoint(@NonNull String name) {
      return endpoints.contains(name);
    }

    /** True if runs can be steered while they execute (Hermes 0.22 and newer). */
    public boolean supportsSteer() {
      return hasEndpoint("run_steer");
    }

    /** True if the server can stream run events at all. */
    public boolean supportsRunEvents() {
      return hasEndpoint("run_events") || hasFeature("run_events_sse");
    }
  }

  /** Receives decoded events while {@link #streamEvents} runs; called on the calling thread. */
  public interface EventSink {
    /** Returns false to stop reading the stream. */
    boolean onEvent(@NonNull VibeEvent event);

    /**
     * Called for every keepalive comment the server sends while the run is quiet (every 10 to 30
     * seconds). A caller that wants to verify the run state independently of the stream can do it
     * here. Returns false to stop reading the stream.
     */
    default boolean onKeepAlive() {
      return true;
    }
  }

  /** How a call to {@link #streamEvents} ended without exception. */
  public static final class StreamEnd {
    private final long lastSeq;
    private final boolean terminalSeen;
    private final boolean stoppedBySink;
    private final boolean sawEvents;
    private final boolean droppedIncompleteEvent;

    StreamEnd(
        long lastSeq,
        boolean terminalSeen,
        boolean stoppedBySink,
        boolean sawEvents,
        boolean droppedIncompleteEvent) {
      this.lastSeq = lastSeq;
      this.terminalSeen = terminalSeen;
      this.stoppedBySink = stoppedBySink;
      this.sawEvents = sawEvents;
      this.droppedIncompleteEvent = droppedIncompleteEvent;
    }

    /** Highest sequence number delivered, or -1 if the server does not number events. */
    public long getLastSeq() {
      return lastSeq;
    }

    /** True if a terminal run event was delivered; the run is over. */
    public boolean isTerminalSeen() {
      return terminalSeen;
    }

    public boolean isStoppedBySink() {
      return stoppedBySink;
    }

    /** True if at least one event was delivered by this call. */
    public boolean sawEvents() {
      return sawEvents;
    }

    /** True if the connection ended in the middle of an event (it was discarded). */
    public boolean droppedIncompleteEvent() {
      return droppedIncompleteEvent;
    }
  }

  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
  private static final Pattern RUN_ID = Pattern.compile("[A-Za-z0-9_\\-]{1,128}");
  private static final int CONNECT_TIMEOUT_SECONDS = 5;
  private static final int CALL_TIMEOUT_SECONDS = 20;
  private static final int STREAM_READ_TIMEOUT_SECONDS = 75;
  private static final int HEALTH_TIMEOUT_SECONDS = 3;
  private static final long MAX_BODY_BYTES = 8L * 1024 * 1024;
  static final int MAX_HISTORY_CHARS = 400_000;

  private final HttpUrl baseUrl;
  private final String apiKey;
  private final OkHttpClient http;
  private final OkHttpClient streamHttp;
  private final OkHttpClient healthHttp;

  /**
   * Creates a client.
   *
   * @param baseUrl server root such as {@code http://127.0.0.1:8642}
   * @param apiKey the bearer key; never logged
   * @throws IllegalArgumentException if {@code baseUrl} is not an http URL on a loopback host
   */
  public HermesApiClient(@NonNull String baseUrl, @NonNull String apiKey) {
    this(baseUrl, apiKey, SharedHttp.CLIENT);
  }

  private static final class SharedHttp {
    static final OkHttpClient CLIENT = new OkHttpClient();
  }

  HermesApiClient(String baseUrl, String apiKey, OkHttpClient shared) {
    HttpUrl parsed = HttpUrl.parse(baseUrl);
    if (parsed == null || !isLoopback(parsed.host()) || !"http".equals(parsed.scheme())) {
      throw new IllegalArgumentException("Hermes API must be a loopback http URL");
    }
    if (apiKey.isEmpty()) {
      throw new IllegalArgumentException("API key is empty");
    }
    this.baseUrl = parsed;
    this.apiKey = apiKey;
    this.http =
        shared
            .newBuilder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build();
    this.streamHttp =
        http.newBuilder()
            .readTimeout(STREAM_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build();
    this.healthHttp =
        http.newBuilder()
            .connectTimeout(HEALTH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(HEALTH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(HEALTH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();
  }

  private static boolean isLoopback(String host) {
    return "127.0.0.1".equals(host) || "localhost".equals(host) || "::1".equals(host);
  }

  /** The server root this client talks to. */
  @NonNull
  public String getBaseUrl() {
    return baseUrl.toString();
  }

  /** Same as {@link #startRun(String, String, String, List)} without history. */
  @NonNull
  public StartedRun startRun(
      @NonNull String prompt, @Nullable String sessionId, @Nullable String instructions)
      throws HermesApiException {
    return startRun(prompt, sessionId, instructions, null);
  }

  /**
   * Starts an agent run and returns immediately with its id. Not retried automatically: a lost
   * response may still have created a run.
   *
   * @param prompt the user's message
   * @param sessionId continuation key sent as {@code session_id}; Hermes 0.22 and newer load the
   *     earlier conversation from it, {@code null} starts a one-off session
   * @param instructions extra system instructions for this run, may be {@code null}
   * @param history earlier turns of the conversation, oldest first, or {@code null}. Required to
   *     continue a conversation on Hermes 0.19, which ignores {@code session_id} for history. At
   *     most {@value #MAX_HISTORY_CHARS} characters are sent: the oldest turns are dropped, and a
   *     leading assistant turn is dropped too, so the list always starts with a user turn
   */
  @NonNull
  public StartedRun startRun(
      @NonNull String prompt,
      @Nullable String sessionId,
      @Nullable String instructions,
      @Nullable List<VibeTurn> history)
      throws HermesApiException {
    if (prompt.trim().isEmpty()) {
      throw new HermesApiException(
          HermesApiException.Kind.BAD_REQUEST, "prompt is empty", null);
    }
    JsonObject body = new JsonObject();
    body.addProperty("input", prompt);
    if (sessionId != null && !sessionId.isEmpty()) {
      body.addProperty("session_id", sessionId);
    }
    if (instructions != null && !instructions.isEmpty()) {
      body.addProperty("instructions", instructions);
    }
    JsonArray turns = historyJson(history);
    if (turns.size() > 0) {
      body.add("conversation_history", turns);
    }
    Request.Builder request = authorized("/v1/runs").post(RequestBody.create(body.toString(), JSON));
    request.header("Idempotency-Key", UUID.randomUUID().toString());
    JsonObject json = execJson(http, request.build(), null);
    String runId = VibeEventDecoder.text(json, "run_id", "");
    if (!RUN_ID.matcher(runId).matches()) {
      throw new HermesApiException(
          HermesApiException.Kind.PROTOCOL, "server returned no usable run id", null);
    }
    return new StartedRun(runId, VibeEventDecoder.text(json, "status", ""));
  }

  static JsonArray historyJson(@Nullable List<VibeTurn> history) {
    JsonArray turns = new JsonArray();
    if (history == null) {
      return turns;
    }
    int first = history.size();
    long chars = 0;
    while (first > 0) {
      long next = chars + history.get(first - 1).getText().length();
      if (next > MAX_HISTORY_CHARS) {
        break;
      }
      chars = next;
      first--;
    }
    while (first < history.size() && history.get(first).getRole() != VibeTurn.Role.USER) {
      first++;
    }
    for (int i = first; i < history.size(); i++) {
      VibeTurn turn = history.get(i);
      JsonObject entry = new JsonObject();
      entry.addProperty("role", turn.getRole().wireValue());
      entry.addProperty("content", turn.getText());
      turns.add(entry);
    }
    return turns;
  }

  /**
   * Reads the events of a run until the run ends, the sink stops, the connection ends or fails.
   * Returns normally when the server closes the stream; check {@link StreamEnd#isTerminalSeen()}.
   *
   * @param lastSeq highest sequence number already processed, or -1; sent as {@code
   *     Last-Event-ID} so builds with replay skip what was seen; events with a sequence number not
   *     above it are dropped locally as well
   * @param cancellation lets another thread abort the blocking read; may be {@code null}
   * @throws HermesApiException {@code NOT_FOUND} if the server no longer has an event stream for
   *     the run (builds without replay after the first connection), other kinds as documented
   */
  @NonNull
  public StreamEnd streamEvents(
      @NonNull String runId,
      long lastSeq,
      @NonNull EventSink sink,
      @Nullable HermesCancellation cancellation)
      throws HermesApiException {
    requireRunId(runId);
    Request.Builder builder =
        authorized("/v1/runs/" + runId + "/events")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache");
    if (lastSeq >= 0) {
      builder.header("Last-Event-ID", Long.toString(lastSeq));
    }
    okhttp3.Call call = streamHttp.newCall(builder.build());
    if (cancellation != null) {
      cancellation.attach(call);
    }
    try (Response response = call.execute()) {
      if (!response.isSuccessful()) {
        throw errorFor(response);
      }
      ResponseBody body = response.body();
      if (body == null) {
        throw new HermesApiException(HermesApiException.Kind.PROTOCOL, "empty event stream", null);
      }
      return readStream(runId, lastSeq, body.byteStream(), sink);
    } catch (HermesApiException e) {
      throw e;
    } catch (IOException e) {
      throw translate(e, cancellation);
    } finally {
      if (cancellation != null) {
        cancellation.detach(call);
      }
    }
  }

  StreamEnd readStream(String runId, long lastSeq, InputStream stream, EventSink sink)
      throws IOException {
    StreamCollector collector = new StreamCollector(runId, lastSeq, sink);
    SseParser.Result result = SseParser.parse(stream, collector);
    return new StreamEnd(
        collector.lastSeq,
        collector.terminalSeen,
        collector.stoppedBySink,
        collector.sawEvents,
        result.droppedIncompleteEvent);
  }

  private static final class StreamCollector implements SseParser.Listener {
    private final String runId;
    private final EventSink sink;
    private long lastSeq;
    private boolean terminalSeen;
    private boolean stoppedBySink;
    private boolean sawEvents;

    StreamCollector(String runId, long lastSeq, EventSink sink) {
      this.runId = runId;
      this.lastSeq = lastSeq;
      this.sink = sink;
    }

    @Override
    public boolean onEvent(String id, String event, String data) {
      VibeEvent decoded = VibeEventDecoder.decode(runId, id, data);
      if (decoded.seq() >= 0) {
        if (decoded.seq() <= lastSeq) {
          return true;
        }
        lastSeq = decoded.seq();
      }
      sawEvents = true;
      terminalSeen = decoded.isTerminal();
      boolean keepGoing = sink.onEvent(decoded);
      stoppedBySink = !keepGoing;
      return keepGoing && !terminalSeen;
    }

    @Override
    public boolean onComment(String text) {
      boolean keepGoing = sink.onKeepAlive();
      stoppedBySink = stoppedBySink || !keepGoing;
      return keepGoing;
    }
  }

  /** Reads the current status of a run. */
  @NonNull
  public RunStatus getRun(@NonNull String runId, @Nullable HermesCancellation cancellation)
      throws HermesApiException {
    requireRunId(runId);
    JsonObject json = execJson(http, authorized("/v1/runs/" + runId).get().build(), cancellation);
    return new RunStatus(
        VibeEventDecoder.text(json, "run_id", runId),
        VibeEventDecoder.text(json, "status", ""),
        VibeEventDecoder.text(json, "session_id", ""),
        VibeEventDecoder.text(json, "output", ""),
        VibeEventDecoder.text(json, "error", ""),
        VibeEventDecoder.usage(json));
  }

  /**
   * Answers a pending approval. When the request carried a prompt id the canonical body ({@code
   * request_id}, {@code execution_generation}, {@code choice}) is sent, otherwise the legacy
   * {@code {"choice": ...}}.
   *
   * @param request the event being answered; {@code null} answers the single pending legacy prompt
   * @throws HermesApiException {@code CONFLICT} if nothing is pending anymore or the prompt is stale
   */
  public void approve(
      @NonNull String runId, @Nullable VibeEvent.ApprovalRequested request, @NonNull Decision decision)
      throws HermesApiException {
    requireRunId(runId);
    if (request != null
        && !request.choices().isEmpty()
        && !request.choices().contains(decision.wireValue())) {
      throw new HermesApiException(
          HermesApiException.Kind.BAD_REQUEST, "decision is not offered for this request", null);
    }
    JsonObject body = new JsonObject();
    if (request != null && !request.promptId().isEmpty()) {
      body.addProperty("request_id", request.promptId());
      if (request.executionGeneration() >= 0) {
        body.addProperty("execution_generation", request.executionGeneration());
      }
    }
    body.addProperty("choice", decision.wireValue());
    execJson(
        http,
        authorized("/v1/runs/" + runId + "/approval").post(RequestBody.create(body.toString(), JSON)).build(),
        null);
  }

  /** Asks the agent to stop at the next safe point; the run then ends as cancelled. */
  public void stop(@NonNull String runId) throws HermesApiException {
    requireRunId(runId);
    execJson(
        http,
        authorized("/v1/runs/" + runId + "/stop").post(RequestBody.create("{}", JSON)).build(),
        null);
  }

  /**
   * Injects guidance into a running turn.
   *
   * @return whether the agent accepted the text
   * @throws HermesApiException {@code UNSUPPORTED} on Hermes builds without steering
   */
  public boolean steer(@NonNull String runId, @NonNull String text) throws HermesApiException {
    requireRunId(runId);
    JsonObject body = new JsonObject();
    body.addProperty("input", text);
    try {
      JsonObject json =
          execJson(
              http,
              authorized("/v1/runs/" + runId + "/steer").post(RequestBody.create(body.toString(), JSON)).build(),
              null);
      JsonElement accepted = json.get("accepted");
      return accepted != null && accepted.isJsonPrimitive() && accepted.getAsBoolean();
    } catch (HermesApiException e) {
      if (e.getKind() == HermesApiException.Kind.NOT_FOUND && e.getErrorCode().isEmpty()) {
        throw new HermesApiException(
            HermesApiException.Kind.UNSUPPORTED, "this Hermes build cannot steer runs", 404, null, e);
      }
      throw e;
    }
  }

  /** Lists the model ids advertised by {@code GET /v1/models} (the agent alias, not providers). */
  @NonNull
  public List<String> listModels() throws HermesApiException {
    JsonObject json = execJson(http, authorized("/v1/models").get().build(), null);
    JsonElement data = json.get("data");
    List<String> ids = new ArrayList<>();
    if (data != null && data.isJsonArray()) {
      for (JsonElement item : data.getAsJsonArray()) {
        if (item.isJsonObject()) {
          String id = VibeEventDecoder.text(item.getAsJsonObject(), "id", "");
          if (!id.isEmpty()) {
            ids.add(id);
          }
        }
      }
    }
    return Collections.unmodifiableList(ids);
  }

  /** Reads {@code GET /v1/capabilities}. */
  @NonNull
  public Capabilities capabilities() throws HermesApiException {
    JsonObject json = execJson(http, authorized("/v1/capabilities").get().build(), null);
    Set<String> features = new HashSet<>();
    JsonElement featureElement = json.get("features");
    if (featureElement != null && featureElement.isJsonObject()) {
      for (java.util.Map.Entry<String, JsonElement> entry : featureElement.getAsJsonObject().entrySet()) {
        JsonElement value = entry.getValue();
        if (value.isJsonPrimitive()
            && value.getAsJsonPrimitive().isBoolean()
            && value.getAsBoolean()) {
          features.add(entry.getKey());
        }
      }
    }
    Set<String> endpoints = new HashSet<>();
    JsonElement endpointElement = json.get("endpoints");
    if (endpointElement != null && endpointElement.isJsonObject()) {
      endpoints.addAll(endpointElement.getAsJsonObject().keySet());
    }
    return new Capabilities(features, endpoints);
  }

  /**
   * Calls the unauthenticated {@code GET /health}.
   *
   * @return the Hermes version string, possibly empty
   * @throws HermesApiException if the server is not up or does not answer like Hermes
   */
  @NonNull
  public String health(@Nullable HermesCancellation cancellation) throws HermesApiException {
    Request request = new Request.Builder().url(baseUrl.resolve("/health")).get().build();
    JsonObject json = execJson(healthHttp, request, cancellation);
    if (!"ok".equals(VibeEventDecoder.text(json, "status", ""))) {
      throw new HermesApiException(
          HermesApiException.Kind.PROTOCOL, "health endpoint did not report ok", null);
    }
    return VibeEventDecoder.text(json, "version", "");
  }

  private Request.Builder authorized(String path) {
    return new Request.Builder()
        .url(baseUrl.resolve(path))
        .header("Authorization", "Bearer " + apiKey);
  }

  private static void requireRunId(String runId) {
    if (!RUN_ID.matcher(runId).matches()) {
      throw new IllegalArgumentException("malformed run id");
    }
  }

  private JsonObject execJson(OkHttpClient client, Request request, HermesCancellation cancellation)
      throws HermesApiException {
    okhttp3.Call call = client.newCall(request);
    if (cancellation != null) {
      cancellation.attach(call);
    }
    try (Response response = call.execute()) {
      if (!response.isSuccessful()) {
        throw errorFor(response);
      }
      return parseObject(readBounded(response.body()));
    } catch (HermesApiException e) {
      throw e;
    } catch (IOException e) {
      throw translate(e, cancellation);
    } finally {
      if (cancellation != null) {
        cancellation.detach(call);
      }
    }
  }

  private static JsonObject parseObject(String text) throws HermesApiException {
    try {
      JsonElement parsed = JsonParser.parseString(text);
      if (parsed.isJsonObject()) {
        return parsed.getAsJsonObject();
      }
    } catch (JsonParseException e) {
      throw new HermesApiException(
          HermesApiException.Kind.PROTOCOL, "response is not valid JSON", e);
    }
    throw new HermesApiException(
        HermesApiException.Kind.PROTOCOL, "response is not a JSON object", null);
  }

  private static String readBounded(@Nullable ResponseBody body) throws IOException {
    if (body == null) {
      return "";
    }
    BufferedSource source = body.source();
    if (source.request(MAX_BODY_BYTES + 1)) {
      throw new IOException("response larger than " + MAX_BODY_BYTES + " bytes");
    }
    return source.readUtf8();
  }

  private static HermesApiException errorFor(Response response) {
    int status = response.code();
    String code = "";
    String message = "HTTP " + status;
    try {
      String text = readBounded(response.body());
      JsonElement parsed = JsonParser.parseString(text);
      if (parsed.isJsonObject() && parsed.getAsJsonObject().get("error") != null) {
        JsonElement error = parsed.getAsJsonObject().get("error");
        if (error.isJsonObject()) {
          code = VibeEventDecoder.text(error.getAsJsonObject(), "code", "");
          String detail = VibeEventDecoder.text(error.getAsJsonObject(), "message", "");
          if (!detail.isEmpty()) {
            message = "HTTP " + status + ": " + detail;
          }
        }
      }
    } catch (IOException | RuntimeException unreadableBody) {
      message = "HTTP " + status + " (unreadable error body)";
    }
    return new HermesApiException(
        HermesApiException.kindForStatus(status), message, status, code, null);
  }

  private static HermesApiException translate(IOException e, HermesCancellation cancellation) {
    if (cancellation != null && cancellation.isCancelled()) {
      return new HermesApiException(HermesApiException.Kind.CANCELLED, "cancelled", e);
    }
    if (e instanceof SocketTimeoutException || e instanceof InterruptedIOException) {
      return new HermesApiException(HermesApiException.Kind.TIMEOUT, "timed out", e);
    }
    return new HermesApiException(
        HermesApiException.Kind.NETWORK, "cannot reach the local Hermes server", e);
  }
}
