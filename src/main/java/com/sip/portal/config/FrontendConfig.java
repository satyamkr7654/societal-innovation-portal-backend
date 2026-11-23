package com.sip.portal.config;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/** Serves the built React app when FRONTEND_DIR is set, so one server runs everything. */
@Configuration
class FrontendConfig implements WebMvcConfigurer {
    private final AppProperties props;

    FrontendConfig(AppProperties props) {
        this.props = props;
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        if (props.servesFrontend()) registry.addViewController("/").setViewName("forward:/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (!props.servesFrontend()) return;
        Path dir = Path.of(props.frontendDir()).toAbsolutePath().normalize();
        // File names under /assets contain a content hash, so they can be cached for a year
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(new FileSystemResource(dir.resolve("assets") + "/"))
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        registry.addResourceHandler("/**")
                .addResourceLocations(new FileSystemResource(dir + "/"))
                .setCacheControl(CacheControl.noCache())
                .resourceChain(false)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        // Page addresses such as /admin/review belong to React Router
                        Resource file = super.getResource(path, location);
                        if (file != null && file.isFile()) return file;
                        return path.startsWith("api/") || path.startsWith("assets/") || path.contains(".")
                                ? null : location.createRelative("index.html");
                    }
                });
    }
}
