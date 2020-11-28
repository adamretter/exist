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

import org.exist.mediatype.MediaTypeResolver;
import org.exist.mediatype.StorageType;

import javax.swing.filechooser.FileFilter;
import java.io.File;

public class NotXMLFileFilter extends FileFilter {

    private final MediaTypeResolver mediaTypeResolver;
    private final String description;

    public NotXMLFileFilter(final MediaTypeResolver mediaTypeResolver, final String description) {
        this.mediaTypeResolver = mediaTypeResolver;
        this.description = description;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public boolean accept(final File f) {
        if (f.isDirectory()) {
            return true;
        }
        return !mediaTypeResolver.fromFileName(f.toPath()).map(mt -> mt.getStorageType() == StorageType.XML)
                .orElse(false);
    }
}
