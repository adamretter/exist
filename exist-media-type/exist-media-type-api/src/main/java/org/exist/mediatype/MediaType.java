/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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
package org.exist.mediatype;

import javax.annotation.Nullable;

/**
 * Information about a Media Type (aka MIME Type)
 * and how resources of that type should be stored into
 * the database.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public interface MediaType {

    /**
     * Get the identifier of the Media Type.
     *
     * For example {@code application/xml}.
     *
     * @return the identifier of the Media Type
     */
    String getIdentifier();

    /**
     * Get the file extensions that are known
     * to be associated with the Media Type.
     *
     * @return the known file extensions associated with the Media Type, or null if there are none
     */
    @Nullable String[] getKnownFileExtensions();

    /**
     * Get the database storage type that should
     * be used for resources of this Media Type.
     *
     * @return the database storage type of the Media Type
     */
    StorageType getStorageType();


    // <editor-fold desc="List of common Media Type Identifiers">
    String APPLICATION_JAVA_ARCHIVE = "application/java-archive";
    String APPLICATION_JAVASCRIPT = "application/javascript";
    String APPLICATION_JSON = "application/json";
    String APPLICATION_OCTET_STREAM = "application/octet-stream";
    String APPLICATION_PDF = "application/pdf";
    String APPLICATION_XML = "application/xml";
    String APPLICATION_XML_DTD = "application/xml-dtd";
    String APPLICATION_XHTML_XML = "application/xhtml+xml";
    String APPLICATION_XPROC_XML = "application/xproc+xml";
    String APPLICATION_XSLT_XML = "application/xslt+xml";
    String APPLICATION_XQUERY = "application/xquery";
    String APPLICATION_ZIP = "application/zip";

    String APPLICATION_X_EXPATH_XAR_ZIP = "application/x.expath.xar+zip";
    String APPLICATION_X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";


    String TEXT_HTML = "text/html";
    String TEXT_PLAIN = "text/plain";
    String TEXT_URI_LIST = "text/uri-list";
    // </editor-fold>
}
