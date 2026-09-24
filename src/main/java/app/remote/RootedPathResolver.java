/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.remote;

import java.io.File;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Maps URL paths (relative to a single root directory) to files on disk and
 * back again. Used by the companion server to stream the local library.
 */
public final class RootedPathResolver {

    private final Path base;

    public RootedPathResolver(File root) {
        if (root == null) throw new IllegalArgumentException("root must not be null");
        this.base = root.toPath().toAbsolutePath().normalize();
    }

    public Path getRoot() {
        return base;
    }

    public File resolve(String urlPath) {
        if (urlPath == null) return null;
        String decoded = URLDecoder.decode(urlPath, StandardCharsets.UTF_8);
        String clean = decoded;
        if (clean.startsWith("/")) clean = clean.substring(1);
        if (clean.isEmpty()) clean = ".";
        try {
            Path candidate = base.resolve(clean).normalize();
            if (!candidate.startsWith(base)) return null;
            File file = candidate.toFile();
            return file.isFile() ? file : null;
        } catch (Exception e) {
            return null;
        }
    }

    public String streamPath(File file) {
        if (file == null) return null;
        Path abs = file.toPath().toAbsolutePath().normalize();
        if (!abs.startsWith(base)) return null;
        String relative = base.relativize(abs).toString().replace(File.separatorChar, '/');
        String encoded = URLEncoder.encode(relative, StandardCharsets.UTF_8).replace("%2F", "/");
        return "/stream/" + encoded;
    }
}