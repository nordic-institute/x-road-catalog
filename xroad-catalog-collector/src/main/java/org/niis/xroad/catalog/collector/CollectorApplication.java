/**
 * The MIT License
 *
 * Copyright (c) 2023- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2016-2023 Finnish Digital Agency
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.niis.xroad.catalog.collector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import java.net.URISyntaxException;
import java.nio.file.Path;

@Slf4j
@SpringBootApplication
public class CollectorApplication {

    public static void main(String[] args) throws URISyntaxException {

        ApplicationContext context = SpringApplication.run(CollectorApplication.class, args);

        final Environment env = context.getEnvironment();

        final String keystore = env.getProperty("xroad-catalog.ssl-keystore");
        final String keystorePw = env.getProperty("xroad-catalog.ssl-keystore-password");

        if (keystore != null && !keystore.isEmpty() && keystorePw != null) {
            if (!Path.of(keystore).toFile().exists()) {
                log.warn("Keystore file at {} is not accessible or does not exist, not using keystore", keystore);
            } else {
                log.info("Using keystore at {}", keystore);
                System.setProperty("javax.net.ssl.keyStore", keystore);
                System.setProperty("javax.net.ssl.keyStorePassword", keystorePw);
            }
        }
    }

}
