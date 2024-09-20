/**
 * The MIT License
 *
 * Copyright (c) 2023- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2016-2023 Finnish Digital Agency
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.catalog.lister.configuration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;

import java.util.Arrays;
import java.util.List;

@Order
public class ConfigurationClientSettingsEnvironmentPostProcessor implements EnvironmentPostProcessor {
    private final DeferredLog log = new DeferredLog();

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        application.addInitializers(ctx -> log.replayTo(this.getClass()));
        if (!isConfigurationClientEnabled(environment)) {
            log.warn("Configuration client is disabled. Skipping setting xroad properties as system properties.");
            return;
        }

        List<String> xroadKeys = getKeys(environment);
        log.info("Found [" + xroadKeys.size() + "] xroad properties to set as system properties: " + xroadKeys);
        xroadKeys.forEach(key -> setPropertyAsSystemProperty(environment, key));
    }

    private List<String> getKeys(ConfigurableEnvironment environment) {
        return environment.getPropertySources()
                .stream()
                .filter(propertySource -> propertySource instanceof EnumerablePropertySource)
                .map(EnumerablePropertySource.class::cast)
                .flatMap(propertySource -> Arrays.stream(propertySource.getPropertyNames()))
                .filter(key -> key.startsWith("xroad."))
                .distinct()
                .toList();
    }

    private void setPropertyAsSystemProperty(ConfigurableEnvironment environment, String key) {
        System.setProperty(key, environment.getProperty(key).toString());
    }

    private boolean isConfigurationClientEnabled(ConfigurableEnvironment environment) {
        return environment.getProperty("xroad-catalog.configuration-client.enabled", Boolean.class, false);
    }
}
