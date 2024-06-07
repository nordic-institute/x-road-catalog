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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;

import fi.vrk.xroad.catalog.collector.configuration.TaskPoolConfiguration;
import fi.vrk.xroad.catalog.collector.util.OrganizationUtil;
import fi.vrk.xroad.catalog.collector.wsimport.ClientType;
import fi.vrk.xroad.catalog.persistence.CatalogService;
import fi.vrk.xroad.catalog.persistence.OrganizationService;
import fi.vrk.xroad.catalog.persistence.entity.Address;
import fi.vrk.xroad.catalog.persistence.entity.Email;
import fi.vrk.xroad.catalog.persistence.entity.Organization;
import fi.vrk.xroad.catalog.persistence.entity.OrganizationDescription;
import fi.vrk.xroad.catalog.persistence.entity.OrganizationName;
import fi.vrk.xroad.catalog.persistence.entity.PhoneNumber;
import fi.vrk.xroad.catalog.persistence.entity.PostOffice;
import fi.vrk.xroad.catalog.persistence.entity.PostOfficeBox;
import fi.vrk.xroad.catalog.persistence.entity.PostOfficeBoxAddress;
import fi.vrk.xroad.catalog.persistence.entity.PostOfficeBoxAddressAdditionalInformation;
import fi.vrk.xroad.catalog.persistence.entity.PostOfficeBoxAddressMunicipality;
import fi.vrk.xroad.catalog.persistence.entity.PostOfficeBoxAddressMunicipalityName;
import fi.vrk.xroad.catalog.persistence.entity.Street;
import fi.vrk.xroad.catalog.persistence.entity.StreetAddress;
import fi.vrk.xroad.catalog.persistence.entity.StreetAddressAdditionalInformation;
import fi.vrk.xroad.catalog.persistence.entity.StreetAddressMunicipality;
import fi.vrk.xroad.catalog.persistence.entity.StreetAddressMunicipalityName;
import fi.vrk.xroad.catalog.persistence.entity.StreetAddressPostOffice;
import fi.vrk.xroad.catalog.persistence.entity.WebPage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FetchOrganizationsTask {

    private String fetchOrganizationsUrl;

    private Integer maxOrganizationsPerRequest;

    private Integer fetchOrganizationsLimit;

    private final CatalogService catalogService;

    private final OrganizationService organizationService;

    private final BlockingQueue<ClientType> fetchOrganizationsQueue;

    private final TaskPoolConfiguration taskPoolConfiguration;

    private final Semaphore semaphore;

    public FetchOrganizationsTask(final ApplicationContext applicationContext,
            final BlockingQueue<ClientType> fetchOrganizationsQueue) {
        this.catalogService = applicationContext.getBean(CatalogService.class);
        this.organizationService = applicationContext.getBean(OrganizationService.class);

        this.fetchOrganizationsQueue = fetchOrganizationsQueue;

        this.taskPoolConfiguration = applicationContext.getBean(TaskPoolConfiguration.class);
        this.fetchOrganizationsUrl = taskPoolConfiguration.getFetchOrganizationsUrl();
        this.maxOrganizationsPerRequest = taskPoolConfiguration.getMaxOrganizationsPerRequest();
        this.fetchOrganizationsLimit = taskPoolConfiguration.getFetchOrganizationsLimit();

        this.semaphore = new Semaphore(taskPoolConfiguration.getFetchOrganizationsPoolSize());

    }

    public void run() {
        log.info("Starting {} with pool size {}", getClass().getSimpleName(), semaphore.availablePermits());
        try {
            while (true) {
                log.debug("Waiting for data ... ");

                // take() blocks until an element becomes available or it gets interrupted
                ClientType client = fetchOrganizationsQueue.take();
                semaphore.acquire();
                Thread.ofVirtual().start(() -> fetchOrganizationsForClient(client));
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for data, stopping {}", getClass().getSimpleName(), e);
            Thread.currentThread().interrupt();
        }
    }

    protected void fetchOrganizationsForClient(final ClientType client) {
        try {
            List<String> organizationIds = OrganizationUtil.getOrganizationIdsList(client, fetchOrganizationsUrl,
                    fetchOrganizationsLimit, catalogService);
            int numberOfOrganizations = organizationIds.size();
            log.info("Fetched {} organization GUIDs from {}", numberOfOrganizations, fetchOrganizationsUrl);

            AtomicInteger elementCount = new AtomicInteger();
            List<String> guidsList = new ArrayList<>();
            organizationIds.forEach(id -> {
                guidsList.add(id);
                elementCount.getAndIncrement();
                if (elementCount.get() % maxOrganizationsPerRequest == 0) {
                    saveBatch(OrganizationUtil.getDataByIds(client, guidsList, fetchOrganizationsUrl, catalogService));
                    guidsList.clear();
                }
                if (elementCount.get() == organizationIds.size() && !guidsList.isEmpty()) {
                    saveBatch(OrganizationUtil.getDataByIds(client, guidsList, fetchOrganizationsUrl, catalogService));
                }
            });
            log.info("Processed {} organizations", numberOfOrganizations);
        } catch (Exception e) {
            log.error("Error while fetching organizations for client {}", client, e);
        } finally {
            semaphore.release();
        }

    }

    private void saveBatch(JSONArray data) {
        for (int i = 0; i < data.length(); i++) {
            Organization organization = OrganizationUtil.createOrganization(data.optJSONObject(i));
            JSONObject dataJson = data.optJSONObject(i);
            Organization savedOrganization = organizationService.saveOrganization(organization);
            saveOrganizationNames(dataJson, savedOrganization);
            saveOrganizationDescriptions(dataJson, savedOrganization);
            saveEmails(dataJson, savedOrganization);
            savePhoneNumbers(dataJson, savedOrganization);
            saveWebPages(dataJson, savedOrganization);
            saveAddresses(dataJson, savedOrganization);
        }
    }

    private void saveOrganizationNames(JSONObject data, Organization savedOrganization) {
        List<OrganizationName> organizationNames = OrganizationUtil.createNames(data
                .optJSONArray("organizationNames"));
        organizationNames.forEach(organizationName -> {
            organizationName.setOrganization(savedOrganization);
            organizationService.saveOrganizationName(organizationName);
        });
    }

    private void saveOrganizationDescriptions(JSONObject data, Organization savedOrganization) {
        List<OrganizationDescription> organizationDescriptions = OrganizationUtil.createDescriptions(data
                .optJSONArray("organizationDescriptions"));
        organizationDescriptions.forEach(organizationDescription -> {
            organizationDescription.setOrganization(savedOrganization);
            organizationService.saveOrganizationDescription(organizationDescription);
        });
    }

    private void saveEmails(JSONObject data, Organization savedOrganization) {
        List<Email> emails = OrganizationUtil.createEmails(data.optJSONArray("emails"));
        emails.forEach(email -> {
            email.setOrganization(savedOrganization);
            organizationService.saveEmail(email);
        });
    }

    private void savePhoneNumbers(JSONObject data, Organization savedOrganization) {
        List<PhoneNumber> phoneNumbers = OrganizationUtil.createPhoneNumbers(data.optJSONArray("phoneNumbers"));
        phoneNumbers.forEach(phone -> {
            phone.setOrganization(savedOrganization);
            organizationService.savePhoneNumber(phone);
        });
    }

    private void saveWebPages(JSONObject data, Organization savedOrganization) {
        List<WebPage> webPages = OrganizationUtil.createWebPages(data.optJSONArray("webPages"));
        webPages.forEach(webPage -> {
            webPage.setOrganization(savedOrganization);
            organizationService.saveWebPage(webPage);
        });
    }

    private void saveAddresses(JSONObject data, Organization savedOrganization) {
        List<Address> addresses = OrganizationUtil.createAddresses(data.optJSONArray("addresses"));
        JSONArray addressesListJson = data.optJSONArray("addresses");
        addresses.forEach(address -> {
            address.setOrganization(savedOrganization);
            Address savedAddress = organizationService.saveAddress(address);
            saveAddressDetails(addressesListJson, savedAddress);
        });
    }

    private void saveAddressDetails(JSONArray addressesListJson, Address savedAddress) {
        for (int j = 0; j < addressesListJson.length(); j++) {
            if (addressesListJson.optJSONObject(j).optJSONObject("streetAddress") != null) {
                JSONObject streetAddressJson = addressesListJson.optJSONObject(j).optJSONObject("streetAddress");
                saveStreetAddress(streetAddressJson, savedAddress);
            }

            if (addressesListJson.optJSONObject(j).optJSONObject("postOfficeBoxStreetAddress") != null) {
                JSONObject postOfficeBoxAddressJson = addressesListJson.optJSONObject(j)
                        .optJSONObject("postOfficeBoxStreetAddress");
                savePostOfficeBoxAddress(postOfficeBoxAddressJson, savedAddress);
            }
        }
    }

    private void saveStreetAddress(JSONObject streetAddressJson, Address savedAddress) {
        StreetAddress streetAddress = OrganizationUtil.createStreetAddress(streetAddressJson);
        streetAddress.setAddress(savedAddress);
        StreetAddress savedStreetAddress = organizationService.saveStreetAddress(streetAddress);
        saveStreetAddressMunicipality(streetAddressJson.optJSONObject("municipality"), savedStreetAddress);
        saveStreetAddressAdditionalInformation(streetAddressJson.optJSONArray("additionalInformation"),
                savedStreetAddress);
        saveStreetAddressPostOffice(streetAddressJson.optJSONArray("postOffice"), savedStreetAddress);
        saveStreetAddressStreet(streetAddressJson.optJSONArray("street"), savedStreetAddress);
    }

    private void saveStreetAddressMunicipality(JSONObject municipalityJson, StreetAddress savedStreetAddress) {
        if (municipalityJson != null) {
            StreetAddressMunicipality streetAddressMunicipality = OrganizationUtil
                    .createStreetAddressMunicipality(municipalityJson);
            streetAddressMunicipality.setStreetAddress(savedStreetAddress);
            StreetAddressMunicipality savedStreetAddressMunicipality = organizationService
                    .saveStreetAddressMunicipality(streetAddressMunicipality);

            if (municipalityJson.optJSONArray("name") != null) {
                JSONArray streetAddressMunicipalityNamesJson = municipalityJson.optJSONArray("name");
                List<StreetAddressMunicipalityName> streetAddressMunicipalityNames = OrganizationUtil
                        .createStreetAddressMunicipalityNames(streetAddressMunicipalityNamesJson);
                streetAddressMunicipalityNames.forEach(municipalityName -> {
                    municipalityName.setStreetAddressMunicipality(savedStreetAddressMunicipality);
                    organizationService.saveStreetAddressMunicipalityName(municipalityName);
                });
            }
        }
    }

    private void saveStreetAddressAdditionalInformation(JSONArray additionalInformationJson,
            StreetAddress savedStreetAddress) {
        if (additionalInformationJson != null) {
            List<StreetAddressAdditionalInformation> streetAddressAdditionalInformationList = OrganizationUtil
                    .createStreetAddressAdditionalInformation(additionalInformationJson);
            streetAddressAdditionalInformationList.forEach(additionalInfo -> {
                additionalInfo.setStreetAddress(savedStreetAddress);
                organizationService.saveStreetAddressAdditionalInformation(additionalInfo);
            });
        }
    }

    private void saveStreetAddressPostOffice(JSONArray postOfficeJson, StreetAddress savedStreetAddress) {
        if (postOfficeJson != null) {
            List<StreetAddressPostOffice> streetAddressPostOfficeList = OrganizationUtil
                    .createStreetAddressPostOffices(postOfficeJson);
            streetAddressPostOfficeList.forEach(postOffice -> {
                postOffice.setStreetAddress(savedStreetAddress);
                organizationService.saveStreetAddressPostOffice(postOffice);
            });
        }
    }

    private void saveStreetAddressStreet(JSONArray streetJson, StreetAddress savedStreetAddress) {
        if (streetJson != null) {
            List<Street> streetList = OrganizationUtil.createStreets(streetJson);
            streetList.forEach(street -> {
                street.setStreetAddress(savedStreetAddress);
                organizationService.saveStreet(street);
            });
        }
    }

    private void savePostOfficeBoxAddress(JSONObject postOfficeBoxAddressJson, Address savedAddress) {
        PostOfficeBoxAddress postOfficeBoxAddress = OrganizationUtil
                .createPostOfficeBoxAddress(postOfficeBoxAddressJson);
        postOfficeBoxAddress.setAddress(savedAddress);
        PostOfficeBoxAddress savedPostOfficeBoxAddress = organizationService
                .savePostOfficeBoxAddress(postOfficeBoxAddress);

        savePostOfficeBoxAddressAdditionalInformation(postOfficeBoxAddressJson.optJSONArray("additionalInformation"),
                savedPostOfficeBoxAddress);
        savePostOffice(postOfficeBoxAddressJson.optJSONArray("postOffice"), savedPostOfficeBoxAddress);
        savePostOfficeBoxAddressMunicipality(postOfficeBoxAddressJson.optJSONObject("municipality"),
                savedPostOfficeBoxAddress);
        savePostOfficeBox(postOfficeBoxAddressJson.optJSONArray("postOfficeBox"), savedPostOfficeBoxAddress);
    }

    private void savePostOfficeBoxAddressAdditionalInformation(JSONArray additionalInformationJson,
            PostOfficeBoxAddress savedPostOfficeBoxAddress) {
        if (additionalInformationJson != null) {
            List<PostOfficeBoxAddressAdditionalInformation> postOfficeBoxAddressAdditionalInformationList = OrganizationUtil
                    .createPostOfficeBoxAddressAdditionalInformation(additionalInformationJson);
            postOfficeBoxAddressAdditionalInformationList.forEach(additionalInfo -> {
                additionalInfo.setPostOfficeBoxAddress(savedPostOfficeBoxAddress);
                organizationService.savePostOfficeBoxAddressAdditionalInformation(additionalInfo);
            });
        }
    }

    private void savePostOffice(JSONArray postOfficeJson, PostOfficeBoxAddress savedPostOfficeBoxAddress) {
        if (postOfficeJson != null) {
            List<PostOffice> postOfficeList = OrganizationUtil.createPostOffice(postOfficeJson);
            postOfficeList.forEach(postOffice -> {
                postOffice.setPostOfficeBoxAddress(savedPostOfficeBoxAddress);
                organizationService.savePostOffice(postOffice);
            });
        }
    }

    private void savePostOfficeBoxAddressMunicipality(JSONObject municipalityJson,
            PostOfficeBoxAddress savedPostOfficeBoxAddress) {
        if (municipalityJson != null) {
            PostOfficeBoxAddressMunicipality postOfficeBoxAddressMunicipality = OrganizationUtil
                    .createPostOfficeBoxAddressMunicipality(municipalityJson);
            postOfficeBoxAddressMunicipality.setPostOfficeBoxAddress(savedPostOfficeBoxAddress);
            PostOfficeBoxAddressMunicipality savedPostOfficeBoxAddressMunicipality = organizationService
                    .savePostOfficeBoxAddressMunicipality(postOfficeBoxAddressMunicipality);

            if (municipalityJson.optJSONArray("name") != null) {
                JSONArray postOfficeBoxAddressMunicipalityNamesJson = municipalityJson.optJSONArray("name");
                List<PostOfficeBoxAddressMunicipalityName> postOfficeBoxAddressMunicipalityNames = OrganizationUtil
                        .createPostOfficeBoxAddressMunicipalityNames(postOfficeBoxAddressMunicipalityNamesJson);
                postOfficeBoxAddressMunicipalityNames.forEach(municipalityName -> {
                    municipalityName.setPostOfficeBoxAddressMunicipality(savedPostOfficeBoxAddressMunicipality);
                    organizationService.savePostOfficeBoxAddressMunicipalityName(municipalityName);
                });
            }
        }
    }

    private void savePostOfficeBox(JSONArray postOfficeBoxJson, PostOfficeBoxAddress savedPostOfficeBoxAddress) {
        if (postOfficeBoxJson != null) {
            List<PostOfficeBox> postOfficeBoxList = OrganizationUtil
                    .createPostOfficeBoxes(postOfficeBoxJson);
            postOfficeBoxList.forEach(postOfficeBox -> {
                postOfficeBox.setPostOfficeBoxAddress(savedPostOfficeBoxAddress);
                organizationService.savePostOfficeBox(postOfficeBox);
            });
        }
    }

}
