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
package org.exist.util;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.mediatype.MediaType;
import org.exist.mediatype.StorageType;
import org.junit.*;
import org.junit.runner.RunWith;

/**
 * Test case for mime-type mapping.
 * Tests the distribution edition of mime-types.xml
 * as well as variants that exploit the default mime type feature
 * 
 * @author Peter Ciuffetti
 */
@RunWith(ParallelRunner.class)
public class MimeTableTest  {

	/**
	 * This test checks the behavior of MimeTable.java
	 * with respect to the distribution version of mime-types.xml.
	 * The distribution version of mime-types.xml does not use the
	 * default mime type capability.
	 */
    @Test
	public void testDistributionVersionOfMimeTypesXml() throws URISyntaxException {
		final Path mimeTypes = Paths.get(getClass().getResource("mime-types.xml").toURI());

		MimeTable mimeTable = new MimeTable(mimeTypes);
		assertNotNull("Mime table not found", mimeTable);

		MediaType mt = mimeTable.fromFileName("test.xml").orElse(null);
		assertNotNull("Mime type not found for test.xml", mt);
		assertEquals("Incorrect mime type", "application/xml", mt.getIdentifier());
		assertEquals("Incorrect resource type", StorageType.XML, mt.getStorageType());

		mt = mimeTable.fromFileName("test.html").orElse(null);
		assertNotNull("Mime type not found for test.html", mt);
		assertEquals("Incorrect mime type", "text/html", mt.getIdentifier());
		assertEquals("Incorrect resource type", StorageType.XML, mt.getStorageType());

		mt = mimeTable.fromFileName("test.jpg").orElse(null);
		assertNotNull("Mime type not found for test.jpg", mt);
		assertEquals("Incorrect mime type", "image/jpeg", mt.getIdentifier());
		assertEquals("Incorrect resource type", StorageType.BINARY, mt.getStorageType());

		mt = mimeTable.fromFileName("foo").orElse(null);
		assertNull("Should return null mime type for file without extension", mt);

		mt = mimeTable.fromFileName("foo.bar").orElse(null);
		assertNull("Should return null mime type for file with extension not configured in mime-types.xml", mt);
	}

	/**
	 * This test checks the behavior of the mime-types@default-resource-type attribute
	 * The test config assigns all resources to application/xml
	 */
    @Test
	public void testWithDefaultResourceTypeFeature() throws URISyntaxException {
		final Path mimeTypes = Paths.get(getClass().getResource("mime-types-xml-default.xml").toURI());

		MimeTable mimeTable = new MimeTable(mimeTypes);
		assertNotNull("Mime table not found", mimeTable);

		MediaType mt;

		mt = mimeTable.fromFileName("test.xml").orElse(null);
		assertNotNull("Mime type not found for test.xml", mt);
		assertEquals("Incorrect mime type", "application/xml", mt.getIdentifier());
		assertEquals("Incorrect resource type", StorageType.XML, mt.getStorageType());

		mt = mimeTable.fromFileName("test.html").orElse(null);
		assertNotNull("Mime type not found for test.html", mt);
		assertEquals("Incorrect mime type", "application/xml", mt.getIdentifier());
		assertEquals("Incorrect resource type", StorageType.XML, mt.getStorageType());

		mt = mimeTable.fromFileName("test.jpg").orElse(null);
		assertNotNull("Mime type not found for test.jpg", mt);
		assertEquals("Incorrect mime type", "application/xml", mt.getIdentifier());
		assertEquals("Incorrect resource type", StorageType.XML, mt.getStorageType());

		mt = mimeTable.fromFileName("foo").orElse(null);
		assertNotNull("Mime type not found for foo", mt);
		assertEquals("Incorrect mime type", "application/xml", mt.getIdentifier());
		assertEquals("Incorrect resource type", StorageType.XML, mt.getStorageType());

		mt = mimeTable.fromFileName("foo.bar").orElse(null);
		assertNotNull("Mime type not found for test.jpg", mt);
		assertEquals("Incorrect mime type", "application/xml", mt.getIdentifier());
		assertEquals("Incorrect resource type", StorageType.XML, mt.getStorageType());
	}

	/**
	 * This test checks the behavior of the mime-types@default-mime-type attribute
	 * The test config assigns all resources to foo/bar (BINARY)
	 */
    @Test
	public void testWithDefaultMimeTypeFeature() throws URISyntaxException {
		final Path mimeTypes = Paths.get(getClass().getResource("mime-types-foo-default.xml").toURI());

		MimeTable mimeTable = new MimeTable(mimeTypes);
		assertNotNull("Mime table not found", mimeTable);

		MediaType mt;

		mt = mimeTable.fromFileName("test.xml").orElse(null);
		assertNotNull("Mime type not found for test.xml", mt);
		assertEquals("Incorrect mime type", "foo/bar", mt.getIdentifier());
		assertEquals("Incorrect resource type", StorageType.BINARY, mt.getStorageType());

		mt = mimeTable.fromFileName("test.html").orElse(null);
		assertNotNull("Mime type not found for test.html", mt);
		assertEquals("Incorrect mime type", "foo/bar", mt.getIdentifier());
		assertEquals("Incorrect resource type", StorageType.BINARY, mt.getStorageType());

		mt = mimeTable.fromFileName("test.jpg").orElse(null);
		assertNotNull("Mime type not found for test.jpg", mt);
		assertEquals("Incorrect mime type", "foo/bar", mt.getIdentifier());
		assertEquals("Incorrect resource type", StorageType.BINARY, mt.getStorageType());

		mt = mimeTable.fromFileName("foo").orElse(null);
		assertNotNull("Mime type not found for foo", mt);
		assertEquals("Incorrect mime type", "foo/bar", mt.getIdentifier());
		assertEquals("Incorrect resource type", StorageType.BINARY, mt.getStorageType());

		mt = mimeTable.fromFileName("foo.bar").orElse(null);
		assertNotNull("Mime type not found for test.jpg", mt);
		assertEquals("Incorrect mime type", "foo/bar", mt.getIdentifier());
		assertEquals("Incorrect resource type", StorageType.BINARY, mt.getStorageType());
	}
}
