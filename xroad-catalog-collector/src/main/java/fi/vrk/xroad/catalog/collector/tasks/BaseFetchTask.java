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
package fi.vrk.xroad.catalog.collector.tasks;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;

import org.springframework.context.ApplicationContext;

import fi.vrk.xroad.catalog.collector.util.XRoadRestServiceIdentifierType;
import fi.vrk.xroad.catalog.collector.wsimport.XRoadServiceIdentifierType;
import fi.vrk.xroad.catalog.persistence.CatalogService;
import fi.vrk.xroad.catalog.persistence.entity.ServiceId;
import fi.vrk.xroad.catalog.persistence.entity.SubsystemId;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BaseFetchTask<T> {

    protected final CatalogService catalogService;

    private final BlockingQueue<T> inputQueue;

    private final Semaphore semaphore;

    protected BaseFetchTask(final ApplicationContext applicationContext, final BlockingQueue<T> inputQueue,
            final int poolSize) {
        this.catalogService = applicationContext.getBean(CatalogService.class);

        this.inputQueue = inputQueue;

        this.semaphore = new Semaphore(poolSize);
    }

    public void run() {
        log.info("Starting {} with pool size {}", getClass().getSimpleName(), semaphore.availablePermits());
        try {
            while (true) {
                log.debug("Polling for input ... ");

                // take() blocks until an element becomes available or it gets interrupted
                T input = inputQueue.take();
                semaphore.acquire();
                Thread.ofVirtual().start(() -> wrappedFetch(input));
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while handling inputs, stopping {}", getClass().getSimpleName(), e);
            Thread.currentThread().interrupt();
        }
    }

    private void wrappedFetch(final T input) {
        try {
            fetch(input);
        } catch (Exception e) {
            log.error("Error fetching data", e);
        } finally {
            semaphore.release();
        }
    }

    protected abstract void fetch(T input);

    protected ServiceId createServiceId(XRoadServiceIdentifierType service) {
        return new ServiceId(service.getServiceCode(),
                service.getServiceVersion());
    }

    protected ServiceId createServiceId(XRoadRestServiceIdentifierType service) {
        return new ServiceId(service.getServiceCode(),
                service.getServiceVersion());
    }

    protected SubsystemId createSubsystemId(XRoadServiceIdentifierType service) {
        return new SubsystemId(service.getXRoadInstance(),
                service.getMemberClass(),
                service.getMemberCode(),
                service.getSubsystemCode());
    }

    protected SubsystemId createSubsystemId(XRoadRestServiceIdentifierType service) {
        return new SubsystemId(service.getXRoadInstance(),
                service.getMemberClass(),
                service.getMemberCode(),
                service.getSubsystemCode());
    }
}
