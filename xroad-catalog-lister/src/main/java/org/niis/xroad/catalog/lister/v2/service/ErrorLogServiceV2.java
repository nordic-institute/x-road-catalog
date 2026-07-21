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

import org.niis.xroad.catalog.lister.v2.converter.ErrorLogConverter;
import org.niis.xroad.catalog.lister.v2.dto.ErrorLogDto;
import org.niis.xroad.catalog.lister.v2.util.DateTimeUtil;
import org.niis.xroad.catalog.persistence.entity.ErrorLog;
import org.niis.xroad.catalog.persistence.repository.ErrorLogRepositoryV2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ErrorLogServiceV2 {

    private final ErrorLogRepositoryV2 errorLogRepository;
    private final ErrorLogConverter converter;

    public ErrorLogServiceV2(ErrorLogRepositoryV2 errorLogRepository, ErrorLogConverter converter) {
        this.errorLogRepository = errorLogRepository;
        this.converter = converter;
    }

    public Page<ErrorLogDto> get(String memberClass, String memberCode, String subsystemCode,
                                 String serviceCode, String serviceVersion,
                                 LocalDateTime since, LocalDateTime until, Pageable pageable) {
        // Contract: serviceVersion is the raw URL segment. Java null means "no version-level query".
        // "null" sentinel means "null-version filter". Any other string is an explicit version.
        Page<ErrorLog> rows;
        if (serviceCode != null && serviceVersion != null) {
            String resolvedVersion = DateTimeUtil.resolveVersionSentinel(serviceVersion);
            if (resolvedVersion == null) {
                rows = errorLogRepository.findAnyByNullVersion(since, until,
                        memberClass, memberCode, subsystemCode, serviceCode, pageable);
            } else {
                rows = errorLogRepository.findAnyByVersion(since, until,
                        memberClass, memberCode, subsystemCode, serviceCode, resolvedVersion, pageable);
            }
        } else if (serviceCode != null) {
            // serviceVersion is Java null — caller is querying at service level (all versions).
            rows = errorLogRepository.findAnyByService(since, until,
                    memberClass, memberCode, subsystemCode, serviceCode, pageable);
        } else if (subsystemCode != null) {
            rows = errorLogRepository.findAnyBySubsystem(since, until,
                    memberClass, memberCode, subsystemCode, pageable);
        } else if (memberCode != null) {
            rows = errorLogRepository.findAnyByMember(since, until, memberClass, memberCode, pageable);
        } else if (memberClass != null) {
            rows = errorLogRepository.findAnyByMemberClass(since, until, memberClass, pageable);
        } else {
            rows = errorLogRepository.findAnyInRange(since, until, pageable);
        }
        return rows.map(converter::toDto);
    }
}
