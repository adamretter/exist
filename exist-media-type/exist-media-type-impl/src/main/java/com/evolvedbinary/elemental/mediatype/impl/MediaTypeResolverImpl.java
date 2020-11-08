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
package com.evolvedbinary.elemental.mediatype.impl;

import com.sun.activation.registries.MimeTypeEntry;
import io.lacuna.bifurcan.IList;
import io.lacuna.bifurcan.LinearMap;
import jakarta.activation.MimetypesFileTypeMap;
import net.jcip.annotations.ThreadSafe;
import org.exist.mediatype.MediaType;
import org.exist.mediatype.MediaTypeResolver;
import org.exist.mediatype.StorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.exist.mediatype.MediaType.APPLICATION_OCTET_STREAM;

/**
 * Implementation of a Media Type Resolver.
 *
 * Sources the mappings between Media Types and File Extensions
 * from a {@link MimetypesFileTypeMap}, and then further
 * maps those onto database storage using a {@link MediaTypeMapper}.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@ThreadSafe
public class MediaTypeResolverImpl implements MediaTypeResolver {

    private static final Logger LOG = LoggerFactory.getLogger(MediaTypeResolverImpl.class);

    private final io.lacuna.bifurcan.IMap<String, MediaType> extensionsIndex;
    private final io.lacuna.bifurcan.IMap<String, MediaType> identifiersIndex;

    private final MediaType defaultMediaType;

    public MediaTypeResolverImpl(final ApplicationMimetypesFileTypeMap fileTypeMap, final MediaTypeMapper mediaTypeMapper) {
        final IList<Set<Map.Entry<String, MimeTypeEntry>>> allEntries = fileTypeMap.getAllEntries();
        if (allEntries == null) {
            LOG.warn("Could not load file type maps. No mime types are known to the system!");
            this.extensionsIndex = io.lacuna.bifurcan.Map.empty();
            this.identifiersIndex = io.lacuna.bifurcan.Map.empty();
            this.defaultMediaType = null;
            return;
        }
        assert(!allEntries.isLinear());

        final LinearMap<String, MediaTypeImpl.Builder> mutExtensionsIndex = new LinearMap<>();
        final LinearMap<String, MediaTypeImpl.Builder> mutIdentifiersIndex = new LinearMap<>();

        for (final Set<Map.Entry<String, MimeTypeEntry>> entrySet : allEntries) {
            for (final Map.Entry<String, MimeTypeEntry> entry : entrySet) {
                final String fileExtension = entry.getKey().toLowerCase();
                final String identifier = entry.getValue().getMIMEType().toLowerCase();

                MediaTypeImpl.Builder mediaTypeBuilder = mutExtensionsIndex.get(fileExtension, null);
                if (mediaTypeBuilder == null) {
                    // not present in extensionsIndex

                    mediaTypeBuilder = mutIdentifiersIndex.get(identifier, null);
                    if (mediaTypeBuilder == null) {

                        // not present in identifiersIndex or extensionsIndex
                        mediaTypeBuilder = MediaTypeImpl.Builder
                                .forMediaType(identifier, mediaTypeMapper.resolveStorageType(identifier))
                                .addFileExtension(fileExtension);

                        mutExtensionsIndex.put(fileExtension, mediaTypeBuilder);
                        mutIdentifiersIndex.put(identifier, mediaTypeBuilder);

                    } else {

                        // present in identifiersIndex, but not extensionsIndex
                        mediaTypeBuilder.addFileExtension(fileExtension);
                        mutExtensionsIndex.put(fileExtension, mediaTypeBuilder);
                    }
                } else {
                    // present in extensionsIndex, can assume MediaType impl has this extension

                    if (!mutIdentifiersIndex.contains(identifier)) {
                        // present in extensionsIndex, but not present in identifiersIndex
                        mutIdentifiersIndex.put(identifier, mediaTypeBuilder);
                    }
                }
            }
        }

        this.extensionsIndex = mutExtensionsIndex.mapValues((k, v) -> v.build()).forked();
        this.identifiersIndex = mutIdentifiersIndex.mapValues((k, v) -> v.build()).forked();

        assert(!extensionsIndex.isLinear());
        assert(!identifiersIndex.isLinear());

        this.defaultMediaType = identifiersIndex.get(APPLICATION_OCTET_STREAM)
                .orElseGet(() ->  MediaTypeImpl.Builder.forMediaType(APPLICATION_OCTET_STREAM, StorageType.BINARY).build());
    }

    @Override
    public Optional<MediaType> fromFileName(final Path path) {
        if (path == null) {
            return Optional.empty();
        }
        return fromFileNameImpl(path.getFileName().toString());
    }

    @Override
    public Optional<MediaType> fromFileName(String path) {
        if (path == null) {
            return Optional.empty();
        }

        // if this is a path, just take the last segment i.e. the filename
        int pathSepIdx = -1;
        if ((pathSepIdx = path.lastIndexOf('/')) > -1) {
            path = path.substring(pathSepIdx + 1);
        } else if ((pathSepIdx = path.lastIndexOf('\\')) > -1) {
            path = path.substring(pathSepIdx + 1);
        }

        return fromFileNameImpl(path);
    }

    private Optional<MediaType> fromFileNameImpl(final String fileName) {
            int idx = fileName.lastIndexOf("."); // period index

            if (idx == -1) {
                return Optional.empty();
            }

            final String extension = fileName.substring(idx + 1);
            if (extension.length() == 0) {
                return Optional.empty();
            }

            return extensionsIndex.get(extension.toLowerCase()).map(v -> (MediaType)v);
    }

    @Override
    public Optional<MediaType> fromString(final String mediaType) {
        if (mediaType == null) {
            return Optional.empty();
        }

        return identifiersIndex.get(mediaType.toLowerCase()).map(v -> (MediaType)v);
    }

    @Override
    public MediaType forUnknown() {
        return defaultMediaType;
    }
}
