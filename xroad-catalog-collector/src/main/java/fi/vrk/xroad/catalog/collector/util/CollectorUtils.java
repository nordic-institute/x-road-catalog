/**
 *
 *  The MIT License
 *
 *  Copyright (c) 2023- Nordic Institute for Interoperability Solutions (NIIS)
 *  Copyright (c) 2016-2023 Finnish Digital Agency
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 *
 */
package fi.vrk.xroad.catalog.collector.util;

import java.time.LocalDate;
import java.time.LocalDateTime;

import fi.vrk.xroad.catalog.collector.wsimport.ClientType;
import fi.vrk.xroad.catalog.persistence.entity.ErrorLog;

public final class CollectorUtils {

    private CollectorUtils() {
    }

    public static boolean shouldFetchCompanies(boolean fetchUnlimited, int fetchHourAfter, int fetchHourBefore) {
        if (fetchUnlimited) {
            return true;
        }
        return isTimeBetweenHours(fetchHourAfter, fetchHourBefore);
    }

    public static boolean isTimeBetweenHours(int fetchHourAfter, int fetchHourBefore) {
        LocalDateTime today = LocalDateTime.now();
        LocalDateTime fetchTimeFrom = LocalDate.now().atTime(fetchHourAfter, 0);
        LocalDateTime fetchTimeTo = LocalDate.now().atTime(fetchHourBefore, 0);
        return (today.isAfter(fetchTimeFrom) && today.isBefore(fetchTimeTo));
    }

    public static ErrorLog createErrorLog(ClientType clientType, String message, String code) {
        if (clientType != null) {
            return ErrorLog.builder()
                    .created(LocalDateTime.now())
                    .message(message)
                    .code(code)
                    .xRoadInstance(clientType.getId().getXRoadInstance())
                    .memberClass(clientType.getId().getMemberClass())
                    .memberCode(clientType.getId().getMemberCode())
                    .groupCode(clientType.getId().getGroupCode())
                    .securityCategoryCode(clientType.getId().getSecurityCategoryCode())
                    .serverCode(clientType.getId().getServerCode())
                    .serviceCode(clientType.getId().getServiceCode())
                    .serviceVersion(clientType.getId().getServiceVersion())
                    .subsystemCode(clientType.getId().getSubsystemCode())
                    .build();
        }
        return ErrorLog.builder().created(LocalDateTime.now()).message(message).code(code).build();
    }

}
