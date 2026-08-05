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
package org.niis.xroad.catalog.collector.service;

import org.niis.xroad.catalog.persistence.entity.ErrorLog;
import org.niis.xroad.catalog.persistence.entity.Member;
import org.niis.xroad.catalog.persistence.entity.Service;
import org.niis.xroad.catalog.persistence.entity.ServiceId;
import org.niis.xroad.catalog.persistence.entity.SubsystemId;

import java.util.Collection;
import java.util.Set;

/**
 * CRUD methods for catalog objects. no business logic (e.g. hash calculation),
 * just persistence-related logic.
 * Catalog entities (member, subsystem, service, wsdl) have time stamps created,
 * updated and deleted.
 * They are used so that "updated" is changed always when entity is updated in
 * any way, including
 * creation and deletion. This is important since getActiveMembers(Date
 * updatedSince) only checks
 * updated-field and ignores created & deleted.
 */
public interface CatalogService {

    /**
     * Stores given members and subsystems. This should be the full dataset of both
     * items
     * - items not included in the parameters are marked as removed, if the existed
     * previously.
     *
     * "Full service": updates all status timestamps
     * (created/changed/fetched/removed) automatically,
     * and knows whether to update existing items or create new ones.
     *
     * Does not touch the child items (service, wsdl). If creating new subsystems,
     * the
     * service collection will be empty.
     *
     * @param members all Members that currently exist. If some members are missing
     *                from
     *                the collection, they are (marked) removed. Member should have
     *                member.subsystems collection populated, and each subsystem
     *                should
     *                have subsystem.member populated as well.
     * @return Set of Member entities representing new members saved to the database
     */
    Set<Member> saveAllMembersAndSubsystems(Collection<Member> members);

    /**
     * Stores services for given subsystem. Does not modify the associated Subsystem
     * or
     * the wsdl.
     * 
     * @param subsystem identifier info for subsystem. Also needs to have
     *                  subsystem.member
     *                  populated properly.
     * @param service   services
     */
    void saveServices(SubsystemId subsystem, Collection<Service> service);

    /**
     * Saves given wsdl data. The wsdl can either be a new one, or an update to an
     * existing one.
     * Updates "changed" field based on whether data is different compared to last
     * time.
     * 
     * A blank WSDL is ignored and recorded in the error log, so a failed fetch cannot
     * destroy previously collected content.
     *
     * @param subsystemId identifier of the subsystem
     * @param serviceId   identifier of the service
     * @param wsdl        the actual wsdl
     */
    void saveWsdl(SubsystemId subsystemId, ServiceId serviceId, String wsdl);

    /**
     * Saves given openApi data. The openApi can either be a new one, or an update
     * to an existing one.
     * Updates "changed" field based on whether data is different compared to last
     * time.
     * 
     * A blank OpenApi descriptor is ignored and recorded in the error log, so a failed fetch cannot
     * destroy previously collected content.
     *
     * @param subsystemId identifier of the subsystem
     * @param serviceId   identifier of the service
     * @param openApi     the actual openApi
     */
    void saveOpenApi(SubsystemId subsystemId, ServiceId serviceId, String openApi);

    /**
     * Saves given rest data. The rest can either be a new one, or an update to an
     * existing one.
     * Updates "changed" field based on whether data is different compared to last
     * time.
     *
     * The rest parameter is the endpoint metadata JSON the collector serializes for
     * the service, not a descriptor fetched from the service itself.
     *
     * @param subsystemId identifier of the subsystem
     * @param serviceId   identifier of the service
     * @param rest        collector-serialized endpoint metadata JSON for the service
     */
    void saveRest(SubsystemId subsystemId, ServiceId serviceId, String rest);

    /**
     * Saves given rest data. The rest can either be a new one, or an update to an
     * existing one.
     * Updates "changed" field based on whether data is different compared to last
     * time.
     * 
     * @param subsystemId identifier of the subsystem
     * @param serviceId   identifier of the service
     * @param method      method info
     * @param path        path info
     */
    void saveEndpoint(SubsystemId subsystemId, ServiceId serviceId, String method, String path);

    /**
     * Marks all entries in the Endpoints table as removed
     * so that when new endpoints are being fetched, those will be marked as
     * non-removed
     * and when some endpoints are missing in the future, the ones still present in
     * the table
     * will remain as removed
     * 
     * @param subsystemId identifier of the subsystem
     * @param serviceId   identifier of the service
     */
    void prepareEndpoints(SubsystemId subsystemId, ServiceId serviceId);

    /**
     * Saves given errorLog data.
     * 
     * @param errorLog the actual errorLog
     * @return error log
     */
    ErrorLog saveErrorLog(ErrorLog errorLog);

    /**
     * Deletes old log entries
     * 
     * @param daysBefore older than daysBefore
     */
    void deleteOldErrorLogEntries(Integer daysBefore);

    /**
     * Returns a batch of Member codes for members that haven't had their Company or
     * Organization data updated for
     * the specified number of days.
     *
     * @param daysSinceLastUpdate number of days since last update
     * @param batchSize           number of items to return
     * @return List of Member codes
     */
    Set<String> getMembersRequiringExternalUpdate(int daysSinceLastUpdate, int batchSize);

}
