/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2022-2023 The JReleaser authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jreleaser.extensions.opentelemetry;

import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OpenTelemetry utility class.
 * <p>
 * Adapted from https://github.com/open-telemetry/opentelemetry-java-contrib/maven-extension
 * Original author &amp; copyright: The OpenTelemetry Authors
 */
final class OtelUtils {
    public static final String OTEL_PREFIX = "otel";

    private OtelUtils() {
    }

    static String prettyPrintSdkConfiguration(
        AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk) {
        List<String> configAttributeNames =
            Arrays.asList(
                "otel.traces.exporter",
                "otel.metrics.exporter",
                "otel.exporter.otlp.endpoint",
                "otel.exporter.otlp.traces.endpoint",
                "otel.exporter.otlp.metrics.endpoint",
                "otel.exporter.jaeger.endpoint",
                "otel.exporter.prometheus.port",
                "otel.resource.attributes",
                "otel.service.name");

        ConfigProperties sdkConfig = autoConfiguredOpenTelemetrySdk.getConfig();
        Map<String, String> configurationAttributes = new LinkedHashMap<>();
        for (String attributeName : configAttributeNames) {
            String attributeValue = sdkConfig.getString(attributeName);
            if (attributeValue != null) {
                configurationAttributes.put(attributeName, attributeValue);
            }
        }

        Resource sdkResource = autoConfiguredOpenTelemetrySdk.getResource();

        return "Configuration: "
            + configurationAttributes.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining(", "))
            + ", Resource: "
            + sdkResource.getAttributes();
    }
}
