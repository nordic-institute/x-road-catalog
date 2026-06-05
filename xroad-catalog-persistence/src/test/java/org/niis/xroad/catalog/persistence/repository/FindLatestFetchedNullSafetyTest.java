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
package org.niis.xroad.catalog.persistence.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the contract that every repository's {@code findLatestFetched()} default method
 * returns {@code null} when the underlying table is empty (i.e. when
 * {@code findLatestFetchedInstant()} returns {@code null}). Without the null guard the
 * default method NPEs in {@code LocalDateTime.ofInstant(null, …)} on a fresh deployment.
 */
class FindLatestFetchedNullSafetyTest {

    @Test
    void memberRepositoryReturnsNullOnEmpty() {
        MemberRepository r = mock(MemberRepository.class);
        when(r.findLatestFetchedInstant()).thenReturn(null);
        when(r.findLatestFetched()).thenCallRealMethod();
        assertNull(r.findLatestFetched());
    }

    @Test
    void subsystemRepositoryReturnsNullOnEmpty() {
        SubsystemRepository r = mock(SubsystemRepository.class);
        when(r.findLatestFetchedInstant()).thenReturn(null);
        when(r.findLatestFetched()).thenCallRealMethod();
        assertNull(r.findLatestFetched());
    }

    @Test
    void serviceRepositoryReturnsNullOnEmpty() {
        ServiceRepository r = mock(ServiceRepository.class);
        when(r.findLatestFetchedInstant()).thenReturn(null);
        when(r.findLatestFetched()).thenCallRealMethod();
        assertNull(r.findLatestFetched());
    }

    @Test
    void wsdlRepositoryReturnsNullOnEmpty() {
        WsdlRepository r = mock(WsdlRepository.class);
        when(r.findLatestFetchedInstant()).thenReturn(null);
        when(r.findLatestFetched()).thenCallRealMethod();
        assertNull(r.findLatestFetched());
    }

    @Test
    void openApiRepositoryReturnsNullOnEmpty() {
        OpenApiRepository r = mock(OpenApiRepository.class);
        when(r.findLatestFetchedInstant()).thenReturn(null);
        when(r.findLatestFetched()).thenCallRealMethod();
        assertNull(r.findLatestFetched());
    }

    @Test
    void restRepositoryReturnsNullOnEmpty() {
        RestRepository r = mock(RestRepository.class);
        when(r.findLatestFetchedInstant()).thenReturn(null);
        when(r.findLatestFetched()).thenCallRealMethod();
        assertNull(r.findLatestFetched());
    }

    @Test
    void endpointRepositoryReturnsNullOnEmpty() {
        EndpointRepository r = mock(EndpointRepository.class);
        when(r.findLatestFetchedInstant()).thenReturn(null);
        when(r.findLatestFetched()).thenCallRealMethod();
        assertNull(r.findLatestFetched());
    }
}
