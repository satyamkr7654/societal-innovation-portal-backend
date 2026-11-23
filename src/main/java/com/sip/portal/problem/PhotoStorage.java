package com.sip.portal.problem;

import com.sip.portal.common.ApiException;
import com.sip.portal.config.AppProperties;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Re-encodes uploads, so only real pictures are kept and embedded GPS data is dropped. */
@Component
class PhotoStorage {
    static final String URL_PREFIX = "/api/photos/";
    private static final long MAX_PIXELS = 25_000_000;
    private static final Set<String> FORMATS = Set.of("jpeg", "png");
    private static final Pattern FILE_NAME = Pattern.compile("[0-9a-f-]{36}\\.jpg");

    private final Path directory;

    PhotoStorage(AppProperties props) throws IOException {
        directory = Path.of(props.uploadDir()).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        ImageIO.setUseCache(false);
    }

    String save(MultipartFile file) {
        BufferedImage picture = decode(file);
        // Drawing on white removes transparency, which JPEG cannot store
        BufferedImage flat = new BufferedImage(picture.getWidth(), picture.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = flat.createGraphics();
        graphics.drawImage(picture, 0, 0, Color.WHITE, null);
        graphics.dispose();
        String name = UUID.randomUUID() + ".jpg";
        try {
            ImageIO.write(flat, "jpg", directory.resolve(name).toFile());
        } catch (IOException e) {
            throw new IllegalStateException("Could not store photo", e);
        }
        return URL_PREFIX + name;
    }

    Optional<Path> find(String name) {
        if (!FILE_NAME.matcher(name).matches()) return Optional.empty(); // also blocks path traversal
        Path path = directory.resolve(name);
        return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }

    void delete(String url) {
        find(url.substring(URL_PREFIX.length())).ifPresent(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // an orphaned file is harmless
            }
        });
    }

    private static BufferedImage decode(MultipartFile file) {
        try (ImageInputStream input = ImageIO.createImageInputStream(file.getInputStream())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid();
            ImageReader reader = readers.next();
            try {
                if (!FORMATS.contains(reader.getFormatName().toLowerCase(java.util.Locale.ROOT))) throw invalid();
                reader.setInput(input, true, true);
                // Checked before decoding, so a tiny file cannot expand to gigabytes
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) throw invalid();
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException | IndexOutOfBoundsException e) {
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "Please upload a valid JPG or PNG photo.");
    }
}
