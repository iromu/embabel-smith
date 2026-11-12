/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.studio.config;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the built SPA from {@code classpath:/static} and falls back to
 * {@code index.html} for the client-side routes, so a deep link or a refresh on
 * {@code /run}, {@code /improve}, etc. loads the app instead of a 404. Existing static
 * assets are returned as-is; the API under {@code /api/**} is excluded so unmatched API
 * paths still 404 rather than returning the SPA shell.
 *
 * @author Ivan Rodriguez
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
