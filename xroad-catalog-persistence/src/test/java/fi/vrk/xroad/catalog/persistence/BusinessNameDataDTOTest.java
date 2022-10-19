/**
 * The MIT License
 * Copyright (c) 2022, Population Register Centre (VRK)
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
package fi.vrk.xroad.catalog.persistence;

import fi.vrk.xroad.catalog.persistence.dto.BusinessNameData;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
@Transactional
@Slf4j
public class BusinessNameDataDTOTest {

    @Test
    public void testBusinessNameDataDTO() {
        LocalDateTime created = LocalDateTime.now();
        LocalDateTime changed = LocalDateTime.now();
        LocalDateTime fetched = LocalDateTime.now();
        LocalDateTime registrationDate = LocalDateTime.now();
        String name = "Name";
        String language = "Finnish";
        long source = 1;
        long version = 1;
        long ordering = 1;
        BusinessNameData businessNameData1 = new BusinessNameData();
        businessNameData1.setName(name);
        businessNameData1.setLanguage(language);
        businessNameData1.setSource(source);
        businessNameData1.setVersion(version);
        businessNameData1.setOrdering(ordering);
        businessNameData1.setCreated(created);
        businessNameData1.setChanged(changed);
        businessNameData1.setFetched(fetched);
        businessNameData1.setRegistrationDate(registrationDate);
        businessNameData1.setEndDate(null);
        businessNameData1.setRemoved(null);
        BusinessNameData businessNameData2 = new BusinessNameData(source, ordering, version, name, language,
                registrationDate, null, created, changed, fetched, null);
        BusinessNameData businessNameData3 = BusinessNameData.builder().name(name).language(language)
                .source(source).version(version).ordering(ordering).created(created).changed(changed).fetched(fetched)
                .registrationDate(registrationDate).endDate(null).removed(null).build();
        assertEquals(businessNameData1, businessNameData2);
        assertEquals(businessNameData1, businessNameData3);
        assertEquals(businessNameData2, businessNameData3);
        assertEquals(name, businessNameData1.getName());
        assertEquals(language, businessNameData1.getLanguage());
        assertEquals(name, businessNameData2.getName());
        assertEquals(language, businessNameData2.getLanguage());
        assertEquals(name, businessNameData3.getName());
        assertEquals(language, businessNameData3.getLanguage());
    }

}


