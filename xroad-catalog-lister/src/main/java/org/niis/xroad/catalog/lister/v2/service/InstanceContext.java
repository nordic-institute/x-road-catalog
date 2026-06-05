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
package org.niis.xroad.catalog.lister.v2.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;

/**
 * Resolves the X-Road instance identifier from the locally-cached
 * {@code shared-params.xml} that the X-Road configuration-client downloads
 * after Spring startup. The file is loaded lazily on first access and cached
 * for the lifetime of the application — once a central server publishes an
 * instance ID it does not change.
 *
 * <p>If the file isn't present when first queried (typical during the ~60s
 * window between Spring startup and the first configuration-client sync), a
 * {@code 503 Service Unavailable} is raised so callers can retry. Subsequent
 * calls hit the cached value with no I/O.</p>
 */
@Slf4j
@Component
public class InstanceContext {

    @Value("${xroad-catalog.shared-params-file}")
    private String sharedParamsFile;

    private String cachedInstance;

    public synchronized String getCurrentInstance() {
        if (cachedInstance == null) {
            cachedInstance = loadInstance();
        }
        return cachedInstance;
    }

    private String loadInstance() {
        File file = new File(sharedParamsFile);
        if (!file.exists()) {
            log.warn("shared-params.xml not yet available at {} — configuration-client may still be initializing",
                    sharedParamsFile);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "X-Road instance identifier not yet available; configuration-client may still be initializing");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(file);
            NodeList list = document.getElementsByTagName("instanceIdentifier");
            if (list.getLength() == 0 || list.item(0).getTextContent() == null
                    || list.item(0).getTextContent().isBlank()) {
                throw new IllegalStateException(
                        "shared-params.xml is missing <instanceIdentifier>: " + sharedParamsFile);
            }
            String instance = list.item(0).getTextContent().trim();
            log.info("Loaded X-Road instance identifier '{}' from {}", instance, sharedParamsFile);
            return instance;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to load X-Road instance identifier from " + sharedParamsFile, e);
        }
    }
}
