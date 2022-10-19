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

import fi.vrk.xroad.catalog.persistence.dto.BusinessAuxiliaryNameData;
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
public class BusinessAuxiliaryNameDataDTOTest {

    @Test
    public void testBusinessAuxiliaryNameDataDTO() {
        LocalDateTime created = LocalDateTime.now();
        LocalDateTime changed = LocalDateTime.now();
        LocalDateTime fetched = LocalDateTime.now();
        LocalDateTime registrationDate = LocalDateTime.now();
        String name = "Name";
        String language = "Finnish";
        long source = 1;
        long version = 1;
        long ordering = 1;
        BusinessAuxiliaryNameData businessAuxiliaryNameData1 = new BusinessAuxiliaryNameData();
        businessAuxiliaryNameData1.setName(name);
        businessAuxiliaryNameData1.setLanguage(language);
        businessAuxiliaryNameData1.setSource(source);
        businessAuxiliaryNameData1.setVersion(version);
        businessAuxiliaryNameData1.setOrdering(ordering);
        businessAuxiliaryNameData1.setCreated(created);
        businessAuxiliaryNameData1.setChanged(changed);
        businessAuxiliaryNameData1.setFetched(fetched);
        businessAuxiliaryNameData1.setRegistrationDate(registrationDate);
        businessAuxiliaryNameData1.setEndDate(null);
        businessAuxiliaryNameData1.setRemoved(null);
        BusinessAuxiliaryNameData businessAuxiliaryNameData2 = new BusinessAuxiliaryNameData(source, ordering, version, name, language,
                registrationDate, null, created, changed, fetched, null);
        BusinessAuxiliaryNameData businessAuxiliaryNameData3 = BusinessAuxiliaryNameData.builder().name(name).language(language)
                .source(source).version(version).ordering(ordering).created(created).changed(changed).fetched(fetched)
                .registrationDate(registrationDate).endDate(null).removed(null).build();
        assertEquals(businessAuxiliaryNameData1, businessAuxiliaryNameData2);
        assertEquals(businessAuxiliaryNameData1, businessAuxiliaryNameData3);
        assertEquals(businessAuxiliaryNameData2, businessAuxiliaryNameData3);
        assertEquals(name, businessAuxiliaryNameData1.getName());
        assertEquals(language, businessAuxiliaryNameData1.getLanguage());
        assertEquals(name, businessAuxiliaryNameData2.getName());
        assertEquals(language, businessAuxiliaryNameData2.getLanguage());
        assertEquals(name, businessAuxiliaryNameData3.getName());
        assertEquals(language, businessAuxiliaryNameData3.getLanguage());
    }

}


