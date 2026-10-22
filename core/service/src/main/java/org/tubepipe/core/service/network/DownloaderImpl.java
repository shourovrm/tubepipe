package org.tubepipe.core.service.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

import org.schabi.newpipe.extractor.downloader.CancellableCall;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.downloader.StreamingResponse;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.localization.Localization;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Trimmed copy of PipePipeClient's DownloaderImpl (GPL-3.0, PipePipe and NewPipe contributors). The request building,
 * DNS retry and streaming behaviour are upstream's; Bilibili, reCaptcha cookies, restricted-mode
 * cookies, InfoCache, the legacy TLS socket factory and the player-response benchmark cache are
 * left out because nothing on the YouTube SABR path uses them.
 */
public final class DownloaderImpl extends Downloader {
    public static final String USER_AGENT
            = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0";

    private static final int MAX_DNS_RETRIES = 2;
    private static final long DNS_RETRY_DELAY_MS = 500L;

    private static DownloaderImpl instance;
    private final OkHttpClient client;

    private DownloaderImpl(final OkHttpClient.Builder builder) {
        this.client = builder.readTimeout(30, TimeUnit.SECONDS).build();
    }

    public static DownloaderImpl init(@Nullable final OkHttpClient.Builder builder) {
        instance = new DownloaderImpl(builder != null ? builder : new OkHttpClient.Builder());
        return instance;
    }

    public static DownloaderImpl getInstance() {
        return instance;
    }

    @Override
    public Response execute(@NonNull final Request request) throws IOException, ReCaptchaException {
        final String url = request.url();
        final OkHttpClient requestClient = request.followRedirects() ? client
                : client.newBuilder().followRedirects(false).followSslRedirects(false).build();
        final okhttp3.Response response = executeWithDnsRetry(requestClient,
                buildOkHttpRequest(request));
        if (response.code() == 429) {
            response.close();
            throw new ReCaptchaException("reCaptcha Challenge requested", url);
        }
        final ResponseBody body = response.body();
        byte[] rawBodyBytes = null;
        String bodyText = null;
        try {
            if (body != null) {
                rawBodyBytes = body.bytes();
                bodyText = new String(rawBodyBytes, StandardCharsets.UTF_8);
            }
        } finally {
            if (body != null) {
                body.close();
            }
        }
        return new Response(response.code(), response.message(), response.headers().toMultimap(),
                bodyText, rawBodyBytes, response.request().url().toString());
    }

    // Streaming variants hand back okhttp's byteStream() so a SABR media batch (tens of MB at
    // 1080p) is not buffered into one byte[].
    @Override
    public StreamingResponse getStreaming(final String url,
                                          @Nullable final Map<String, List<String>> headers,
                                          @Nullable final Localization localization)
            throws IOException, ReCaptchaException {
        return executeStreaming(Request.newBuilder().get(url).headers(headers)
                .localization(localization).build(), client);
    }

