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

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Keeps the management port closed unless it is explicitly enabled.
 *
 * <p>
 * The management port serves the actuator endpoints (health, info, beans, env,
 * prometheus) and the OpenAPI document and Swagger UI, none of which goes
 * through the authentication of the API port. It is opened only when
 * {@value #ENABLED_PROPERTY} is true ({@code BELAYER_MANAGEMENT_ENABLED});
 * otherwise {@code management.server.port} is set to -1, which keeps Spring
 * Boot from listening on it at all.
 * </p>
 *
 * <p>
 * springdoc is switched off along with it. It is configured to serve on the
 * management port (springdoc.use-management-port), and with that port gone
 * its Swagger UI configuration fails to start the application.
 * </p>
 *
 * <p>
 * It runs after the configuration files are loaded, so that the default in
 * application-common.properties is what it reads when the variable is unset.
 * </p>
 */
public class ManagementPortEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** property that opens the management port when true. */
    public static final String ENABLED_PROPERTY = "belayer.management.enabled";

    /** name of the property source that closes the management port. */
    static final String PROPERTY_SOURCE_NAME = "belayerManagementPortDisabled";

    /**
     * Create the post processor. Spring Boot instantiates it through
     * META-INF/spring.factories.
     */
    public ManagementPortEnvironmentPostProcessor() {
    }

    /**
     * Close the management port unless {@value #ENABLED_PROPERTY} is true.
     *
     * @param environment the environment to post-process
     * @param application the application to which the environment belongs
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean enabled = environment.getProperty(ENABLED_PROPERTY, Boolean.class, false);
        if (!enabled) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, Map.of(
                    "management.server.port", "-1",
                    "springdoc.api-docs.enabled", "false",
                    "springdoc.swagger-ui.enabled", "false")));
        }
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
