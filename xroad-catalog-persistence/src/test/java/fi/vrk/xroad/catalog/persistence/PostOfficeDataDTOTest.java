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

import fi.vrk.xroad.catalog.persistence.dto.PostOfficeData;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;


@SpringBootTest
public class PostOfficeDataDTOTest {

    @Test
    public void testPostOfficeDataDTO() {
        String language = "FI";
        String value = "value";
        LocalDateTime created = LocalDateTime.now();
        LocalDateTime changed = LocalDateTime.now();
        LocalDateTime fetched = LocalDateTime.now();
        PostOfficeData postOfficeData1 = new PostOfficeData();
        postOfficeData1.setLanguage(language);
        postOfficeData1.setValue(value);
        postOfficeData1.setCreated(created);
        postOfficeData1.setChanged(changed);
        postOfficeData1.setFetched(fetched);
        postOfficeData1.setRemoved(null);
        PostOfficeData postOfficeData2 = new PostOfficeData(language, value, created, changed, fetched, null);
        PostOfficeData postOfficeData3 = PostOfficeData.builder().language(language).value(value).created(created)
                .changed(changed).fetched(fetched).removed(null).build();
        assertEquals(postOfficeData1, postOfficeData2);
        assertEquals(postOfficeData1, postOfficeData3);
        assertEquals(postOfficeData2, postOfficeData3);
        assertEquals(language, postOfficeData1.getLanguage());
        assertEquals(value, postOfficeData1.getValue());
        assertEquals(created, postOfficeData1.getCreated());
        assertEquals(changed, postOfficeData1.getChanged());
        assertEquals(fetched, postOfficeData1.getFetched());
        assertNotEquals(0, postOfficeData1.hashCode());
        assertEquals(true, postOfficeData1.equals(postOfficeData2));
        assertEquals(language, postOfficeData2.getLanguage());
        assertEquals(value, postOfficeData2.getValue());
        assertEquals(created, postOfficeData2.getCreated());
        assertEquals(changed, postOfficeData2.getChanged());
        assertEquals(fetched, postOfficeData2.getFetched());
        assertNotEquals(0, postOfficeData2.hashCode());
        assertEquals(true, postOfficeData2.equals(postOfficeData3));
        assertEquals(language, postOfficeData3.getLanguage());
        assertEquals(value, postOfficeData3.getValue());
        assertEquals(created, postOfficeData3.getCreated());
        assertEquals(changed, postOfficeData3.getChanged());
        assertEquals(fetched, postOfficeData3.getFetched());
        assertNotEquals(0, postOfficeData3.hashCode());
        assertEquals(true, postOfficeData3.equals(postOfficeData1));
    }

}


