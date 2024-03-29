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

import fi.vrk.xroad.catalog.persistence.dto.DescriptorInfoList;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SpringBootTest
public class DescriptorInfoListDTOTest {

    @Test
    public void testDescriptorInfoListDTO() {
        DescriptorInfoList descriptorInfoList1 = new DescriptorInfoList();
        descriptorInfoList1.setDescriptorInfoList(new ArrayList<>());
        DescriptorInfoList descriptorInfoList2 = new DescriptorInfoList(new ArrayList<>());
        DescriptorInfoList descriptorInfoList3 = DescriptorInfoList.builder().descriptorInfoList(new ArrayList<>()).build();
        assertEquals(descriptorInfoList1, descriptorInfoList2);
        assertEquals(descriptorInfoList1, descriptorInfoList3);
        assertEquals(descriptorInfoList2, descriptorInfoList3);
        assertNotEquals(0, descriptorInfoList1.hashCode());
        assertEquals(true, descriptorInfoList1.equals(descriptorInfoList2));
        assertNotEquals(0, descriptorInfoList2.hashCode());
        assertEquals(true, descriptorInfoList2.equals(descriptorInfoList3));
        assertNotEquals(0, descriptorInfoList3.hashCode());
        assertEquals(true, descriptorInfoList3.equals(descriptorInfoList1));
    }

}


