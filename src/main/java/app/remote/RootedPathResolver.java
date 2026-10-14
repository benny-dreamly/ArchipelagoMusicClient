/*
 * SPDX-License-Identifier: MPL-2.0
 */
package app.remote;

import java.io.File;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps URL paths (relative to one or more root directories) to files on disk
 * and back again. Used by the companion server to stream the local library,
 * which may span several configured album folders.
 */
public final class RootedPathResolver {

    private final List<Path> bases;

    public RootedPathResolver(File root) {
        this(List.of(requireRoot(root)));
    }

    public RootedPathResolver(List<File> roots) {
        if (roots == null || roots.isEmpty()) {
            throw new IllegalArgumentException("at least one root is required");
        }
        List<Path> resolved = new ArrayList<>();
        for (File root : roots) {
            if (root == null) continue;
            Path path = root.toPath().toAbsolutePath().normalize();
            if (!resolved.contains(path)) {
                resolved.add(path);
            }
        }
        if (resolved.isEmpty()) {
            throw new IllegalArgumentException("at least one root is required");
        }
        this.bases = List.copyOf(resolved);
    }

    private static File requireRoot(File root) {
        if (root == null) throw new IllegalArgumentException("root must not be null");
        return root;
    }

    public Path getRoot() {
        return bases.get(0);
    }

    public File resolve(String urlPath) {
        if (urlPath == null) return null;
        String decoded = URLDecoder.decode(urlPath, StandardCharsets.UTF_8);
        String clean = decoded;
        if (clean.startsWith("/")) clean = clean.substring(1);
        if (clean.isEmpty()) clean = ".";
        for (Path base : bases) {
            Path candidate;
            try {
                candidate = base.resolve(clean).normalize();
            } catch (Exception e) {
                continue;
            }
            if (!candidate.startsWith(base)) continue;
            File file = candidate.toFile();
            if (file.isFile()) {
                return file;
            }
        }
        return null;
    }

    public String streamPath(File file) {
        if (file == null) return null;
        Path abs = file.toPath().toAbsolutePath().normalize();
        for (Path base : bases) {
            if (!abs.startsWith(base)) continue;
            String relative = base.relativize(abs).toString().replace(File.separatorChar, '/');
            String encoded = URLEncoder.encode(relative, StandardCharsets.UTF_8)
                    .replace("%2F", "/");
            return "/stream/" + encoded;
        }
        return null;
    }
}
