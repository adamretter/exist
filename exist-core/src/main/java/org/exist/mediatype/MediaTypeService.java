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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.BrokerPoolService;
import org.exist.storage.BrokerPoolServiceException;
import org.exist.util.Configuration;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Service for accessing the Media Type Resolver.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class MediaTypeService implements BrokerPoolService {
    private static final Logger LOG = LogManager.getLogger(MediaTypeService.class);

    private MediaTypeResolver mediaTypeResolver;

    @Override
    public void configure(final Configuration configuration) throws BrokerPoolServiceException {
        final ServiceLoader<MediaTypeResolverFactory> serviceloader =
                ServiceLoader.load(MediaTypeResolverFactory.class);

        List<Path> configDirsList = null;

        // 1. config from application's `etc/` folder
        final Path applicationConfigDir = configuration.getConfigFilePath().map(Path::getParent).orElse(null);
        if (applicationConfigDir != null) {
            if (configDirsList == null) {
                configDirsList = new ArrayList<>();
            }
            configDirsList.add(applicationConfigDir);
        }

        // 2. default config from classpath
        final URL mediaTypeMappingsFileUrl = getClass().getResource("media-type-mappings.xml");
        if (mediaTypeMappingsFileUrl != null) {
            try {
                final Path defaultFromClasspathDir = Paths.get(mediaTypeMappingsFileUrl.toURI()).getParent();
                if (configDirsList == null) {
                    configDirsList = new ArrayList<>();
                }
                configDirsList.add(defaultFromClasspathDir);
            } catch (final URISyntaxException e) {
                LOG.error(e.getMessage(), e);
            }
        }

        if (configDirsList == null) {
            throw new BrokerPoolServiceException("Unable to find media-type-mappings.xml");
        }
        final Path[] configDirs = configDirsList.toArray(new Path[configDirsList.size()]);

        for (final Iterator<MediaTypeResolverFactory> it = serviceloader.iterator(); it.hasNext(); ) {
            final MediaTypeResolverFactory mediaTypeResolverFactory = it.next();
            MediaTypeResolver mediaTypeResolver = null;
            try {
                mediaTypeResolver = mediaTypeResolverFactory.newMediaTypeResolver(configDirs);
            } catch (final MediaTypeResolverFactory.InstantiationException e) {
                LOG.error(e.getMessage(), e);
            }

            if (mediaTypeResolver != null) {
                this.mediaTypeResolver = mediaTypeResolver;
                break;  // NOTE: at present there is only one implementation provided by Elemental.
            }
        }

        if (this.mediaTypeResolver == null) {
            throw new BrokerPoolServiceException("Unable to find a suitable implementation of MediaTypeResolverFactory");
        }
    }

    /**
     * Get the configured Media Type Resolver.
     *
     * @return the media type resolver.
     */
    public MediaTypeResolver getMediaTypeResolver() {
        return mediaTypeResolver;
    }
}
