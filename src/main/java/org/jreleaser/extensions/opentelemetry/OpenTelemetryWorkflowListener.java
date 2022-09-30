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

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.jreleaser.extensions.api.workflow.WorkflowListener;
import org.jreleaser.extensions.opentelemetry.semcov.JReleaserOtelSemanticAttributes;
import org.jreleaser.logging.JReleaserLogger;
import org.jreleaser.model.api.JReleaserContext;
import org.jreleaser.model.api.announce.Announcer;
import org.jreleaser.model.api.assemble.Assembler;
import org.jreleaser.model.api.deploy.Deployer;
import org.jreleaser.model.api.distributions.Distribution;
import org.jreleaser.model.api.download.Downloader;
import org.jreleaser.model.api.hooks.ExecutionEvent;
import org.jreleaser.model.api.packagers.Packager;
import org.jreleaser.model.api.project.Project;
import org.jreleaser.model.api.release.Releaser;
import org.jreleaser.model.api.upload.Uploader;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.jreleaser.extensions.opentelemetry.OtelUtils.OTEL_PREFIX;

/**
 * @author Andres Almiray
 * @since 1.3.0
 */
public final class OpenTelemetryWorkflowListener implements WorkflowListener {
    private static final String CONTINUE_ON_ERROR = "continueOnError";
    private static final String ROOT_KEY = "ROOT";
    private final Map<String, Span> spanRegistry = new LinkedHashMap<>();

    private boolean continueOnError;
    private OpenTelemetrySdkService openTelemetrySdkService;

    @Override
    public void init(JReleaserContext context, Map<String, Object> properties) {
        if (properties.containsKey(CONTINUE_ON_ERROR)) {
            continueOnError = isTrue(properties.get(CONTINUE_ON_ERROR));
        }

        openTelemetrySdkService = new OpenTelemetrySdkService(context.getLogger());
    }

    @Override
    public boolean isContinueOnError() {
        return false;
    }

    @Override
    public void onSessionStart(JReleaserContext context) {
        openTelemetrySdkService.initialize();

        TextMapGetter<Map<String, String>> toUpperCaseTextMapGetter = new ToUpperCaseTextMapGetter();
        Context otelContext = openTelemetrySdkService
            .getPropagators()
            .getTextMapPropagator()
            .extract(
                Context.current(),
                System.getenv(),
                toUpperCaseTextMapGetter);

        Project project = context.getModel().getProject();
        String spanName = "Release: "
            + project.getName()
            + ":"
            + project.getVersion();
        debug(context.getLogger(), "Start session span: {}", spanName);

        Span sessionSpan =
            this.openTelemetrySdkService
                .getTracer()
                .spanBuilder(spanName)
                .setParent(otelContext)
                .setAttribute(JReleaserOtelSemanticAttributes.JRELEASER_PROJECT_NAME, project.getName())
                .setAttribute(JReleaserOtelSemanticAttributes.JRELEASER_PROJECT_VERSION, project.getVersion())
                .setSpanKind(SpanKind.SERVER)
                .startSpan();

        spanRegistry.put(ROOT_KEY, sessionSpan);
    }

    @Override
    public void onSessionEnd(JReleaserContext context) {
        spanRegistry.remove(ROOT_KEY).end();

        openTelemetrySdkService.dispose();
    }

    @Override
    public void onWorkflowStep(ExecutionEvent event, JReleaserContext context) {
        Span rootSpan = spanRegistry.get(ROOT_KEY);
        String spanName = event.getName();

        switch (event.getType()) {
            case BEFORE:
                debug(context.getLogger(), "Start workflow step: span {}", spanName);
                spanRegistry.put(spanName, this.openTelemetrySdkService
                    .getTracer()
                    .spanBuilder(spanName)
                    .setParent(Context.current().with(Span.wrap(rootSpan.getSpanContext())))
                    .setAttribute(JReleaserOtelSemanticAttributes.JRELEASER_WORKFLOW_STEP, event.getName())
                    .startSpan());
                break;
            case SUCCESS: {
                debug(context.getLogger(), "End succeeded workflow step: span {}", spanName);
                Span span = spanRegistry.remove(spanName);
                span.setStatus(StatusCode.OK);
                span.end();
                break;
            }
            case FAILURE: {
                debug(context.getLogger(), "End failed workflow step: span {}", spanName);
                Span span = spanRegistry.remove(spanName);
                span.setStatus(StatusCode.ERROR);
                span.recordException(event.getFailure());
                span.end();
                break;
            }
        }
    }

    @Override
    public void onAnnounceStep(ExecutionEvent event, JReleaserContext context, Announcer announcer) {

    }

    @Override
    public void onAssembleStep(ExecutionEvent event, JReleaserContext context, Assembler assembler) {

    }

    @Override
    public void onDeployStep(ExecutionEvent event, JReleaserContext context, Deployer deployer) {

    }

    @Override
    public void onDownloadStep(ExecutionEvent event, JReleaserContext context, Downloader downloader) {

    }

    @Override
    public void onUploadStep(ExecutionEvent event, JReleaserContext context, Uploader uploader) {

    }

    @Override
    public void onReleaseStep(ExecutionEvent event, JReleaserContext context, Releaser releaser) {

    }

    @Override
    public void onDistributionStart(JReleaserContext context, Distribution distribution) {

    }

    @Override
    public void onDistributionEnd(JReleaserContext context, Distribution distribution) {

    }

    @Override
    public void onPackagerPrepareStep(ExecutionEvent event, JReleaserContext context, Distribution distribution, Packager packager) {

    }

    @Override
    public void onPackagerPackageStep(ExecutionEvent event, JReleaserContext context, Distribution distribution, Packager packager) {

    }

    @Override
    public void onPackagerPublishStep(ExecutionEvent event, JReleaserContext context, Distribution distribution, Packager packager) {

    }

    private void debug(JReleaserLogger logger, String message, Object... args) {
        logger.setPrefix(OTEL_PREFIX);
        logger.debug(message, args);
        logger.restorePrefix();
    }

    private void info(JReleaserLogger logger, String message, Object... args) {
        logger.setPrefix(OTEL_PREFIX);
        logger.info(message, args);
        logger.restorePrefix();
    }

    private void warn(JReleaserLogger logger, String message, Object... args) {
        logger.setPrefix(OTEL_PREFIX);
        logger.warn(message, args);
        logger.restorePrefix();
    }

    private void error(JReleaserLogger logger, String message, Object... args) {
        logger.setPrefix(OTEL_PREFIX);
        logger.error(message, args);
        logger.restorePrefix();
    }

    private void error(JReleaserLogger logger, Throwable t, String message, String... args) {
        logger.setPrefix(OTEL_PREFIX);
        logger.error(message, args, t);
        logger.restorePrefix();
    }

    private static boolean isTrue(Object o) {
        if (o == null) return false;
        if (o instanceof Boolean) return (Boolean) o;
        return "true".equalsIgnoreCase(String.valueOf(o).trim());
    }

    private static class ToUpperCaseTextMapGetter implements TextMapGetter<Map<String, String>> {
        @Override
        public Iterable<String> keys(Map<String, String> environmentVariables) {
            return environmentVariables.keySet();
        }

        @Override
        public String get(Map<String, String> environmentVariables, String key) {
            return environmentVariables == null
                ? null
                : environmentVariables.get(key.toUpperCase(Locale.ROOT));
        }
    }
}
