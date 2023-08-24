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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.tsurugidb.tsubakuro.auth.TicketProvider;
import com.tsurugidb.tsubakuro.auth.http.HttpTokenProvider;
import com.tsurugidb.tsubakuro.auth.http.JwtTicketProvider;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@Profile({ "default", "ut" })
public class AuthConfig {

    @Value("${webapi.auth.url}")
    String authServerUrl;

    @Bean
    public TicketProvider getTicketProvider() {
        log.debug("auth url:" + authServerUrl);
        return new JwtTicketProvider(
                new HttpTokenProvider(URI.create(authServerUrl),
                        HttpClient.newBuilder()
                                .version(Version.HTTP_1_1)
                                .connectTimeout(Duration.ofSeconds(20))
                                .build()));
    }

}
