/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2022 The JReleaser authors.
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

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import org.jreleaser.logging.JReleaserLogger;
import org.jreleaser.model.JReleaserVersion;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.jreleaser.extensions.opentelemetry.OtelUtils.OTEL_PREFIX;

/**
 * Service to configure the {@link OpenTelemetry} instance.
 * <p>
 * Adapted from https://github.com/open-telemetry/opentelemetry-java-contrib/maven-extension
 * Original author &amp; copyright: The OpenTelemetry Authors
 */
public final class OpenTelemetrySdkService {
    public static final String VERSION = JReleaserVersion.getPlainVersion();

    private final JReleaserLogger logger;
    @Nullable
    private AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk;
    private OpenTelemetry openTelemetry = OpenTelemetry.noop();
    @Nullable
    private OpenTelemetrySdk openTelemetrySdk;
    @Nullable
    private Tracer tracer;

    public OpenTelemetrySdkService(JReleaserLogger logger) {
        this.logger = logger;
    }

    public synchronized void dispose() {
        logger.setPrefix(OTEL_PREFIX);

        try {
            logger.debug("dispose OpenTelemetrySdkService...");
            OpenTelemetrySdk openTelemetrySdk = this.openTelemetrySdk;
            if (openTelemetrySdk != null) {
                logger.debug("Shutdown SDK Trace Provider...");
                CompletableResultCode sdkProviderShutdown =
                    openTelemetrySdk.getSdkTracerProvider().shutdown();
                sdkProviderShutdown.join(10, TimeUnit.SECONDS);
                if (sdkProviderShutdown.isSuccess()) {
                    logger.debug("SDK Trace Provider shut down");
                } else {
                    logger.warn(
                        "Failure to shutdown SDK Trace Provider (done: "
                            + sdkProviderShutdown.isDone()
                            + ")");
                }
                this.openTelemetrySdk = null;
            }
            this.openTelemetry = OpenTelemetry.noop();

            this.autoConfiguredOpenTelemetrySdk = null;
            logger.debug("OpenTelemetrySdkService disposed");
        } finally {
            logger.restorePrefix();
        }
    }

    public void initialize() {
        logger.setPrefix(OTEL_PREFIX);

        try {
            logger.debug("Initialize OpenTelemetrySdkService v{}...", VERSION);

            // Change default of "otel.traces.exporter" from "otlp" to "none"
            // The impacts are
            // * If no otel exporter settings are passed, then the JReleaser extension will not export
            //   rather than exporting on OTLP GRPC to http://localhost:4317
            // * If OTEL_EXPORTER_OTLP_ENDPOINT is defined but OTEL_TRACES_EXPORTER is not, then don't
            //   export
            Map<String, String> properties = Collections.singletonMap("otel.traces.exporter", "none");

            this.autoConfiguredOpenTelemetrySdk =
                AutoConfiguredOpenTelemetrySdk.builder()
                    .setServiceClassLoader(getClass().getClassLoader())
                    .addPropertiesSupplier(() -> properties)
                    .build();

            logger.debug(
                "OpenTelemetry SDK initialized with  "
                    + OtelUtils.prettyPrintSdkConfiguration(autoConfiguredOpenTelemetrySdk));

            this.openTelemetrySdk = autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk();
            this.openTelemetry = this.openTelemetrySdk;

            this.tracer = openTelemetry.getTracer("io.opentelemetry.contrib.jreleaser", VERSION);
        } finally {
            logger.restorePrefix();
        }
    }

    public Tracer getTracer() {
        Tracer tracer = this.tracer;
        if (tracer == null) {
            throw new IllegalStateException("Not initialized");
        }
        return tracer;
    }

    /**
     * Returns the {@link ContextPropagators} for this {@link OpenTelemetry}.
     */
    public ContextPropagators getPropagators() {
        return openTelemetry.getPropagators();
    }
}
