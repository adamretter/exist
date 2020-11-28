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
package org.exist.client;

import java.io.File;
import java.util.Iterator;

import javax.swing.filechooser.FileFilter;

import org.exist.mediatype.MediaType;


/**
 * A FileFilter that filters for files based on their extension
 * Uses the filename extensions defined in mime-types.xml
 * 
 *  Java 6 API has a similar FileNameExtensionFilter
 */
public class MediaTypeFilter extends FileFilter {
    private final MediaType mediaType;

    public MediaTypeFilter(final MediaType mediaType) {
        this.mediaType = mediaType;
    }
	
    @Override
    public boolean accept(final File file) {
        if (file.isDirectory()) { //permit directories to be viewed
            return true;
        }

        final int extensionOffset = file.getName().lastIndexOf('.');	//do-not allow files without an extension
        if (extensionOffset == -1) {
            return false;
        }
		
        // check the extension of the file is known for the MediaType
        final String fileExtension = file.getName().substring(extensionOffset + 1).toLowerCase();
        for (final String extension : mediaType.getKnownFileExtensions()) {
            if (fileExtension.equals(extension)) {
                return true;
            }
        }

        return false;
    }
    
    @Override
    public String getDescription() {
        final StringBuilder description = new StringBuilder(mediaType.getIdentifier());

        final String[] knownFileExtensions = mediaType.getKnownFileExtensions();
        if (knownFileExtensions != null && knownFileExtensions.length > 0) {
            description.append(" (");
            for (int i = 0; i < knownFileExtensions.length; i++) {
                description.append(knownFileExtensions[i]);
                if (i + 1 < knownFileExtensions.length) {
                    description.append(' ');
                }
            }
            description.append(")");
        }

        return description.toString();
    }
}