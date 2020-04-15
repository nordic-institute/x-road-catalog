/**
 * The MIT License
 * Copyright (c) 2016, Population Register Centre (VRK)
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
package fi.vrk.xroad.catalog.persistence.service;

import fi.vrk.xroad.catalog.persistence.entity.*;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * CRUD methods for catalog objects. no business logic (e.g. hash calculation),
 * just persistence-related logic.
 * Catalog entities (member, subsystem, service, wsdl) have time stamps created, updated and deleted.
 * They are used so that "updated" is changed always when entity is updated in any way, including
 * creation and deletion. This is important since getActiveMembers(Date updatedSince) only checks
 * updated-field and ignores created & deleted.
 */
public interface CatalogService {

    /**
     * Gets all non-removed members, regardless of when they were changed.
     * Fetches all data from graph member->subsystem->service.
     * Does NOT fetch wsdl.
     */
    Iterable<Member> getActiveMembers();

    /**
     * Same as {@link #getActiveMembers()} except that returns also
     * removed items
     */
    Iterable<Member> getAllMembers();

    /**
     * Gets all non-removed members that have been changed (created) after given time.
     * Change is determined by field "changed".
     * Fetches all data from graph member->subsystem->service->wsdl.
     * @param changedAfter Only interested in member after this
     * @return Iterable of Member entities
     */
    Iterable<Member> getActiveMembers(LocalDateTime changedAfter);

    /**
     * Same as {@link #getActiveMembers(LocalDateTime)} except that returns also removed items
     * @param changedAfter Only interested in member after this
     * @return Iterable of Member entities
     */
    Iterable<Member> getAllMembers(LocalDateTime changedAfter);

    /**
     * Returns full Member object
     * @param xRoadInstance name of the instance
     * @param memberClass member class
     * @param memberCode member  code
     * @return Member entity
     */
    Member getMember(String xRoadInstance, String memberClass, String memberCode);

    /**
     * Returns the full Wsdl object. Only returns active ones, removed are not found.
     * @return Wsdl, if any, null if not found
     * @throws RuntimeException if multiple matches found.
     */
    Wsdl getWsdl(String externalId);

    /**
     * Returns the full OpenApi object. Only returns active ones, removed are not found.
     * @return Wsdl, if any, null if not found
     * @throws RuntimeException if multiple matches found.
     */
    OpenApi getOpenApi(String externalId);

    /**
     * Returns the full Service object. Only returns active ones, removed are not found.
     * @return Service, if any, null if not found
     * @throws RuntimeException if multiple matches found.
     */
    Service getService(String serviceCode, String subsystemCode);

    /**
     * Stores given members and subsystems. This should be the full dataset of both items
     * - items not included in the parameters are marked as removed, if the existed previously.
     *
     * "Full service": updates all status timestamps (created/changed/fetched/removed) automatically,
     * and knows whether to update existing items or create new ones.
     *
     * Does not touch the child items (service, wsdl). If creating new subsystems, the
     * service collection will be empty.
     *
     * @param members all Members that currently exist. If some members are missing from
     *                the collection, they are (marked) removed. Member should have
     *                member.subsystems collection populated, and each subsystem should
     *                have subsystem.member populated as well.
     */
    void saveAllMembersAndSubsystems(Collection<Member> members);

    /**
     * Stores services for given subsystem. Does not modify the associated Subsystem or
     * the wsdl.
     * @param subsystem identifier info for subsystem. Also needs to have subsystem.member
     *                  populated properly.
     * @param service services
     */
    void saveServices(SubsystemId subsystem, Collection<Service> service);

    /**
     * Saves given wsdl data. The wsdl can either be a new one, or an update to an existing one.
     * Updates "changed" field based on whether data is different compared to last time.
     * @param subsystemId identifier of the subsystem
     * @param serviceId identifier of the service
     * @param wsdl the actual wsdl
     */
    void saveWsdl(SubsystemId subsystemId, ServiceId serviceId, String wsdl);

    /**
     * Saves given openApi data. The openApi can either be a new one, or an update to an existing one.
     * Updates "changed" field based on whether data is different compared to last time.
     * @param subsystemId identifier of the subsystem
     * @param serviceId identifier of the service
     * @param openApi the actual openApi
     */
    void saveOpenApi(SubsystemId subsystemId, ServiceId serviceId, String openApi);

