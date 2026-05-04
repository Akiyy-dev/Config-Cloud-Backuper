package com.naocraftlab.configbackuper.webdav;

import com.naocraftlab.configbackuper.FabricModInitializer;
import okhttp3.*;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

public class WebDavClient {

    private static final int CONNECT_TIMEOUT = 10;
    private static final int READ_TIMEOUT = 60;
    private static final int WRITE_TIMEOUT = 60;
    private static final int MAX_RETRIES = 3;

    private final OkHttpClient client;

    public WebDavClient() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
                .addInterceptor(new RetryInterceptor(MAX_RETRIES))
                .build();
    }

    /**
     * 创建远程目录（MKCOL）
     */
    public boolean createDirectory(String url, String auth) {
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .method("MKCOL", RequestBody.create(null, new byte[0]))
                    .header("Authorization", auth)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                int code = response.code();
                // 201 Created 或 405 Method Not Allowed (目录已存在) 都算成功
                return code == 201 || code == 405 || code == 204;
            }
        } catch (IOException e) {
            FabricModInitializer.getLogger().error("Failed to create WebDAV directory: " + url, e);
            return false;
        }
    }

    /**
     * 上传文件到 WebDAV（PUT）
     */
    public boolean uploadFile(String url, File file, String auth) {
        try {
            RequestBody body = new RequestBody() {
                @Override
                public MediaType contentType() {
                    return MediaType.parse("application/octet-stream");
                }

                @Override
                public long contentLength() {
                    return file.length();
                }

                @Override
                public void writeTo(BufferedSink sink) throws IOException {
                    try (Source source = Okio.source(file)) {
                        sink.writeAll(source);
                    }
                }
            };

            Request request = new Request.Builder()
                    .url(url)
                    .put(body)
                    .header("Authorization", auth)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                int code = response.code();
                return code == 201 || code == 204 || code == 200;
            }
        } catch (IOException e) {
            FabricModInitializer.getLogger().error("Failed to upload file to WebDAV: " + url, e);
            return false;
        }
    }

    /**
     * 构建认证头
     */
    public static String buildAuth(String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    /**
     * 构建完整的远程 URL
     */
    public static String buildRemoteUrl(String serverUrl, String remotePath, String fileName) {
        String baseUrl = serverUrl.endsWith("/") ? serverUrl : serverUrl + "/";
        String path = remotePath.startsWith("/") ? remotePath.substring(1) : remotePath;
        path = path.endsWith("/") ? path : path + "/";
        return baseUrl + path + URLEncoder.encode(fileName, StandardCharsets.UTF_8);
    }

    /**
     * 重试拦截器
     */
    private static class RetryInterceptor implements Interceptor {
        private final int maxRetries;

        public RetryInterceptor(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            Response response = null;
            IOException lastException = null;

            for (int i = 0; i <= maxRetries; i++) {
                try {
                    if (i > 0) {
                        // 指数退避
                        Thread.sleep(1000L * (1L << (i - 1)));
                    }
                    response = chain.proceed(request);
                    if (response.isSuccessful()) {
                        return response;
                    }
                    // 对 5xx 错误进行重试
                    if (response.code() >= 500 && response.code() < 600 && i < maxRetries) {
                        response.close();
                        continue;
                    }
                    return response;
                } catch (IOException e) {
                    lastException = e;
                    if (i == maxRetries) {
                        throw e;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Retry interrupted", e);
                }
            }

            throw lastException != null ? lastException : new IOException("Request failed after retries");
        }
    }
}
