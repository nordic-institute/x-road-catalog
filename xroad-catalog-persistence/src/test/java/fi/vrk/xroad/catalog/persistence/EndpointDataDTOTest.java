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
package fi.vrk.xroad.catalog.persistence;

import fi.vrk.xroad.catalog.persistence.dto.EndpointData;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SpringBootTest
public class EndpointDataDTOTest {

    @Test
    public void testEndpointDataDTO() {
        String method = "GET";
        String path = "/getData";
        EndpointData endpointData1 = new EndpointData();
        endpointData1.setMethod(method);
        endpointData1.setPath(path);
        EndpointData endpointData2 = new EndpointData(method, path);
        EndpointData endpointData3 = EndpointData.builder().method(method).path(path).build();
        assertEquals(endpointData1, endpointData2);
        assertEquals(endpointData1, endpointData3);
        assertEquals(endpointData2, endpointData3);
        assertEquals(method, endpointData1.getMethod());
        assertEquals(path, endpointData1.getPath());
        assertNotEquals(0, endpointData1.hashCode());
        assertEquals(true, endpointData1.equals(endpointData2));
        assertEquals(method, endpointData2.getMethod());
        assertEquals(path, endpointData2.getPath());
        assertNotEquals(0, endpointData2.hashCode());
        assertEquals(true, endpointData2.equals(endpointData3));
        assertEquals(method, endpointData3.getMethod());
        assertEquals(path, endpointData3.getPath());
        assertNotEquals(0, endpointData3.hashCode());
        assertEquals(true, endpointData3.equals(endpointData1));
    }

}


