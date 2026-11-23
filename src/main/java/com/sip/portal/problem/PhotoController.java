package com.sip.portal.problem;

import com.sip.portal.common.ApiException;
import java.time.Duration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
class PhotoController {
    private final PhotoStorage photos;

    PhotoController(PhotoStorage photos) {
        this.photos = photos;
    }

    @GetMapping(PhotoStorage.URL_PREFIX + "{name}")
    ResponseEntity<Resource> photo(@PathVariable String name) {
        var path = photos.find(name)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Photo not found."));
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(new FileSystemResource(path));
    }
}
