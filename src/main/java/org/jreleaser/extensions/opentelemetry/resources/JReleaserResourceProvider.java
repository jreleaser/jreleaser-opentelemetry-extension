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
package org.jreleaser.extensions.opentelemetry.resources;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import org.jreleaser.extensions.opentelemetry.semcov.JReleaserOtelSemanticAttributes;
import org.jreleaser.model.JReleaserVersion;
import org.kordamp.jipsy.annotations.ServiceProviderFor;

/**
 * @author Andres Almiray
 * @since 1.0.0
 */
@ServiceProviderFor(ResourceProvider.class)
public class JReleaserResourceProvider implements ResourceProvider {
    @Override
    public Resource createResource(ConfigProperties config) {
        return Resource.builder()
            .put(ResourceAttributes.SERVICE_NAME, JReleaserOtelSemanticAttributes.SERVICE_NAME_VALUE)
            .put(ResourceAttributes.SERVICE_VERSION, JReleaserVersion.getPlainVersion())
            .build();
    }
}
