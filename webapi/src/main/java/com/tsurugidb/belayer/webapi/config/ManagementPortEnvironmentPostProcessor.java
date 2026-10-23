/*
 * Copyright 2023 tsurugi project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tsurugidb.belayer.webapi.config;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Applies the switches of the management port.
 *
 * <p>
 * The management port serves the actuator endpoints and, through springdoc,
 * the OpenAPI document and Swagger UI. None of them goes through the
 * authentication of the API port, so what it serves is kept small: only the
 * endpoints listed in {@value #EXPOSURE_PROPERTY}
 * ({@code BELAYER_MANAGEMENT_EXPOSURE}, health and prometheus by default) are
 * exposed. The port is open unless {@value #ENABLED_PROPERTY}
 * ({@code BELAYER_MANAGEMENT_ENABLED}) is false, in which case
 * {@code management.server.port} is set to -1 and Spring Boot does not listen
 * on it at all.
 * </p>
 *
 * <p>
 * springdoc follows the exposure: its OpenAPI document is enabled only when
 * openapi or swagger-ui is exposed (Swagger UI reads the document), and its
 * Swagger UI only when swagger-ui is exposed. It is configured to serve on
 * the management port (springdoc.use-management-port), and with that port
 * gone its Swagger UI configuration fails to start the application, so both
 * are switched off when the port is closed.
 * </p>
 *
 * <p>
 * It runs after the configuration files are loaded, so that the defaults in
 * application-common.properties are what it reads when the variables are
 * unset.
 * </p>
 */
public class ManagementPortEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** property that closes the management port when false. */
    public static final String ENABLED_PROPERTY = "belayer.management.enabled";

    /** property that lists the endpoints exposed on the management port. */
    public static final String EXPOSURE_PROPERTY = "belayer.management.exposure";

    /** name of the property source that this post processor adds. */
    static final String PROPERTY_SOURCE_NAME = "belayerManagementPort";

    /**
     * Create the post processor. Spring Boot instantiates it through
     * META-INF/spring.factories.
     */
    public ManagementPortEnvironmentPostProcessor() {
    }

    /**
     * Close the management port when {@value #ENABLED_PROPERTY} is false, and
     * switch springdoc off unless its endpoints are exposed.
     *
     * @param environment the environment to post-process
     * @param application the application to which the environment belongs
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean enabled = environment.getProperty(ENABLED_PROPERTY, Boolean.class, true);
        Set<String> exposure = parseExposure(environment.getProperty(EXPOSURE_PROPERTY, ""));
        boolean all = exposure.contains("*");
        boolean swaggerUi = enabled && (all || exposure.contains("swagger-ui"));
        boolean apiDocs = swaggerUi || (enabled && (all || exposure.contains("openapi")));

        var properties = new HashMap<String, Object>();
        if (!enabled) {
            properties.put("management.server.port", "-1");
        }
        if (!apiDocs) {
            properties.put("springdoc.api-docs.enabled", "false");
        }
        if (!swaggerUi) {
            properties.put("springdoc.swagger-ui.enabled", "false");
        }
        if (!properties.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
        }
    }

    /**
     * Parse a comma-separated list of endpoint IDs.
     *
     * @param value the list, as in management.endpoints.web.exposure.include
     * @return the endpoint IDs, trimmed, without blanks
     */
    static Set<String> parseExposure(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * Run last, after the configuration files have been loaded.
     *
     * @return the lowest precedence
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
