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
import com.sun.activation.registries.MimeTypeFile;
import io.lacuna.bifurcan.IList;
import io.lacuna.bifurcan.LinearList;
import jakarta.activation.MimetypesFileTypeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * An implementation of {@link jakarta.activation.FileTypeMap} which
 * extends {@link jakarta.activation.MimetypesFileTypeMap} to also
 * read from an application specific file.
 *
 * <b>MIME types file search order:</b><p>
 * The ApplicationMimetypesFileTypeMap looks in various places in the user's
 * system for MIME types file entries. When requests are made
 * to search for MIME types in the ApplicationMimetypesFileTypeMap, it searches
 * MIME types files in the following order:
 * <ol>
 * <li> Programmatically added entries to the ApplicationMimetypesFileTypeMap instance.
 * <li> The file <code>.mime.types</code> in the user's home directory.
 * <li> One or more files named <code>mime.types</code> in the application's config directory(s).
 * <li> The file <code>mime.types</code> in the Java runtime.
 * <li> The file or resources named <code>META-INF/mime.types</code>.
 * <li> The file or resource named <code>META-INF/mimetypes.default</code>
 * (usually found only in the <code>activation.jar</code> file).
 * </ol>
 *
 * See {@link MimetypesFileTypeMap} for further information.
 *
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class ApplicationMimetypesFileTypeMap extends MimetypesFileTypeMap {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationMimetypesFileTypeMap.class);

    public ApplicationMimetypesFileTypeMap(@Nullable final Path... configDirs) {
        final Vector<MimeTypeFile> dbv = new Vector<>(6);	// usually 6 or less databases
        MimeTypeFile mf = null;
        dbv.add(null);		// place holder for PROG entry

        LOG.trace("MimetypesFileTypeMap: load HOME");
        try {
            final String user_home = System.getProperty("user.home");

            if (user_home != null) {
                String path = user_home + File.separator + ".mime.types";
                mf = loadFileRefl(path);
                if (mf != null) {
                    dbv.add(mf);
                }
            }
        } catch (final SecurityException ex) {}

        LOG.trace("ApplicationMimetypesFileTypeMap: load application");
        if (configDirs != null) {
            for (final Path configDir : configDirs) {
                final Path mimeTypesPath = configDir.resolve("mime.types");
                if (!Files.exists(mimeTypesPath)) {
                    LOG.warn("No custom mime.types found at: " + mimeTypesPath.toAbsolutePath().toString() + ", skipping...");
                }

                mf = loadFileRefl(mimeTypesPath.toAbsolutePath().toString());
                if (mf != null) {
                    dbv.add(mf);
                }
            }
        }

        LOG.trace("MimetypesFileTypeMap: load SYS");
        try {
            // check system's home
            final String confDir = confDirRefl();
            if (confDir != null) {
                mf = loadFileRefl(confDir + "mime.types");
                if (mf != null) {
                    dbv.add(mf);
                }
            }
        } catch (final SecurityException ex) {}

        LOG.trace("MimetypesFileTypeMap: load JAR");
        // load from the app's jar file
        loadAllResourcesRefl(dbv, "META-INF/mime.types");

        LOG.trace("MimetypesFileTypeMap: load DEF");
        mf = loadResourceRefl("/META-INF/mimetypes.default");

        if (mf != null) {
            dbv.add(mf);
        }

        final MimeTypeFile[] DB = new MimeTypeFile[dbv.size()];
        dbv.copyInto(DB);
        setDBRefl(DB);
    }

    private MimeTypeFile loadFileRefl(final String name) {
        try {
            final Method loadFileMethod = MimetypesFileTypeMap.class.getDeclaredMethod("loadFile", String.class);
            loadFileMethod.setAccessible(true);
            return (MimeTypeFile) loadFileMethod.invoke(this, name);
        } catch (final NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            LOG.error(e.getMessage(), e);
            return null;
        }
    }

    private static String confDirRefl() {
        try {
            final Field confDirField = MimetypesFileTypeMap.class.getDeclaredField("confDir");
            confDirField.setAccessible(true);
            return (String) confDirField.get(null);
        } catch (final NoSuchFieldException | IllegalAccessException e) {
            LOG.error(e.getMessage(), e);
            return null;
        }
    }

    private void loadAllResourcesRefl(final Vector v, final String name) {
        try {
            final Method loadAllResourcesMethod = MimetypesFileTypeMap.class.getDeclaredMethod("loadAllResources", Vector.class, String.class);
            loadAllResourcesMethod.setAccessible(true);
            loadAllResourcesMethod.invoke(this, v, name);
        } catch (final NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private MimeTypeFile loadResourceRefl(final String name) {
        try {
            final Method loadResourceMethod = MimetypesFileTypeMap.class.getDeclaredMethod("loadResource", String.class);
            loadResourceMethod.setAccessible(true);
            return (MimeTypeFile) loadResourceMethod.invoke(this, name);
        } catch (final NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            LOG.error(e.getMessage(), e);
            return null;
        }
    }

    private void setDBRefl(final MimeTypeFile[] DB) {
        try {
            final Field dbField = MimetypesFileTypeMap.class.getDeclaredField("DB");
            dbField.setAccessible(true);
            dbField.set(this, DB);
        } catch (final NoSuchFieldException | IllegalAccessException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    /**
     * Get all the entries from the MimetypesFileTypeMap.
     *
     * @return the entries from the MimetypesFileTypeMap.
     */
    IList<Set<Map.Entry<String, MimeTypeEntry>>> getAllEntries() {
        final MimeTypeFile[] mimeTypeFiles;
        try {
            final Field dbField = MimetypesFileTypeMap.class.getDeclaredField("DB");
            dbField.setAccessible(true);
            mimeTypeFiles = (MimeTypeFile[]) dbField.get(this);
        } catch (final NoSuchFieldException | IllegalAccessException e) {
            LOG.error(e.getMessage(), e);
            return null;
        }

        if (mimeTypeFiles == null) {
            return null;
        }

        final LinearList<Set<Map.Entry<String, MimeTypeEntry>>> entries = new LinearList<>();
        final Field typeHashField;
        try {
            typeHashField = MimeTypeFile.class.getDeclaredField("type_hash");
        } catch (final NoSuchFieldException e) {
            LOG.error(e.getMessage(), e);
            return null;
        }
        typeHashField.setAccessible(true);

        try {
            for (final MimeTypeFile mimeTypeFile : mimeTypeFiles) {
                if (mimeTypeFile != null) {
                    final Hashtable<String, MimeTypeEntry> typeHash = (Hashtable<String, MimeTypeEntry>) typeHashField.get(mimeTypeFile);
                    entries.addLast(typeHash.entrySet());
                }
            }
        } catch (final IllegalAccessException e) {
            LOG.error("Unable to access type_hash from MimeTypeFile", e);
            return null;
        }

        return entries.forked();
    }
}
