package fi.vrk.xroad.catalog.lister;

import fi.vrk.xroad.catalog.persistence.dto.SecurityServerInfo;
import fi.vrk.xroad.catalog.persistence.CatalogService;
import fi.vrk.xroad.catalog.persistence.dto.ListOfServicesRequest;
import fi.vrk.xroad.catalog.persistence.dto.ListOfServicesResponse;
import fi.vrk.xroad.catalog.persistence.dto.MemberDataList;
import fi.vrk.xroad.catalog.persistence.dto.ServiceStatistics;
import fi.vrk.xroad.catalog.persistence.dto.ServiceStatisticsRequest;
import fi.vrk.xroad.catalog.persistence.dto.ServiceStatisticsResponse;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.validation.Valid;
import javax.ws.rs.core.*;
import javax.xml.parsers.ParserConfigurationException;

@RestController
@RequestMapping("/api")
@PropertySource("classpath:lister.properties")
public class ServiceController {

    @Value("${xroad-catalog.max-history-length-in-days}")
    private Integer maxHistoryLengthInDays;

    @Value("${xroad-catalog.shared-params-file}")
    private String sharedParamsFile;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private SharedParamsParser sharedParamsParser;

    @PostMapping(path = "/getServiceStatistics", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> getServiceStatistics(@Valid @RequestBody ServiceStatisticsRequest request) {
        if (request.getHistoryAmountInDays() == null
                || request.getHistoryAmountInDays() < 1 || request.getHistoryAmountInDays() > maxHistoryLengthInDays) {
            return new ResponseEntity<>(
                    "Input parameter historyAmountInDays must be greater "
                            + "than zero and less than the required maximum of " + maxHistoryLengthInDays + " days",
                    HttpStatus.BAD_REQUEST);
        }
        List<ServiceStatistics> serviceStatisticsList = catalogService.getServiceStatistics(request.getHistoryAmountInDays());
        if (serviceStatisticsList != null && !serviceStatisticsList.isEmpty()) {
            return ResponseEntity.ok(ServiceStatisticsResponse.builder().serviceStatisticsList(serviceStatisticsList).build());
        } else {
            return ResponseEntity.noContent().build();
        }
    }

    @PostMapping(path = "/getServiceStatisticsCSV", consumes = "application/json", produces = "text/csv")
    public ResponseEntity<?> getServiceStatisticsCSV(@Valid @RequestBody ServiceStatisticsRequest request) {
        if (request.getHistoryAmountInDays() == null
                || request.getHistoryAmountInDays() < 1 || request.getHistoryAmountInDays() > maxHistoryLengthInDays) {
            return new ResponseEntity<>(
                    "Input parameter historyAmountInDays must be greater "
                            + "than zero and less than the required maximum of " + maxHistoryLengthInDays + " days",
                    HttpStatus.BAD_REQUEST);
        }
        List<ServiceStatistics> serviceStatisticsList = catalogService.getServiceStatistics(request.getHistoryAmountInDays());
        if (serviceStatisticsList != null && !serviceStatisticsList.isEmpty()) {
            try {
                StringWriter sw = new StringWriter();
                CSVPrinter csvPrinter = new CSVPrinter(sw, CSVFormat.DEFAULT
                        .withHeader("Date", "Number of REST services", "Number of SOAP services", "Total distinct services"));
                serviceStatisticsList.forEach(serviceStatistics -> printCSVRecord(csvPrinter,
                        Arrays.asList(serviceStatistics.getCreated().toString(),
                        serviceStatistics.getNumberOfRestServices().toString(),
                        serviceStatistics.getNumberOfSoapServices().toString(),
                        serviceStatistics.getTotalNumberOfDistinctServices().toString())));
                sw.close();
                csvPrinter.close();
                return ResponseEntity.ok()
                        .contentType(org.springframework.http.MediaType.valueOf(MediaType.TEXT_PLAIN))
                        .body(new ByteArrayResource(sw.toString().getBytes()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/getListOfServices", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> getListOfServices(@Valid @RequestBody ListOfServicesRequest request) {
        if (request.getHistoryAmountInDays() == null
                || request.getHistoryAmountInDays() < 1 || request.getHistoryAmountInDays() > maxHistoryLengthInDays) {
            return new ResponseEntity<>(
                    "Input parameter historyAmountInDays must be greater "
                            + "than zero and less than the required maximum of " + maxHistoryLengthInDays + " days",
                    HttpStatus.BAD_REQUEST);
        }

        List<SecurityServerInfo> securityServerList = getSecurityServerData();
        List<MemberDataList> memberDataList = catalogService.getMemberData(request.getHistoryAmountInDays());
        if (memberDataList != null && !memberDataList.isEmpty()) {
            return ResponseEntity.ok(ListOfServicesResponse.builder()
                    .memberData(memberDataList)
                    .securityServerData(securityServerList)
                    .build());
        } else {
            return ResponseEntity.noContent().build();
        }
    }

    @PostMapping(path = "/getListOfServicesCSV", consumes = "application/json", produces = "text/csv")
    public ResponseEntity<?> getListOfServicesCSV(@Valid @RequestBody ListOfServicesRequest request) {
        if (request.getHistoryAmountInDays() == null
                || request.getHistoryAmountInDays() < 1 || request.getHistoryAmountInDays() > maxHistoryLengthInDays) {
            return new ResponseEntity<>(
                    "Input parameter historyAmountInDays must be greater "
                            + "than zero and less than the required maximum of " + maxHistoryLengthInDays + " days",
                    HttpStatus.BAD_REQUEST);
        }
        List<SecurityServerInfo> securityServerList = getSecurityServerData();
        List<MemberDataList> memberDataList = catalogService.getMemberData(request.getHistoryAmountInDays());
        if (memberDataList != null && !memberDataList.isEmpty()) {
            try {
                StringWriter sw = new StringWriter();
                CSVPrinter csvPrinter = new CSVPrinter(sw, CSVFormat.DEFAULT
                        .withHeader("Date", "XRoad instance", "Member class", "Member code",
                                    "Member name", "Member created", "Subsystem code", "Subsystem created",
                                    "Service code", "Service version", "Service created"));
                printListOfServicesCSV(csvPrinter, memberDataList, securityServerList);
                sw.close();
                csvPrinter.close();
                return ResponseEntity.ok()
                        .contentType(org.springframework.http.MediaType.valueOf(MediaType.TEXT_PLAIN))
                        .body(new ByteArrayResource(sw.toString().getBytes()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return ResponseEntity.noContent().build();
    }

    private void printCSVRecord(CSVPrinter csvPrinter, List<String> data) {
        try {
            csvPrinter.printRecord(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void printListOfServicesCSV(CSVPrinter csvPrinter, List<MemberDataList> memberDataList, List<SecurityServerInfo> securityServerList) {
        memberDataList.forEach(memberList -> {
            printCSVRecord(csvPrinter, Arrays.asList(memberList.getDate().toString(), "", "", "", "", "", "", "", "", "", ""));
            memberList.getMemberDataList().forEach(memberData -> {
                String memberCreated = memberData.getCreated().toString();
                String xRoadInstance = memberData.getXRoadInstance();
                String memberClass = memberData.getMemberClass();
                String memberCode = memberData.getMemberCode();
                String memberName = memberData.getName();

                if (memberData.getSubsystemList().isEmpty() || memberData.getSubsystemList() == null) {
                    printCSVRecord(csvPrinter, Arrays.asList("", xRoadInstance, memberClass, memberCode, memberName,
                            memberCreated, "", "", "", "", ""));
                }

                memberData.getSubsystemList().forEach(subsystemData -> {

                    if (subsystemData.getServiceList().isEmpty() || subsystemData.getServiceList() == null) {
                        printCSVRecord(csvPrinter, Arrays.asList("", xRoadInstance, memberClass, memberCode, memberName, memberCreated,
                                subsystemData.getSubsystemCode(), subsystemData.getCreated().toString(), "", "", ""));
                    }

                    subsystemData.getServiceList().forEach(serviceData -> printCSVRecord(csvPrinter, Arrays.asList(
                            "", xRoadInstance, memberClass, memberCode, memberName, memberCreated, subsystemData.getSubsystemCode(),
                            subsystemData.getCreated().toString(), serviceData.getServiceCode(), serviceData.getServiceVersion(),
                            serviceData.getCreated().toString())));
                });
            });
        });

        if (securityServerList != null && !securityServerList.isEmpty()) {
            printCSVRecord(csvPrinter, Arrays.asList("", "Security server (SS) info:", "", "", "", "", "", "", "", "", ""));
            printCSVRecord(csvPrinter, Arrays.asList("member class", "member code", "server code", "address", "", "", "", "", "","", ""));

            securityServerList.forEach(securityServerInfo -> printCSVRecord(csvPrinter, Arrays.asList(securityServerInfo.getMemberClass(),
                    securityServerInfo.getMemberCode(), securityServerInfo.getServerCode(), securityServerInfo.getAddress()
                    , "", "", "", "", "", "", "")));
        }
    }

    private List<SecurityServerInfo> getSecurityServerData() {
        List<SecurityServerInfo> securityServerList = new ArrayList<>();
        try {
            Set<SecurityServerInfo> securityServerInfos = sharedParamsParser.parse(sharedParamsFile);
            if (securityServerInfos.iterator().hasNext()) {
                securityServerList = new ArrayList<>(securityServerInfos);
            }
        } catch (ParserConfigurationException | IOException | SAXException e) {
            throw new RuntimeException(e);
        }
        return securityServerList;
    }
}
