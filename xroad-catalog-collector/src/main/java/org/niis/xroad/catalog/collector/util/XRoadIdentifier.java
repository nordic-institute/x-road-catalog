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
package org.niis.xroad.catalog.collector.util;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.niis.xrd4j.common.exception.XRd4JException;
import org.niis.xrd4j.common.member.ObjectType;
import org.niis.xrd4j.common.member.ProducerMember;

import java.util.List;

/**
 * Provides an intermediate representation that doesn't validate which parts of
 * the identifier are present. XRD4J does validations in the constructor of
 * {@link ProducerMember}, which makes sense for calling services but not for
 * representing their state inside the application.
 */
@Getter
@Setter
@Builder
public class XRoadIdentifier {

    private String xRoadInstance;
    private String memberClass;
    private String memberCode;
    private String subsystemCode;
    private String serviceCode;

    // Non-identifier fields
    private String serviceVersion;
    private ObjectType objectType;

    // REST or OpenAPI
    private String serviceType;

    private List<Endpoint> endpointList;

    public List<Endpoint> getEndpoints() {
        return endpointList;
    }

    public void setEndpoints(List<Endpoint> endpoints) {
        this.endpointList = endpoints;
    }

    /**
     * Converts {@link XRoadIdentifier} to XRD4J's {@link ProducerMember} type.
     * @return The identifier as {@link ProducerMember}
     * @throws XRd4JException in case validation of the identifier fails. Check
     * the XRD4J library for more details.
     */
    public ProducerMember toProducerMember() throws XRd4JException {
        ProducerMember producerMember;

        /*
         * This is done because the ProducerMember constructor with the serviceVersion
         * will require the serviceCode to not be null. But for metaservices it can be
         */
        if (serviceCode == null) {
            producerMember =
                    new ProducerMember(xRoadInstance, memberClass, memberCode,
                            subsystemCode, null);
        } else {
            producerMember =
                    new ProducerMember(xRoadInstance, memberClass, memberCode,
                            subsystemCode, serviceCode, serviceVersion);
        }
        producerMember.setObjectType(objectType);
        return producerMember;
    }
}
