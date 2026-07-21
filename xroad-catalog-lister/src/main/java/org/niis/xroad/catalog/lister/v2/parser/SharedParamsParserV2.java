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
package org.niis.xroad.catalog.lister.v2.parser;

import org.niis.xroad.catalog.lister.v2.dto.MemberClassInfo;
import org.niis.xroad.catalog.lister.v2.dto.SecurityServerInfoV2;
import org.niis.xroad.catalog.lister.v2.dto.SubsystemNameInfo;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class SharedParamsParserV2 {

    private static final String MEMBER = "member";
    private static final String MEMBER_CLASS = "memberClass";
    private static final String MEMBER_CODE = "memberCode";
    private static final String SUBSYSTEM = "subsystem";
    private static final String SUBSYSTEM_CODE = "subsystemCode";
    private static final String SUBSYSTEM_NAME = "subsystemName";
    private static final String GLOBAL_SETTINGS = "globalSettings";
    private static final String CODE = "code";
    private static final String DESCRIPTION = "description";
    private static final String SECURITY_SERVER = "securityServer";
    private static final String OWNER = "owner";
    private static final String SERVER_CODE = "serverCode";
    private static final String ADDRESS = "address";
    private static final String CLIENT = "client";
    private static final String ID = "id";
    private static final String NAME = "name";

    public List<MemberClassInfo> parseMemberClasses(String sharedParamsFile)
            throws ParserConfigurationException, IOException, SAXException {
        Document document = parseDocument(sharedParamsFile);
        NodeList globalSettings = document.getElementsByTagName(GLOBAL_SETTINGS);
        List<MemberClassInfo> classes = new ArrayList<>();
        if (globalSettings.getLength() == 0) {
            return classes;
        }
        Element gs = (Element) globalSettings.item(0);
        NodeList memberClasses = gs.getElementsByTagName(MEMBER_CLASS);
        for (int i = 0; i < memberClasses.getLength(); i++) {
            Element mc = (Element) memberClasses.item(i);
            // Only consider direct children of globalSettings, not nested ones
            if (!gs.isSameNode(mc.getParentNode())) {
                continue;
            }
            String code = text(mc, CODE);
            String description = text(mc, DESCRIPTION);
            if (code != null) {
                classes.add(MemberClassInfo.builder().code(code).description(description).build());
            }
        }
        return classes;
    }

    public List<SubsystemNameInfo> parseSubsystemNames(String sharedParamsFile)
            throws ParserConfigurationException, IOException, SAXException {
        Document document = parseDocument(sharedParamsFile);
        NodeList members = document.getElementsByTagName(MEMBER);
        List<SubsystemNameInfo> names = new ArrayList<>();
        for (int i = 0; i < members.getLength(); i++) {
            Node memberNode = members.item(i);
            if (memberNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element memberElement = (Element) memberNode;
            // Skip memberClass elements that happen to be under other tags — only top-level <member>
            Node parent = memberElement.getParentNode();
            boolean parentIsConf = parent != null && "conf".equals(localName(parent));
            boolean parentDetached = parent == null || parent.getParentNode() == null;
            if (!parentIsConf && !parentDetached) {
                continue;
            }
            Element memberClassElement = firstChildElement(memberElement, MEMBER_CLASS);
            if (memberClassElement == null) {
                continue;
            }
            String memberClass = text(memberClassElement, CODE);
            String memberCode = text(memberElement, MEMBER_CODE);
            NodeList subsystems = memberElement.getElementsByTagName(SUBSYSTEM);
            for (int j = 0; j < subsystems.getLength(); j++) {
                Element sub = (Element) subsystems.item(j);
                String subsystemCode = text(sub, SUBSYSTEM_CODE);
                String subsystemName = text(sub, SUBSYSTEM_NAME);
                if (subsystemName != null && !subsystemName.isBlank()) {
                    names.add(SubsystemNameInfo.builder()
                            .memberClass(memberClass)
                            .memberCode(memberCode)
                            .subsystemCode(subsystemCode)
                            .subsystemName(subsystemName)
                            .build());
                }
            }
        }
        return names;
    }

    /**
     * Parses security server information from X-Road global configuration shared-params.xml,
     * resolving each security server's owner and clients against the member/subsystem {@code id}
     * attributes referenced by the security server element.
     *
     * @return list of {@link SecurityServerInfoV2} objects, one per {@code securityServer} element
     * @throws ParserConfigurationException when there are issues with parsing the file
     * @throws IOException                  when unable to read input file
     * @throws SAXException                 when there are issues with parsing of XML
     */
    public List<SecurityServerInfoV2> parseSecurityServers(String sharedParamsFile)
            throws ParserConfigurationException, IOException, SAXException {
        Document document = parseDocument(sharedParamsFile);
        NodeList securityServers = document.getElementsByTagName(SECURITY_SERVER);
        NodeList members = document.getElementsByTagName(MEMBER);
        List<SecurityServerInfoV2> result = new ArrayList<>();
        for (int i = 0; i < securityServers.getLength(); i++) {
            Node node = securityServers.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                result.add(toSecurityServerInfo((Element) node, members));
            }
        }
        return result;
    }

    private SecurityServerInfoV2 toSecurityServerInfo(Element securityServerElement, NodeList members) {
        String ownerId = text(securityServerElement, OWNER);
        String serverCode = text(securityServerElement, SERVER_CODE);
        String address = text(securityServerElement, ADDRESS);
        List<String> clientIdList = clientIdList(securityServerElement.getElementsByTagName(CLIENT));
        SecurityServerInfoV2.MemberRef owner = ownerRef(members, ownerId);
        List<SecurityServerInfoV2.ClientRef> clients = clientRefs(members, clientIdList);
        return new SecurityServerInfoV2(serverCode, address, owner, clients);
    }

    private List<String> clientIdList(NodeList clientIds) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < clientIds.getLength(); i++) {
            ids.add(clientIds.item(i).getFirstChild().getNodeValue());
        }
        return ids;
    }

    private SecurityServerInfoV2.MemberRef ownerRef(NodeList members, String ownerId) {
        for (int i = 0; i < members.getLength(); i++) {
            Node node = members.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element memberElement = (Element) node;
                if (memberElement.getAttribute(ID).equals(ownerId)) {
                    return new SecurityServerInfoV2.MemberRef(memberClassOf(memberElement),
                            text(memberElement, MEMBER_CODE), text(memberElement, NAME));
                }
            }
        }
        return new SecurityServerInfoV2.MemberRef(null, null, null);
    }

    /**
     * Resolves each client id against the member/subsystem elements. For every client id the whole
     * member list is scanned from the start: a member-level match short-circuits the scan for that
     * id, but a subsystem-level match only breaks out of the inner subsystem loop — the outer scan
     * continues over the remaining members for the same id. This mirrors the original V1 traversal
     * exactly; it is a no-op in practice since shared-params {@code id} attributes are unique.
     */
    private List<SecurityServerInfoV2.ClientRef> clientRefs(NodeList members, List<String> clientIdList) {
        List<SecurityServerInfoV2.ClientRef> clients = new ArrayList<>();
        for (String clientId : clientIdList) {
            for (int j = 0; j < members.getLength(); j++) {
                Node node = members.item(j);
                if (node.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element memberElement = (Element) node;
                if (memberElement.getAttribute(ID).equals(clientId)) {
                    clients.add(new SecurityServerInfoV2.ClientRef(memberClassOf(memberElement),
                            text(memberElement, MEMBER_CODE), null));
                    break;
                }
                NodeList subsystems = memberElement.getElementsByTagName(SUBSYSTEM);
                for (int k = 0; k < subsystems.getLength(); k++) {
                    Element subsystemElement = (Element) subsystems.item(k);
                    if (subsystemElement.getAttribute(ID).equals(clientId)) {
                        clients.add(new SecurityServerInfoV2.ClientRef(memberClassOf(memberElement),
                                text(memberElement, MEMBER_CODE), text(subsystemElement, SUBSYSTEM_CODE)));
                        break;
                    }
                }
            }
        }
        return clients;
    }

    private String memberClassOf(Element memberElement) {
        Element memberClassElement = firstChildElement(memberElement, MEMBER_CLASS);
        return memberClassElement != null ? text(memberClassElement, CODE) : null;
    }

    private Document parseDocument(String file) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new File(file));
    }

    private String text(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) {
            return null;
        }
        Node child = list.item(0);
        return child != null ? child.getTextContent() : null;
    }

    private Element firstChildElement(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        return list.getLength() == 0 ? null : (Element) list.item(0);
    }

    private String localName(Node node) {
        String name = node.getLocalName();
        return name != null ? name : node.getNodeName().replaceAll(".*:", "");
    }
}
