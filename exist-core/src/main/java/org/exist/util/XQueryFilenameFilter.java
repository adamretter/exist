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

import org.exist.mediatype.MediaType;
import org.exist.mediatype.MediaTypeResolver;
import org.exist.mediatype.StorageType;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Predicate;

import static org.exist.mediatype.MediaType.APPLICATION_XQUERY;

public class XQueryFilenameFilter implements FilenameFilter {
    private final MediaTypeResolver mediaTypeResolver;

    public XQueryFilenameFilter(final MediaTypeResolver mediaTypeResolver) {
        this.mediaTypeResolver = mediaTypeResolver;
    }

    @Override
    public boolean accept(final File dir, final String name) {
        final Optional<MediaType> maybeMediaType = mediaTypeResolver.fromFileName(name);
        return maybeMediaType.map(mt -> mt.getStorageType() == StorageType.BINARY && mt.getIdentifier().equals(APPLICATION_XQUERY))
                .orElse(false);
    }

    public static Predicate<Path> asPredicate(final MediaTypeResolver mediaTypeResolver) {
        return path -> {
            if(!Files.isDirectory(path)) {
                final Optional<MediaType> maybeMediaType = mediaTypeResolver.fromFileName(path);
                return maybeMediaType.map(mt -> mt.getStorageType() == StorageType.BINARY && mt.getIdentifier().equals(APPLICATION_XQUERY))
                        .orElse(false);
            }
            return false;
        };
    }
}
