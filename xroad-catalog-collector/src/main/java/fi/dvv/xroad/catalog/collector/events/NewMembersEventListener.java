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
package fi.dvv.xroad.catalog.collector.events;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.catalog.collector.events.NewMembersEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;

@Slf4j
@Component
public class NewMembersEventListener {
    @Autowired
    private BlockingQueue<String> fetchCompaniesQueue;
    @Autowired
    private BlockingQueue<String> fetchOrganizationsQueue;

    @EventListener
    public void handleNewMembersEvent(NewMembersEvent event) {
        log.info("New members detected: {}", event.newMembersCodes());
        fetchCompaniesQueue.addAll(event.newMembersCodes());
        log.info("{} new members sent to the FetchCompaniesTask", event.newMembersCodes().size());
        fetchOrganizationsQueue.addAll(event.newMembersCodes());
        log.info("{} new members sent to the FetchOrganizationsTask", event.newMembersCodes().size());
    }
}
