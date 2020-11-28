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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import javax.annotation.Nullable;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import com.evolvedbinary.j8fu.OptionalUtil;
import com.evolvedbinary.j8fu.lazy.LazyVal;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.exist.mediatype.MediaType;
import org.exist.mediatype.StorageType;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import org.exist.mediatype.MediaTypeResolver;

import static javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING;

/**
 * Global table of mime types. This singleton class maintains a list
 * of mime types known to the system. It is used to look up the
 * mime type for a specific file extension and to check if a file
 * is an XML or binary resource.
 * 
 * The mime type table is read from a file "mime-types.xml",
 * which should reside in the directory identified in the exist home
 * directory. If no such file is found, the class tries
 * to load the default map from the org.exist.util package via the 
 * class loader.
 * 
 * @author wolf
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class MimeTable implements MediaTypeResolver {

    private final static Logger LOG = LogManager.getLogger(MimeTable.class);

    private static final String FILE_LOAD_FAILED_ERR = "Failed to load mime-type table from ";
    private static final String LOAD_FAILED_ERR = "Failed to load mime-type table from class loader";
    
    private static final String MIME_TYPES_XML = "mime-types.xml";
    private static final String MIME_TYPES_XML_DEFAULT = "org/exist/util/" + MIME_TYPES_XML;    
    
    private static MimeTable instance = null;
    
    /**
     * Returns the singleton.
     *
     * @return the mimetable
     */
    public static MimeTable getInstance() {
        if(instance == null) {
            instance = new MimeTable();
        }
        return instance;
    }
    
    /**
     * Returns the singleton, using a custom mime-types.xml file
     *
     * @param path the path to the mime-types.xml file.
     *
     * @return the mimetable
     */
    public static MimeTable getInstance(final Path path) {
        if (instance == null) {
            instance = new MimeTable(path);
        }
        return instance;
    }

    private MimeType defaultMime = null;
    private Optional<MediaType> maybeDefaultMediaType = Optional.empty();
    private Map<String, MimeType> mimeTypes = new TreeMap<>();
    private Map<String, MimeType> extensions = new TreeMap<>();
    private Map<String, String> preferredExtension = new TreeMap<>();
    
    public MimeTable() {
        load();
    }
    
    public MimeTable(final Path path) {
        if (Files.isReadable(path)) {
            try {
                LOG.info("Loading mime table from file: " + path.toAbsolutePath().toString());
                try(final InputStream is = Files.newInputStream(path)) {
                    loadMimeTypes(is);
                }
            } catch (final ParserConfigurationException | SAXException | IOException e) {
                LOG.error(FILE_LOAD_FAILED_ERR + path.toAbsolutePath().toString(), e);
            }
        }
    }

    public MimeTable(final InputStream stream, final String src) {
        load(stream, src);
    }

    private void load() {
        final ClassLoader cl = MimeTable.class.getClassLoader();
        final InputStream is = cl.getResourceAsStream(MIME_TYPES_XML_DEFAULT);
        if (is == null) {
            LOG.error(LOAD_FAILED_ERR);
        }

        try {
            loadMimeTypes(is);
        } catch (final ParserConfigurationException | SAXException | IOException e) {
            LOG.error(LOAD_FAILED_ERR, e);
        }
    }
    
    private void load(final InputStream stream, final String src) {
        boolean loaded = false;
        LOG.info("Loading mime table from stream: " + src);
        try {
        	loadMimeTypes(stream);
        } catch (final ParserConfigurationException | SAXException | IOException e) {
            LOG.error(LOAD_FAILED_ERR, e);
        }
    	
        if (!loaded) {
            final ClassLoader cl = MimeTable.class.getClassLoader();
            final InputStream is = cl.getResourceAsStream(MIME_TYPES_XML_DEFAULT);
            if (is == null) {
                LOG.error(LOAD_FAILED_ERR);
            }
            try {
                loadMimeTypes(is);
            } catch (final ParserConfigurationException | SAXException | IOException e) {
                LOG.error(LOAD_FAILED_ERR, e);
            }
        }
    }

    /**
     * @param stream
     * @throws SAXException 
     * @throws ParserConfigurationException 
     * @throws IOException 
     */
    private void loadMimeTypes(InputStream stream) throws ParserConfigurationException, SAXException, IOException {
        final SAXParserFactory factory = ExistSAXParserFactory.getSAXParserFactory();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
		final InputSource src = new InputSource(stream);
        final SAXParser parser = factory.newSAXParser();
        final XMLReader reader = parser.getXMLReader();

        reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
        reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        reader.setFeature(FEATURE_SECURE_PROCESSING, true);

        reader.setContentHandler(new MimeTableHandler());
        reader.parse(src);
    }

    private class MimeTableHandler extends DefaultHandler {

        private static final String EXTENSIONS = "extensions";
        private static final String DESCRIPTION = "description";
        private static final String MIME_TYPE = "mime-type";
        private static final String MIME_TYPES = "mime-types";
        
        private MimeType mime = null;
        private final StringBuilder charBuf = new StringBuilder(64);
        
        /* (non-Javadoc)
         * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
         */
        @Override
        public void startElement(String uri, String localName, String qName,
                Attributes attributes) throws SAXException {


            if (MIME_TYPES.equals(qName)) {
                // Check for a default mime type settings
                final String defaultMimeAttr = attributes.getValue("default-mime-type");
                final String defaultTypeAttr = attributes.getValue("default-resource-type");

                // Resource type default is XML
                int type = MimeType.XML;
                if (defaultTypeAttr != null) {
                    if ("binary".equals(defaultTypeAttr)) {
                        type = MimeType.BINARY;
                    }
                }

                // If a default-mime-type is specified, create a new default mime type
                if (defaultMimeAttr != null
                        && !defaultMimeAttr.isEmpty()) {
                    defaultMime = new MimeType(defaultMimeAttr, type);

                    // If the default-resource-type is specified, and the default-mime-type is unspecified, use a predefined type
                } else if (defaultTypeAttr != null) {
                    if (type == MimeType.XML) {
                        defaultMime = MimeType.XML_TYPE;
                    } else if (type == MimeType.BINARY) {
                        defaultMime = MimeType.BINARY_TYPE;
                    }
                } else {
                    // the defaultMime is left to null, for backward compatibility with 1.2
                }

                // Put the default mime into the mime map
                if (defaultMime != null) {
                    mimeTypes.put(defaultMime.getName(), defaultMime);
                    maybeDefaultMediaType = Optional.ofNullable(defaultMime).map(MimeTypeAdapter::new);
                }
            }

            if (MIME_TYPE.equals(qName)) {
                final String name = attributes.getValue("name");
                if (name == null || name.isEmpty()) {
                    LOG.error("No name specified for mime-type");
                    return;
                }
                int type = MimeType.BINARY;
                final String typeAttr = attributes.getValue("type");
                if (typeAttr != null && "xml".equals(typeAttr))
                    {type = MimeType.XML;}
                mime = new MimeType(name, type);
                mimeTypes.put(name, mime);
            }
            charBuf.setLength(0);
        }
        
        /* (non-Javadoc)
         * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
         */
        @Override
        public void endElement(String uri, String localName, String qName)
                throws SAXException {
            if (MIME_TYPE.equals(qName)) {
                mime = null;
            } else if (DESCRIPTION.equals(qName)) {
                if (mime != null) {
                    final String description = charBuf.toString().trim();
                    mime.setDescription(description);
                }
            } else if (EXTENSIONS.equals(qName)) {
                if (mime != null) {
                    final String extList = charBuf.toString().trim();
                    final StringTokenizer tok = new StringTokenizer(extList, ", ");
                    String preferred = null;
                    while (tok.hasMoreTokens()) {
                        String ext = tok.nextToken().toLowerCase();
                        if (!extensions.containsKey(ext)) {
                            extensions.put(ext, mime);
                        }
                        if (preferred==null) {
                           preferred = ext;
                        }
                    }
                    preferredExtension.put(mime.getName(),preferred);
                }
            }
        }
        
        /* (non-Javadoc)
         * @see org.xml.sax.helpers.DefaultHandler#characters(char[], int, int)
         */
        @Override
        public void characters(char[] ch, int start, int length)
                throws SAXException {
            charBuf.append(ch, start, length);
        }
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
            // TODO(AR) If no entry is found, then contentType == "application/octet-stream"... we should make that mediaType a constant... or we should have getContentType return null???
            return maybeDefaultMediaType;
        }

        final String extension = fileName.substring(idx + 1);
        if (extension.length() == 0) {
            // TODO(AR) If no entry is found, then contentType == "application/octet-stream"... we should make that mediaType a constant... or we should have getContentType return null???
            return maybeDefaultMediaType;
        }

        final Optional<MediaType> maybeMediaType = Optional.ofNullable(extensions.get('.' + extension))
                .map(MimeTypeAdapter::new);
        return OptionalUtil.or(
                maybeMediaType,
                maybeDefaultMediaType
        );
    }

    public class MimeTypeAdapter implements MediaType {
        private final MimeType mimeType;

        private MimeTypeAdapter(final MimeType mimeType) {
            this.mimeType = mimeType;
        }

        @Override
        public String getIdentifier() {
            return mimeType.getName();
        }

        @Override
        public @Nullable String[] getKnownFileExtensions() {
            final Set<String> exts = new HashSet<>();
            for (final Map.Entry<String, MimeType> entry : extensions.entrySet()) {
                if (entry.getValue().getName().equals(getIdentifier())) {
                    exts.add(entry.getKey().substring(1));  // strip leading '.'
                }
            }
            return exts.toArray(new String[exts.size()]);
        }

        @Override
        public StorageType getStorageType() {
            switch (mimeType.getType()) {
                case MimeType.XML:
                    return StorageType.XML;
                case MimeType.BINARY:
                    return StorageType.BINARY;
                default:
                    throw new IllegalStateException();
            }
        }
    }

    @Override
    public Optional<MediaType> fromString(final String mediaType) {
        final MimeType mt = mimeTypes.get(mediaType);
        return Optional.ofNullable(mt)
                .map(MimeTypeAdapter::new);
    }

    private final LazyVal<MediaType> unknownMediaType = new LazyVal<>(() ->  new MimeTypeAdapter(MimeType.BINARY_TYPE));
    @Override
    public MediaType forUnknown() {
        return unknownMediaType.get();
    }
}
