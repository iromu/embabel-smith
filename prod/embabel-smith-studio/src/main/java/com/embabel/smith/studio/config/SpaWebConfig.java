package com.embabel.smith.studio.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the built SPA from {@code classpath:/static} and falls back to {@code index.html} for the
 * client-side routes, so a deep link or a refresh on {@code /run}, {@code /improve}, etc. loads the
 * app instead of a 404. Existing static assets are returned as-is; the API under {@code /api/**}
 * is excluded so unmatched API paths still 404 rather than returning the SPA shell.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource resource = location.createRelative(resourcePath);
                        if (resource.exists() && resource.isReadable()) {
                            return resource;
                        }
                        if (resourcePath.startsWith("api/")) {
                            return null;
                        }
                        return location.createRelative("index.html");
                    }
                });
    }
}
