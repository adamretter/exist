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

import io.lacuna.bifurcan.LinearSet;
import net.jcip.annotations.NotThreadSafe;
import net.jcip.annotations.ThreadSafe;
import org.exist.mediatype.MediaType;
import org.exist.mediatype.StorageType;

import javax.annotation.Nullable;

/**
 * Implementation of a Media Type.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@ThreadSafe
public class MediaTypeImpl implements MediaType {

    private final String identifier;
    @Nullable private final String[] knownFileExtensions;
    private final StorageType storageType;

    /**
     * MediaTypeImpl is 100% immutable.
     *
     * Instead of calling the constructor you can incrementally
     * build one via {@link Builder}.
     */
    private MediaTypeImpl(final String identifier, @Nullable final String[] knownFileExtensions, final StorageType storageType) {
        this.identifier = identifier;
        this.knownFileExtensions = knownFileExtensions;
        this.storageType = storageType;
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public @Nullable String[] getKnownFileExtensions() {
        return knownFileExtensions;
    }

    @Override
    public StorageType getStorageType() {
        return storageType;
    }

    /**
     * Builder pattern which allows us to
     * ultimately construct an Immutable MediaTypeImpl.
     */
    @NotThreadSafe
    static class Builder {
        private final String identifier;
        private final StorageType storageType;
        private final LinearSet<String> knownFileExtensions = new LinearSet<>();

        private Builder(final String identifier, final StorageType storageType) {
            this.identifier = identifier;
            this.storageType = storageType;
        }

        /**
         * Initiate the build of a MediaTypeImpl.
         *
         * @param identifier the Media Type identifier
         * @param storageType the database storage that should be used for resources of this Media Type
         */
        static Builder forMediaType(final String identifier, final StorageType storageType) {
            return new Builder(identifier, storageType);
        }

        /**
         * Add a file extension for the Media Type.
         *
         * @param fileExtension a file extension
         * @return this
         */
        Builder addFileExtension(final String fileExtension) {
            knownFileExtensions.add(fileExtension);
            return this;
        }

        /**
         * Build the Immutable MediaType.
         *
         * @return an immutable MediaType.
         */
        MediaType build() {
            final String[] aryKnownFileExtensions = knownFileExtensions.size() > 0 ? knownFileExtensions.toArray(size -> new String[size]) : null;
            return new MediaTypeImpl(identifier, aryKnownFileExtensions, storageType);
        }
    }
}
