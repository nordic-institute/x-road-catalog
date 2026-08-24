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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;

/**
 * Pins the contract that env-var overrides of literal-underscore {@code xroad.*} keys (e.g.
 * {@code xroad.configuration-client.global_conf_tls_cert_verification}) reach the configuration client through
 * {@link ConfigurationClientSettingsEnvironmentPostProcessor} without any code change.
 */
class ConfigurationClientSettingsEnvironmentPostProcessorTest {

    private static final String TLS_CERT_VERIFICATION_KEY = "xroad.configuration-client.global_conf_tls_cert_verification";
    private static final String CONFIGURATION_CLIENT_ENABLED_KEY = "xroad-catalog.configuration-client.enabled";
    private static final String UNMAPPED_ENV_ONLY_KEY = "xroad.configuration-client.only-present-as-env-var";

    private final ConfigurationClientSettingsEnvironmentPostProcessor postProcessor =
            new ConfigurationClientSettingsEnvironmentPostProcessor();

    private String originalTlsCertVerificationProperty;
    private String originalUnmappedEnvOnlyProperty;

    @BeforeEach
    void snapshotSystemProperties() {
        originalTlsCertVerificationProperty = System.getProperty(TLS_CERT_VERIFICATION_KEY);
        originalUnmappedEnvOnlyProperty = System.getProperty(UNMAPPED_ENV_ONLY_KEY);
    }

    @AfterEach
    void restoreSystemProperties() {
        restoreProperty(TLS_CERT_VERIFICATION_KEY, originalTlsCertVerificationProperty);
        restoreProperty(UNMAPPED_ENV_ONLY_KEY, originalUnmappedEnvOnlyProperty);
    }

    private void restoreProperty(String key, String originalValue) {
        if (originalValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, originalValue);
        }
    }

    @Test
    void shouldLetEnvVarOverrideLiteralUnderscoreKeyVerbatim() {
        StandardEnvironment environment = new StandardEnvironment();
        Map<String, Object> systemEnvironment = new HashMap<>();
        systemEnvironment.put("XROAD_CONFIGURATION_CLIENT_GLOBAL_CONF_TLS_CERT_VERIFICATION", "false");
        environment.getPropertySources().addFirst(
                new SystemEnvironmentPropertySource(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, systemEnvironment));

        Map<String, Object> applicationYamlDefaults = new HashMap<>();
        applicationYamlDefaults.put(TLS_CERT_VERIFICATION_KEY, "true");
        applicationYamlDefaults.put(CONFIGURATION_CLIENT_ENABLED_KEY, "true");
        environment.getPropertySources().addLast(new MapPropertySource("applicationYaml", applicationYamlDefaults));

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertEquals("false", System.getProperty(TLS_CERT_VERIFICATION_KEY));
    }

    @Test
    void shouldNotSetSystemPropertiesWhenConfigurationClientDisabled() {
        StandardEnvironment environment = new StandardEnvironment();
        Map<String, Object> systemEnvironment = new HashMap<>();
        systemEnvironment.put("XROAD_CONFIGURATION_CLIENT_GLOBAL_CONF_TLS_CERT_VERIFICATION", "false");
        environment.getPropertySources().addFirst(
                new SystemEnvironmentPropertySource(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, systemEnvironment));

        Map<String, Object> applicationYamlDefaults = new HashMap<>();
        applicationYamlDefaults.put(TLS_CERT_VERIFICATION_KEY, "true");
        applicationYamlDefaults.put(CONFIGURATION_CLIENT_ENABLED_KEY, "false");
        environment.getPropertySources().addLast(new MapPropertySource("applicationYaml", applicationYamlDefaults));

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertNull(System.getProperty(TLS_CERT_VERIFICATION_KEY));
    }

    @Test
    void shouldNotCopyEnvVarOnlyKeyWithoutDottedDefaultInEnumerableSource() {
        StandardEnvironment environment = new StandardEnvironment();
        Map<String, Object> systemEnvironment = new HashMap<>();
        systemEnvironment.put("XROAD_CONFIGURATION_CLIENT_ONLY_PRESENT_AS_ENV_VAR", "value-from-env-only");
        environment.getPropertySources().addFirst(
                new SystemEnvironmentPropertySource(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, systemEnvironment));

        Map<String, Object> applicationYamlDefaults = new HashMap<>();
        applicationYamlDefaults.put(CONFIGURATION_CLIENT_ENABLED_KEY, "true");
        environment.getPropertySources().addLast(new MapPropertySource("applicationYaml", applicationYamlDefaults));

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertNull(System.getProperty(UNMAPPED_ENV_ONLY_KEY));
    }

}
