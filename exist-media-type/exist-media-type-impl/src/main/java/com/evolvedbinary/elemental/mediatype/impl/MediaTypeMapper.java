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

import io.lacuna.bifurcan.IList;
import io.lacuna.bifurcan.LinearList;
import net.jcip.annotations.NotThreadSafe;
import org.exist.mediatype.StorageType;
import com.evolvedbinary.elemental.mediatype.impl.beans.MediaType;
import com.evolvedbinary.elemental.mediatype.impl.beans.MediaTypeMappings;
import com.evolvedbinary.elemental.mediatype.impl.beans.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Maps Media Types to database Storage Types.
 *
 * <b>Mappings file search order:</b><p>
 * The MediaTypeMapper looks in various places in the user's
 * system for media type mappings files. When requests are made
 * to resolve media types to storage types, it searches
 * mappings types files in the following order:
 * <ol>
 * <li> The file <code>.media-type-mappings.xml</code> in the user's home directory.
 * <li> One or more files named <code>media-type-mappings.xml</code> in the application's config directory(s).
 * </ol>
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@NotThreadSafe
public class MediaTypeMapper {

    private static final Logger LOG = LoggerFactory.getLogger(MediaTypeMapper.class);

    private final Function<String, StorageType> matchers[];

    public MediaTypeMapper(@Nullable final Path... configDirs) {
        final IList<Path> mappingsPaths = getMappingsFiles(configDirs);
        this.matchers = loadMatchers(mappingsPaths);
    }

    private static IList<Path> getMappingsFiles(final Path... configDirs) {
        final LinearList<Path> mappingsFiles = new LinearList<>();

        LOG.trace("MediaTypeMapper: load HOME");
        final String user_home = System.getProperty("user.home");
        if (user_home != null) {
            final Path mappingsFile = Paths.get(user_home).resolve(".media-type-mappings.xml");
            if (!Files.exists(mappingsFile)) {
                LOG.warn("No media-type-mappings.xml found at: " + mappingsFile.toAbsolutePath().toString() + ", skipping...");
            }
            mappingsFiles.addLast(mappingsFile);
        }

        LOG.trace("MediaTypeMapper: load application");
        if (configDirs != null) {
            for (final Path configDir : configDirs) {
                final Path mappingsFile = configDir.resolve("media-type-mappings.xml");
                if (!Files.exists(mappingsFile)) {
                    LOG.warn("No custom media-type-mappings.xml found at: " + mappingsFile.toAbsolutePath().toString() + ", skipping...");
                }
                mappingsFiles.addLast(mappingsFile);
            }
        }

        return mappingsFiles.forked();
    }

    private static Function<String, StorageType>[] loadMatchers(final IList<Path> mappingsFiles) {
        assert(!mappingsFiles.isLinear());

        final JAXBContext context;
        final Unmarshaller unmarshaller;
        try {
            context = JAXBContext.newInstance(MediaTypeMappings.class);
            unmarshaller = context.createUnmarshaller();
        } catch (final JAXBException e) {
            LOG.error("Unable to instantiate JAXB Unmarshaller: " + e.getMessage(), e);
            return new Function[0];
        }

        final LinearList<Function<String, StorageType>> matchersList = new LinearList<>();
        for (final Path mappingsPath : mappingsFiles) {
            try {
                final MediaTypeMappings mappings = (MediaTypeMappings) unmarshaller.unmarshal(mappingsPath.toUri().toURL());
                if (mappings == null || mappings.getStorage() == null || mappings.getStorage().isEmpty()) {
                    LOG.error("No  mappings found in " + mappingsPath + ", skipping...");
                    continue;
                }

                for (final Storage storage : mappings.getStorage()) {
                    for (final MediaType mediaType : storage.getMediaType()) {

                        final StorageType storageType = toStorageType(storage.getType());
                        final Function<String, StorageType> matcher;
                        switch (mediaType.getMatch()) {
                            case STARTS_WITH:
                                matcher = identifier -> {
                                    if (identifier.startsWith(mediaType.getValue())) {
                                        return storageType;
                                    } else {
                                        return null;
                                    }
                                };
                                break;

                            case FULL:
                                matcher = identifier -> {
                                    if (identifier.equals(mediaType.getValue())) {
                                        return storageType;
                                    } else {
                                        return null;
                                    }
                                };
                                break;

                            case PATTERN:
                                final Pattern pattern = Pattern.compile(mediaType.getValue());
                                final Matcher patternMatcher = pattern.matcher("");
                                matcher = identifier -> {
                                    patternMatcher.reset(identifier);
                                    if (patternMatcher.matches()) {
                                        return storageType;
                                    } else {
                                        return null;
                                    }
                                };
                                break;

                            default:
                                throw new IllegalArgumentException();
                        }

                        matchersList.addLast(matcher);
                    }
                }

            } catch (final MalformedURLException | JAXBException e) {
                LOG.error("Skipping " + mappingsPath + " due to error: " + e.getMessage(), e);
            }
        }

        return matchersList.toArray(size -> new Function[size]);
    }

    private static StorageType toStorageType(final com.evolvedbinary.elemental.mediatype.impl.beans.StorageType type) {
        switch (type) {
            case XML:
                return StorageType.XML;

            case BINARY:
                return StorageType.BINARY;

            default:
                throw new IllegalArgumentException();
        }
    }

    public StorageType resolveStorageType(final String mediaTypeIdentifier) {
        for (final Function<String, StorageType> matcher : matchers) {
            final StorageType storageType = matcher.apply(mediaTypeIdentifier);
            if (storageType != null) {
                return storageType;
            }
        }

        return StorageType.BINARY;  // TODO(AR) this is the default/unknown... should we return null or unknown here?
    }
}
