/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.functions.request;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.exist.mediatype.MediaType;
import org.exist.xmldb.UserManagementService;
import java.io.IOException;
import org.exist.http.RESTTest;
import org.exist.xmldb.EXistResource;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import static org.apache.commons.codec.binary.Base64.encodeBase64String;

/**
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class GetDataTest extends RESTTest {

    private final static String CONTAINER_ELEMENT_NAME = "data";
    private final static String XQUERY = wrapInElement("{request:get-data()}");
    private final static String XQUERY_FILENAME = "test-get-data.xql";

    private static Collection root;

    private static String wrapInElement(String value) {
        return value == null || value.length() == 0 ? "<" + CONTAINER_ELEMENT_NAME + "/>" : "<" + CONTAINER_ELEMENT_NAME + ">" + value + "</" + CONTAINER_ELEMENT_NAME + ">";
    }

    @BeforeClass
    public static void beforeClass() throws XMLDBException {
        root = DatabaseManager.getCollection("xmldb:exist://localhost:" + existWebServer.getPort() + "/xmlrpc/db", "admin", "");
        BinaryResource res = (BinaryResource)root.createResource(XQUERY_FILENAME, "BinaryResource");
        ((EXistResource) res).setMimeType(MediaType.APPLICATION_XQUERY);
        res.setContent(XQUERY);
        root.storeResource(res);
        UserManagementService ums = (UserManagementService)root.getService("UserManagementService", "1.0");
        ums.chmod(res, 0777);
    }

    @AfterClass
    public static void afterClass() throws XMLDBException {
        BinaryResource res = (BinaryResource)root.getResource(XQUERY_FILENAME);
        root.removeResource(res);
    }

    @Test
    public void retrieveEmpty() throws IOException {
        Request post = Request.Post(getCollectionRootUri() + "/" + XQUERY_FILENAME)
            .addHeader("Content-Type", MediaType.APPLICATION_OCTET_STREAM);

        testRequest(post, wrapInElement("").getBytes());
    }
    
    @Test
    public void retrieveBinary() throws IOException {
        final String testData = "12345";

        Request post = Request.Post(getCollectionRootUri() + "/" + XQUERY_FILENAME)
                .bodyByteArray(testData.getBytes(UTF_8), ContentType.APPLICATION_OCTET_STREAM);

        testRequest(post, wrapInElement(encodeBase64String(testData.getBytes(UTF_8)).trim()).getBytes());
    }

    @Test
    public void retrieveXml() throws IOException {
        final String testData = "<a><b><c>hello</c></b></a>";

        Request post = Request.Post(getCollectionRootUri() + "/" + XQUERY_FILENAME)
                .bodyByteArray(testData.getBytes(UTF_8), ContentType.TEXT_XML);

        testRequest(post, wrapInElement("\n\t" + testData + "\n").getBytes(), true);
    }

    @Test
    public void retrieveMalformedXmlFallbackToString() throws IOException {
        final String testData = "<a><b></a>";

        Request post = Request.Post(getCollectionRootUri() + "/" + XQUERY_FILENAME)
            .bodyByteArray(testData.getBytes(UTF_8), ContentType.TEXT_XML);

        testRequest(post, wrapInElement(testData.replace("<", "&lt;").replace(">", "&gt;")).getBytes());
    }

    @Test
    public void retrieveString() throws IOException {
        final String testData = "12345";

        Request post = Request.Post(getCollectionRootUri() + "/" + XQUERY_FILENAME)
                .bodyByteArray(testData.getBytes(UTF_8));

        testRequest(post, wrapInElement(testData).getBytes());
    }
    
    private void testRequest(Request method, final byte expectedResponse[]) throws IOException {
        testRequest(method, expectedResponse, false);
    }
    
    private void testRequest(Request method, byte expectedResponse[], boolean stripWhitespaceAndFormatting) throws IOException {
        final HttpResponse response = method.execute().returnResponse();

            assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

            try (final UnsynchronizedByteArrayOutputStream os = new UnsynchronizedByteArrayOutputStream()) {
                response.getEntity().writeTo(os);

                byte actualResponse[] = os.toByteArray();
                if(stripWhitespaceAndFormatting) {
                    expectedResponse = new String(expectedResponse).replace("\n", "").replace("\t", "").replace(" ", "").getBytes(UTF_8);
                    actualResponse = new String(actualResponse).replace("\n", "").replace("\t","").replace(" ", "").getBytes(UTF_8);
                }
                assertArrayEquals(expectedResponse, actualResponse);
            }
    }
}