    /**
     * Saves given organization data. The organization can either be a new one, or an update to an existing one.
     * Updates "changed" field based on whether data is different compared to last time.
     * @return saved organization
     * @param newValue the actual organization
     */
    Organization saveOrganization(Organization newValue);

    /**
     * Saves given organizationName data.
     * @param newValue the organizationName
     */
    void saveOrganizationName(OrganizationName newValue);

    /**
     * Saves given organizationDescription data.
     * @param newValue the organizationDescription
     */
    void saveOrganizationDescription(OrganizationDescription newValue);

    /**
     * Saves given email data.
     * @param newValue the actual email
     */
    void saveEmail(Email newValue);

    /**
     * Saves given phoneNumber data.
     * @param newValue the actual phoneNumber
     */
    void savePhoneNumber(PhoneNumber newValue);

    /**
     * Saves given webPage data.
     * @param newValue the actual webPage
     */
    void saveWebPage(WebPage newValue);

    /**
     * Saves given address data.
     * @return saved Address
     * @param newValue the actual address
     */
    Address saveAddress(Address newValue);

    /**
     * Saves given StreetAddress data.
     * @return saved StreetAddress
     * @param newValue the actual StreetAddress
     */
    StreetAddress saveStreetAddress(StreetAddress newValue);

    /**
     * Saves given PostOfficeBoxAddress data.
     * @return saved PostOfficeBoxAddress
     * @param newValue the actual PostOfficeBoxAddress
     */
    PostOfficeBoxAddress savePostOfficeBoxAddress(PostOfficeBoxAddress newValue);

    /**
     * Saves given StreetAddressMunicipality data.
     * @return saved StreetAddressMunicipality
     * @param newValue the actual StreetAddressMunicipality
     */
    StreetAddressMunicipality saveStreetAddressMunicipality(StreetAddressMunicipality newValue);

    /**
     * Saves given PostOfficeBoxAddressMunicipality data.
     * @return saved PostOfficeBoxAddressMunicipality
     * @param newValue the actual PostOfficeBoxAddressMunicipality
     */
    PostOfficeBoxAddressMunicipality savePostOfficeBoxAddressMunicipality(PostOfficeBoxAddressMunicipality newValue);


    /**
     * Saves given StreetAddressMunicipalityName data.
     * @return saved StreetAddressMunicipalityName
     * @param newValue the actual StreetAddressMunicipalityName
     */
    StreetAddressMunicipalityName saveStreetAddressMunicipalityName(StreetAddressMunicipalityName newValue);

    /**
     * Saves given PostOfficeBoxAddressMunicipalityName data.
     * @return saved PostOfficeBoxAddressMunicipalityName
     * @param newValue the actual PostOfficeBoxAddressMunicipalityName
     */
    PostOfficeBoxAddressMunicipalityName savePostOfficeBoxAddressMunicipalityName(
            PostOfficeBoxAddressMunicipalityName newValue);

    /**
     * Saves given StreetAddressAdditionalInformation data.
     * @return saved StreetAddressAdditionalInformation
     * @param newValue the actual StreetAddressAdditionalInformation
     */
    StreetAddressAdditionalInformation saveStreetAddressAdditionalInformation(StreetAddressAdditionalInformation newValue);

    /**
     * Saves given PostOfficeBoxAddressAdditionalInformation data.
     * @return saved PostOfficeBoxAddressAdditionalInformation
     * @param newValue the actual PostOfficeBoxAddressAdditionalInformation
     */
    PostOfficeBoxAddressAdditionalInformation savePostOfficeBoxStreetAddressAdditionalInformation(
            PostOfficeBoxAddressAdditionalInformation newValue);

    /**
     * Saves given StreetAddressPostOffice data.
     * @return saved StreetAddressPostOffice
     * @param newValue the actual StreetAddressPostOffice
     */
    StreetAddressPostOffice saveStreetAddressPostOffice(StreetAddressPostOffice newValue);

    /**
     * Saves given PostOffice data.
     * @return saved PostOffice
     * @param newValue the actual PostOffice
     */
    PostOffice savePostOffice(PostOffice newValue);

    /**
     * Saves given PostOfficeBox data.
     * @return saved PostOfficeBox
     * @param newValue the actual PostOfficeBox
     */
    PostOfficeBox savePostOfficeBox(PostOfficeBox newValue);

    /**
     * Saves given Street data.
     * @return saved Street
     * @param newValue the actual Street
     */
    Street saveStreet(Street newValue);

}
