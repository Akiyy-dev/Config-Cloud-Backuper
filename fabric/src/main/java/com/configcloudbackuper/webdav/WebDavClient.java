package com.configcloudbackuper.webdav;

import com.configcloudbackuper.FabricModInitializer;
import okhttp3.*;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public boolean uploadText(String url, String text, String auth) {
        try {
            RequestBody body = RequestBody.create(MediaType.parse("text/plain; charset=utf-8"), text);
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
            FabricModInitializer.getLogger().error("Failed to upload text to WebDAV: " + url, e);
            return false;
        }
    }

    /**
     * 列出远程目录中的文件（PROPFIND）
     * @param dirUrl 远程目录 URL
     * @param auth   认证头
     * @return 远程文件名列表，失败返回空列表
     */
    public List<String> listFiles(String dirUrl, String auth) {
        List<String> fileNames = new ArrayList<>();
        try {
            // PROPFIND 请求体（请求深度为 1，只获取直接子文件/目录）
            String requestBody = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                    + "<propfind xmlns=\"DAV:\"><prop>"
                    + "<displayname/>"
                    + "<getlastmodified/>"
                    + "<getcontentlength/>"
                    + "</prop></propfind>";

            Request request = new Request.Builder()
                    .url(dirUrl)
                    .method("PROPFIND", RequestBody.create(MediaType.parse("application/xml"), requestBody))
                    .header("Authorization", auth)
                    .header("Depth", "1")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    FabricModInitializer.getLogger().warn("WebDAV PROPFIND failed: " + response.code());
                    return fileNames;
                }

                String responseXml = response.body().string();
                // 解析 XML 响应，提取 href 标签内容
                // 简单的正则解析，过滤掉目录本身
                Pattern pattern = Pattern.compile("<D:href>(.*?)</D:href>", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(responseXml);

                String basePath = dirUrl.endsWith("/") ? dirUrl : dirUrl + "/";

                while (matcher.find()) {
                    String href = matcher.group(1);
                    // 解码 URL 编码
                    href = java.net.URLDecoder.decode(href, StandardCharsets.UTF_8);
                    // 跳过目录本身
                    if (href.endsWith("/") || href.equals(basePath) || href.equals(getPathFromUrl(dirUrl))) {
                        continue;
                    }
                    // 提取文件名
                    String fileName = href.substring(href.lastIndexOf('/') + 1);
                    if (!fileName.isEmpty()) {
                        fileNames.add(fileName);
                    }
                }
            }
        } catch (IOException e) {
            FabricModInitializer.getLogger().error("Failed to list WebDAV directory: " + dirUrl, e);
        }
        return fileNames;
    }

    /**
     * 从 URL 中提取路径部分
     */
    private static String getPathFromUrl(String url) {
        try {
            java.net.URL parsedUrl = new java.net.URL(url);
            String path = parsedUrl.getPath();
            return path.endsWith("/") ? path : path + "/";
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * 从 WebDAV 下载文件（GET）
     * @param fileUrl    远程文件 URL
     * @param auth       认证头
     * @param targetPath 本地保存路径
     * @return true 如果下载成功
     */
    public boolean downloadFile(String fileUrl, String auth, Path targetPath) {
        try {
            Request request = new Request.Builder()
                    .url(fileUrl)
                    .get()
                    .header("Authorization", auth)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    FabricModInitializer.getLogger().error("WebDAV download failed: " + response.code());
                    return false;
                }

                // 确保父目录存在
                Files.createDirectories(targetPath.getParent());

                // 将响应体写入文件
                try (InputStream inputStream = response.body().byteStream()) {
                    Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }

                FabricModInitializer.getLogger().info("WebDAV download successful: " + targetPath.getFileName());
                return true;
            }
        } catch (IOException e) {
            FabricModInitializer.getLogger().error("Failed to download file from WebDAV: " + fileUrl, e);
            return false;
        }
    }

    public String downloadText(String fileUrl, String auth) {
        try {
            Request request = new Request.Builder()
                    .url(fileUrl)
                    .get()
                    .header("Authorization", auth)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return null;
                }
                return response.body().string();
            }
        } catch (IOException e) {
            FabricModInitializer.getLogger().error("Failed to download text from WebDAV: " + fileUrl, e);
            return null;
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
     * 构建远程目录 URL（不带文件名）
     */
    public static String buildRemoteDirUrl(String serverUrl, String remotePath) {
        String baseUrl = serverUrl.endsWith("/") ? serverUrl : serverUrl + "/";
        String path = remotePath.startsWith("/") ? remotePath.substring(1) : remotePath;
        path = path.endsWith("/") ? path : path + "/";
        return baseUrl + path;
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
