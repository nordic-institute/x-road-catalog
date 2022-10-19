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

import fi.vrk.xroad.catalog.persistence.dto.MemberInfo;
import fi.vrk.xroad.catalog.persistence.dto.SecurityServerData;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;


@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
@Transactional
@Slf4j
public class SecurityServerDataDTOTest {

    @Test
    public void testSecurityServerDataDTO() {
        MemberInfo owner = MemberInfo.builder().build();
        String serverCode = "SS1";
        String address = "https://ss1";
        List<MemberInfo> clients = new ArrayList<>();
        SecurityServerData securityServerData1 = new SecurityServerData();
        securityServerData1.setOwner(owner);
        securityServerData1.setServerCode(serverCode);
        securityServerData1.setAddress(address);
        securityServerData1.setClients(clients);
        SecurityServerData securityServerData2 = new SecurityServerData(owner, serverCode, address, clients);
        SecurityServerData securityServerData3 = SecurityServerData.builder().owner(owner).serverCode(serverCode).address(address).clients(clients).build();
        assertEquals(securityServerData1, securityServerData2);
        assertEquals(securityServerData1, securityServerData3);
        assertEquals(securityServerData2, securityServerData3);
        assertEquals(owner, securityServerData1.getOwner());
        assertEquals(serverCode, securityServerData1.getServerCode());
        assertEquals(address, securityServerData1.getAddress());
        assertEquals(clients, securityServerData1.getClients());
        assertEquals(owner, securityServerData2.getOwner());
        assertEquals(serverCode, securityServerData2.getServerCode());
        assertEquals(address, securityServerData2.getAddress());
        assertEquals(clients, securityServerData2.getClients());
        assertEquals(owner, securityServerData3.getOwner());
        assertEquals(serverCode, securityServerData3.getServerCode());
        assertEquals(address, securityServerData3.getAddress());
        assertEquals(clients, securityServerData3.getClients());
    }

}


