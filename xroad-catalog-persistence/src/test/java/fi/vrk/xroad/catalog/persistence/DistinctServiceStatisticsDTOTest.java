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

import fi.vrk.xroad.catalog.persistence.dto.DistinctServiceStatistics;
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
public class DistinctServiceStatisticsDTOTest {

    @Test
    public void testDistinctServiceStatisticsDTO() {
        LocalDateTime created = LocalDateTime.now();
        Long numberOfDistinctServices = 5L;
        DistinctServiceStatistics distinctServiceStatistics1 = new DistinctServiceStatistics();
        distinctServiceStatistics1.setCreated(created);
        distinctServiceStatistics1.setNumberOfDistinctServices(numberOfDistinctServices);
        DistinctServiceStatistics distinctServiceStatistics2 = new DistinctServiceStatistics(created, numberOfDistinctServices);
        DistinctServiceStatistics distinctServiceStatistics3 = DistinctServiceStatistics.builder().created(created)
                .numberOfDistinctServices(numberOfDistinctServices).build();
        assertEquals(distinctServiceStatistics1, distinctServiceStatistics2);
        assertEquals(distinctServiceStatistics1, distinctServiceStatistics3);
        assertEquals(distinctServiceStatistics2, distinctServiceStatistics3);
        assertEquals(created, distinctServiceStatistics1.getCreated());
        assertEquals(numberOfDistinctServices, distinctServiceStatistics1.getNumberOfDistinctServices());
        assertEquals("{\"created\":" + created + ",\"numberOfDistinctServices\":\"" + numberOfDistinctServices + "}",
                distinctServiceStatistics1.toString());
        assertEquals(created, distinctServiceStatistics2.getCreated());
        assertEquals(numberOfDistinctServices, distinctServiceStatistics2.getNumberOfDistinctServices());
        assertEquals("{\"created\":" + created + ",\"numberOfDistinctServices\":\"" + numberOfDistinctServices + "}",
                distinctServiceStatistics2.toString());
        assertEquals(created, distinctServiceStatistics3.getCreated());
        assertEquals(numberOfDistinctServices, distinctServiceStatistics3.getNumberOfDistinctServices());
        assertEquals("{\"created\":" + created + ",\"numberOfDistinctServices\":\"" + numberOfDistinctServices + "}",
                distinctServiceStatistics3.toString());
    }

}


