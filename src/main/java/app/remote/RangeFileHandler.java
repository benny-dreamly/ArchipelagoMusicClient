/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.remote;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serves single files from the resolver's root over HTTP with full Range
 * support, so phones can seek while streaming. Handles GET and HEAD.
 */
public final class RangeFileHandler implements HttpHandler {

    private static final Pattern RANGE_PATTERN = Pattern.compile("^bytes=(\\d*)-(\\d*)$");

    private final RootedPathResolver resolver;

    public RangeFileHandler(RootedPathResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String rawPath = exchange.getRequestURI().getRawPath();
            String relative = rawPath;
            if (relative.startsWith("/stream")) {
                relative = relative.substring("/stream".length());
            }
            File file = resolver.resolve(relative);
            if (file == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            long length;
            try {
                length = Files.size(file.toPath());
            } catch (IOException e) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            Headers headers = exchange.getResponseHeaders();
            headers.set("Accept-Ranges", "bytes");
            headers.set("Content-Type", contentTypeFor(file.getName()));

            String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
            if (rangeHeader == null) {
                sendBody(exchange, file.toPath(), 200, 0, length, method);
                return;
            }

            Matcher matcher = RANGE_PATTERN.matcher(rangeHeader.trim());
            if (!matcher.matches()) {
                headers.set("Content-Range", "bytes */" + length);
                exchange.sendResponseHeaders(416, -1);
                return;
            }

            long start;
            long end;
            String startGroup = matcher.group(1);
            String endGroup = matcher.group(2);
            if (startGroup.isEmpty() && endGroup.isEmpty()) {
                sendBody(exchange, file.toPath(), 200, 0, length, method);
                return;
            }
            if (startGroup.isEmpty()) {
                long suffix = Long.parseLong(endGroup);
                if (suffix <= 0 || length == 0) {
                    headers.set("Content-Range", "bytes */" + length);
                    exchange.sendResponseHeaders(416, -1);
                    return;
                }
                start = Math.max(0, length - suffix);
                end = length - 1;
            } else {
                start = Long.parseLong(startGroup);
                end = endGroup.isEmpty() ? length - 1 : Math.min(Long.parseLong(endGroup), length - 1);
                if (start > end || start >= length) {
                    headers.set("Content-Range", "bytes */" + length);
                    exchange.sendResponseHeaders(416, -1);
                    return;
                }
            }

            long partLength = end - start + 1;
            headers.set("Content-Range", "bytes " + start + "-" + end + "/" + length);
            sendBody(exchange, file.toPath(), 206, start, partLength, method);
        } finally {
            exchange.close();
        }
    }

    private void sendBody(HttpExchange exchange, Path file, int status, long offset, long count,
                          String method) throws IOException {
        exchange.getResponseHeaders().set("Content-Length", String.valueOf(count));
        exchange.sendResponseHeaders(status, method.equals("HEAD") ? -1 : count);
        if (method.equals("HEAD") || count <= 0) {
            return;
        }
        try (InputStream in = Files.newInputStream(file);
             OutputStream out = exchange.getResponseBody()) {
            in.skipNBytes(offset);
            byte[] buffer = new byte[64 * 1024];
            long remaining = count;
            while (remaining > 0) {
                int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) break;
                out.write(buffer, 0, read);
                remaining -= read;
            }
        }
    }

    private String contentTypeFor(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".m4a") || lower.endsWith(".mp4")) return "audio/mp4";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".flac")) return "audio/flac";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        return "application/octet-stream";
    }
}