    @Override
    public StreamingResponse getStreaming(final String url,
                                          @Nullable final Map<String, List<String>> headers,
                                          @Nullable final Localization localization,
                                          final long timeoutMs)
            throws IOException, ReCaptchaException {
        final long boundedTimeoutMs = Math.max(1, timeoutMs);
        final OkHttpClient boundedClient = client.newBuilder()
                .callTimeout(boundedTimeoutMs, TimeUnit.MILLISECONDS)
                .connectTimeout(boundedTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(boundedTimeoutMs, TimeUnit.MILLISECONDS)
                .build();
        return executeStreaming(Request.newBuilder().get(url).headers(headers)
                .localization(localization).build(), boundedClient);
    }

    @Override
    public StreamingResponse postStreaming(final String url,
                                           @Nullable final Map<String, List<String>> headers,
                                           @Nullable final byte[] dataToSend,
                                           @Nullable final Localization localization)
            throws IOException, ReCaptchaException {
        return executeStreaming(Request.newBuilder().post(url, dataToSend).headers(headers)
                .localization(localization).build(), client);
    }

    private StreamingResponse executeStreaming(@NonNull final Request request,
                                               @NonNull final OkHttpClient requestClient)
            throws IOException, ReCaptchaException {
        final okhttp3.Response response = executeWithDnsRetry(requestClient,
                buildOkHttpRequest(request));
        if (response.code() == 429) {
            response.close();
            throw new ReCaptchaException("reCaptcha Challenge requested", request.url());
        }
        final ResponseBody body = response.body();
        final InputStream stream = body == null
                ? new ByteArrayInputStream(new byte[0]) : body.byteStream();
        // Closing the StreamingResponse closes this stream, which releases the okhttp connection.
        return new StreamingResponse(response.code(), response.headers().toMultimap(), stream);
    }

    @Override
    public CancellableCall executeAsync(@NonNull final Request request,
                                        @NonNull final Downloader.AsyncCallback callback) {
        final Call call = client.newCall(buildOkHttpRequest(request));
        final CancellableCall cancellableCall = new CancellableCall(call);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull final Call failedCall, @NonNull final IOException error) {
                try {
                    callback.onError(error);
                } finally {
                    cancellableCall.setFinished();
                }
            }

            @Override
            public void onResponse(@NonNull final Call finishedCall,
                                   @NonNull final okhttp3.Response response) {
                try {
                    if (response.code() == 429) {
                        callback.onError(new ReCaptchaException(
                                "reCaptcha Challenge requested", request.url()));
                        return;
                    }
                    final ResponseBody body = response.body();
                    final byte[] rawBodyBytes = body == null ? null : body.bytes();
                    final String bodyText = rawBodyBytes == null
                            ? null : new String(rawBodyBytes, StandardCharsets.UTF_8);
                    callback.onSuccess(new Response(response.code(), response.message(),
                            response.headers().toMultimap(), bodyText, rawBodyBytes,
                            response.request().url().toString()));
                } catch (final Exception error) {
                    // onSuccess declares ExtractionException, so IOException alone is too narrow.
                    callback.onError(error);
                } finally {
                    response.close();
                    cancellableCall.setFinished();
                }
            }
        });
        return cancellableCall;
    }

    private static okhttp3.Request buildOkHttpRequest(@NonNull final Request request) {
        final String httpMethod = request.httpMethod();
        final byte[] dataToSend = request.dataToSend();
        RequestBody requestBody = null;
        if (dataToSend != null) {
            requestBody = RequestBody.create(dataToSend, null);
        } else if ("POST".equals(httpMethod)) {
            requestBody = RequestBody.create(new byte[0], null);
        }
        final okhttp3.Request.Builder builder = new okhttp3.Request.Builder()
                .method(httpMethod, requestBody).url(request.url());
        final Map<String, List<String>> headers = request.headers();
        // The extractor's own User-Agent wins; the default only fills the gap.
        if (!headers.containsKey("User-Agent")) {
            builder.header("User-Agent", USER_AGENT);
        }
        for (final Map.Entry<String, List<String>> pair : headers.entrySet()) {
            final List<String> values = pair.getValue();
            if (values.size() > 1) {
                builder.removeHeader(pair.getKey());
                for (final String value : values) {
                    builder.addHeader(pair.getKey(), value);
                }
            } else if (values.size() == 1) {
                builder.header(pair.getKey(), values.get(0));
            }
        }
        return builder.build();
    }

    private static okhttp3.Response executeWithDnsRetry(@NonNull final OkHttpClient requestClient,
                                                        @NonNull final okhttp3.Request request)
            throws IOException {
        int dnsRetryCount = 0;
        while (true) {
            try {
                return requestClient.newCall(request).execute();
            } catch (final UnknownHostException error) {
                dnsRetryCount++;
                if (dnsRetryCount > MAX_DNS_RETRIES) {
                    throw error;
                }
                try {
                    Thread.sleep(DNS_RETRY_DELAY_MS);
                } catch (final InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw error;
                }
            }
        }
    }
}
