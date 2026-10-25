package ir.hanzodev1375.ghostide.postman.network;

import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import ir.hanzodev1375.ghostide.postman.model.KeyValueItem;
import ir.hanzodev1375.ghostide.postman.model.RequestSnapshot;
import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * The actual HTTP request engine, built on top of OkHttp. This is the one class that turns a
 * {@link RequestSnapshot} into a real network call and hands the result back on the main thread.
 *
 * <p>One engine runs at most one call at a time: {@link #cancel()} aborts it and {@link
 * #shutdown()} releases the client's threads and pooled connections when the owner is destroyed.
 */
public class HttpEngine {

  /** Responses larger than this are truncated in memory (the real size is still reported). */
  public static final long MAX_DISPLAY_BYTES = 2L * 1024 * 1024;

  private static final int READ_CHUNK_BYTES = 16 * 1024;

  private final OkHttpClient client;
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private final int timeoutSeconds;
  private final boolean sslVerificationDisabled;
  private volatile Call currentCall;

  public HttpEngine(int timeoutSeconds, boolean disableSslVerification) {
    this.timeoutSeconds = timeoutSeconds;
    this.sslVerificationDisabled = disableSslVerification;
    OkHttpClient.Builder builder =
        new OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true);

    if (disableSslVerification) {
      applyInsecureTrustManager(builder);
    }

    this.client = builder.build();
  }

  /** Why a request produced no usable response. */
  public enum ErrorKind {
    NONE,
    INVALID_URL,
    INVALID_REQUEST,
    CANCELLED,
    TIMEOUT,
    UNKNOWN_HOST,
    CONNECTION_REFUSED,
    TLS,
    BODY_UNREADABLE,
    NETWORK
  }

  /** Result of one request/response round trip (or a failure before a response ever arrived). */
  public static class HttpResult {
    public boolean success;
    public int statusCode;
    public String statusMessage = "";
    public List<String[]> headers = new ArrayList<>();
    public String body = "";
    public long timeMs;
    public long sizeBytes;
    public boolean bodyTruncated;
    public String errorMessage;
    public ErrorKind errorKind = ErrorKind.NONE;
    public String host = "";
  }

  public interface Callback {
    void onResult(HttpResult result);
  }

  /** @return true when this engine was built with the given settings and can be reused. */
  public boolean matches(int timeoutSeconds, boolean disableSslVerification) {
    return this.timeoutSeconds == timeoutSeconds
        && this.sslVerificationDisabled == disableSslVerification;
  }

  /**
   * Sends {@code snapshot}. The request is built synchronously from the snapshot's lists, so the
   * caller may keep editing them once this returns. {@code callback} runs on the main thread
   * exactly once, also when the call is cancelled ({@link ErrorKind#CANCELLED}).
   */
  public void execute(RequestSnapshot snapshot, Callback callback) {
    String rawUrl = snapshot.url == null ? "" : snapshot.url.trim();
    if (!rawUrl.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
      rawUrl = "https://" + rawUrl;
    }

    HttpUrl parsed = HttpUrl.parse(rawUrl);
    if (parsed == null) {
      deliver(callback, failure(ErrorKind.INVALID_URL, rawUrl, ""));
      return;
    }

    HttpUrl.Builder urlBuilder = parsed.newBuilder();
    for (KeyValueItem param : snapshot.params) {
      String key = param.getKey() == null ? "" : param.getKey().trim();
      if (param.isEnabled() && !key.isEmpty()) {
        urlBuilder.addQueryParameter(key, param.getValue());
      }
    }

    String method = snapshot.method == null ? "GET" : snapshot.method.toUpperCase();
    Request.Builder requestBuilder = new Request.Builder().url(urlBuilder.build());

    for (KeyValueItem header : snapshot.headers) {
      String key = header.getKey() == null ? "" : header.getKey().trim();
      if (header.isEnabled() && !key.isEmpty()) {
        try {
          requestBuilder.header(key, header.getValue());
        } catch (IllegalArgumentException invalidHeader) {
          android.util.Log.w("HttpEngine", "Skipping a header row OkHttp rejects: " + key);
        }
      }
    }

    RequestBody body = null;
    boolean methodAllowsBody = !method.equals("GET") && !method.equals("HEAD");
    if (methodAllowsBody) {
      if (snapshot.bodyType == 1 && snapshot.rawBody != null && !snapshot.rawBody.isEmpty()) {
        MediaType mediaType =
            MediaType.parse(
                snapshot.rawContentType == null || snapshot.rawContentType.isEmpty()
                    ? "text/plain; charset=utf-8"
                    : snapshot.rawContentType);
        body = RequestBody.create(snapshot.rawBody, mediaType);
      } else if (snapshot.bodyType == 2) {
        FormBody.Builder formBuilder = new FormBody.Builder();
        boolean any = false;
        for (KeyValueItem field : snapshot.formFields) {
          String key = field.getKey() == null ? "" : field.getKey().trim();
          if (field.isEnabled() && !key.isEmpty()) {
            formBuilder.add(key, field.getValue());
            any = true;
          }
        }
        if (any) {
          body = formBuilder.build();
        }
      }
    }

    boolean methodRequiresBody =
        method.equals("POST") || method.equals("PUT") || method.equals("PATCH");
    if (body == null && methodRequiresBody) {
      body = RequestBody.create(new byte[0], null);
    }

    Request request;
    try {
      request = requestBuilder.method(method, body).build();
    } catch (IllegalArgumentException e) {
      deliver(callback, failure(ErrorKind.INVALID_REQUEST, e.getMessage(), parsed.host()));
      return;
    }

    String host = parsed.host();
    long startNanos = System.nanoTime();
    Call call = client.newCall(request);
    currentCall = call;
    call.enqueue(
        new okhttp3.Callback() {
          @Override
          public void onFailure(Call failedCall, IOException e) {
            String message =
                e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            HttpResult result = failure(classify(failedCall, e), message, host);
            result.timeMs = (System.nanoTime() - startNanos) / 1_000_000L;
            finish(failedCall, callback, result);
          }

          @Override
          public void onResponse(Call call, Response response) {
            HttpResult result = new HttpResult();
            result.success = true;
            result.host = host;
            result.statusCode = response.code();
            result.statusMessage = response.message();

            Headers responseHeaders = response.headers();
            for (int i = 0; i < responseHeaders.size(); i++) {
              result.headers.add(new String[] {responseHeaders.name(i), responseHeaders.value(i)});
            }

            try (ResponseBody responseBody = response.body()) {
              if (responseBody != null) {
                readBody(responseBody, result);
              }
            } catch (IOException e) {
              result.body = "";
              result.errorMessage = e.getMessage();
              if (call.isCanceled()) {
                result.success = false;
                result.errorKind = ErrorKind.CANCELLED;
              } else {
                result.errorKind = ErrorKind.BODY_UNREADABLE;
              }
            }
            result.timeMs = (System.nanoTime() - startNanos) / 1_000_000L;
            finish(call, callback, result);
          }
        });
  }

  /** Aborts the running call, if any; its callback then reports {@link ErrorKind#CANCELLED}. */
  public void cancel() {
    Call call = currentCall;
    if (call != null) {
      call.cancel();
    }
  }

  /**
   * Cancels the running call and releases the client's dispatcher threads and pooled connections.
   * The engine must not be used afterwards.
   */
  public void shutdown() {
    cancel();
    client.dispatcher().executorService().execute(() -> client.connectionPool().evictAll());
    client.dispatcher().executorService().shutdown();
  }

  private void finish(Call call, Callback callback, HttpResult result) {
    if (currentCall == call) {
      currentCall = null;
    }
    deliver(callback, result);
  }

  /**
   * Reads at most {@link #MAX_DISPLAY_BYTES} into memory and only counts the rest, so a huge
   * download cannot exhaust the heap.
   */
  private static void readBody(ResponseBody responseBody, HttpResult result) throws IOException {
    MediaType contentType = responseBody.contentType();
    Charset charset =
        contentType != null ? contentType.charset(StandardCharsets.UTF_8) : StandardCharsets.UTF_8;
    if (charset == null) {
      charset = StandardCharsets.UTF_8;
    }
    long declared = responseBody.contentLength();
    int initial = declared > 0 ? (int) Math.min(declared, MAX_DISPLAY_BYTES) : READ_CHUNK_BYTES;
    ByteArrayOutputStream kept = new ByteArrayOutputStream(initial);
    byte[] buffer = new byte[READ_CHUNK_BYTES];
    long total = 0;
    try (InputStream in = responseBody.byteStream()) {
      int read;
      while ((read = in.read(buffer)) != -1) {
        long room = MAX_DISPLAY_BYTES - kept.size();
        if (room > 0) {
          kept.write(buffer, 0, (int) Math.min(room, read));
        }
        total += read;
      }
    }
    result.sizeBytes = total;
    result.bodyTruncated = total > MAX_DISPLAY_BYTES;
    result.body = new String(kept.toByteArray(), charset);
  }

  private static ErrorKind classify(Call call, IOException e) {
    if (call.isCanceled()) {
      return ErrorKind.CANCELLED;
    }
    if (e instanceof UnknownHostException) {
      return ErrorKind.UNKNOWN_HOST;
    }
    if (e instanceof SocketTimeoutException
        || (e instanceof InterruptedIOException && "timeout".equals(e.getMessage()))) {
      return ErrorKind.TIMEOUT;
    }
    if (e instanceof ConnectException || e instanceof NoRouteToHostException) {
      return ErrorKind.CONNECTION_REFUSED;
    }
    if (e instanceof SSLException) {
      return ErrorKind.TLS;
    }
    return ErrorKind.NETWORK;
  }

  private static HttpResult failure(ErrorKind kind, String message, String host) {
    HttpResult failure = new HttpResult();
    failure.success = false;
    failure.errorKind = kind;
    failure.errorMessage = message;
    failure.host = host == null ? "" : host;
    return failure;
  }

  private void deliver(Callback callback, HttpResult result) {
    mainHandler.post(() -> callback.onResult(result));
  }

  /**
   * WARNING: this trusts every TLS certificate. It exists purely so people can test servers with a
   * self-signed cert during development, and is only ever enabled if the user explicitly flips the
   * setting on. Never enabled by default. If the TLS context cannot be built, the client keeps the
   * platform's normal, secure verification.
   */
  private void applyInsecureTrustManager(OkHttpClient.Builder builder) {
    final X509TrustManager trustAllManager =
        new X509TrustManager() {
          @Override
          public void checkClientTrusted(X509Certificate[] chain, String authType) {}

          @Override
          public void checkServerTrusted(X509Certificate[] chain, String authType) {}

          @Override
          public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
          }
        };
    try {
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, new TrustManager[] {trustAllManager}, new SecureRandom());
      SSLSocketFactory socketFactory = sslContext.getSocketFactory();
      builder.sslSocketFactory(socketFactory, trustAllManager);
      HostnameVerifier trustAllHostnames = (hostname, session) -> true;
      builder.hostnameVerifier(trustAllHostnames);
    } catch (GeneralSecurityException secureDefaultKept) {
      android.util.Log.w("HttpEngine", "Insecure TLS unavailable, keeping verification on");
    }
  }
}
