/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.xmldb;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import com.evolvedbinary.j8fu.function.BiFunction2E;
import org.apache.commons.io.output.ByteArrayOutputStream;

import org.exist.EXistException;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.EXistInputSource;
import org.exist.util.VirtualTempFile;
import org.xml.sax.InputSource;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

import javax.annotation.Nullable;

import static java.nio.charset.StandardCharsets.UTF_8;

public abstract class AbstractRemoteResource extends AbstractRemote
        implements EXistResource, ExtendedResource, Resource {

    protected final XmldbURI path;
    private String mimeType;

    VirtualTempFile vfile = null;
    private VirtualTempFile contentVFile = null;
    InputSource inputSource = null;
    private boolean isLocal = false;
    private long contentLen = 0L;
    private Permission permissions = null;

    Date dateCreated = null;
    Date dateModified = null;

    protected AbstractRemoteResource(final RemoteCollection parent, final XmldbURI documentName, final String mimeType) {
        super(parent);
        if (documentName.numSegments() > 1) {
            this.path = documentName;
        } else {
            this.path = parent.getPathURI().append(documentName);
        }
        this.mimeType = mimeType;
    }

    @Override
    @Nullable public Properties getProperties() {
        return collection.getProperties();
    }

    @Override
    public Object getContent()
            throws XMLDBException {
        final Object res = getExtendedContent();
        // Backward compatibility
        if (isLocal) {
            return res;
        } else if (res != null) {

            if (res instanceof Path) {
                return readFile((Path)res);
            } else if (res instanceof java.io.File) {
                return readFile(((java.io.File) res).toPath());
            } else if (res instanceof InputSource) {
                return readFile((InputSource) res);
            }
        }
        return res;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            freeResources();
        }
        finally {
            super.finalize();
        }
    }

    @Override
    public void freeResources() {
        vfile = null;
        inputSource = null;
        if (contentVFile != null) {
            contentVFile.delete();
            contentVFile = null;
        }
        isLocal = true;
    }

    /**
     * @deprecated Here for backward compatibility, instead use {@see org.xmldb.api.base.Resource#getContent()}
     */
    @Deprecated
    protected byte[] getData()
            throws XMLDBException {
        final Object res = getExtendedContent();
        if (res != null) {
            if (res instanceof Path) {
                return readFile((Path)res);
            } else if (res instanceof java.io.File) {
                return readFile(((java.io.File) res).toPath());
            } else if (res instanceof InputSource) {
                return readFile((InputSource) res);
            } else if (res instanceof String) {
                return ((String) res).getBytes(UTF_8);
            }
        }

        return (byte[]) res;
    }

    @Override
    public long getContentLength() {
        return contentLen;
    }

    @Override
    public Date getCreationTime() {
        return dateCreated;
    }

    @Override
    public Date getLastModificationTime() {
        return dateModified;
    }

    @Override
    public void setLastModificationTime(final Date dateModified) throws XMLDBException {
        if (dateModified != null) {
            if(dateModified.before(getCreationTime())) {
                throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, "Modification time must be after creation time.");
            }

            try {
                collection.getClient().setLastModified(path.toString(), dateModified.getTime());
            } catch (final EXistException e) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
            } catch (final PermissionDeniedException e) {
                throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
            }

            this.dateModified = dateModified;
        }
    }

    public long getExtendedContentLength() {
        return contentLen;
    }

    @Override
    public String getMimeType() {
        return mimeType;
    }

    @Override
    public Collection getParentCollection() {
        return collection;
    }

    @Override
    public Permission getPermissions() {
        return permissions;
    }

    boolean setContentInternal(final Object value) {
        freeResources();
        boolean wasSet = false;
        if (value instanceof VirtualTempFile) {
            vfile = (VirtualTempFile) value;
            // Assuring the virtual file is close state
            try {
                vfile.close();
            } catch (final IOException ioe) {
                // IgnoreIT(R)
            }
            setExtendendContentLength(vfile.length());
            wasSet = true;
        } else if (value instanceof Path) {
            vfile = new VirtualTempFile(((Path) value).toFile());
            setExtendendContentLength(vfile.length());
            wasSet = true;
        } else if (value instanceof java.io.File) {
            vfile = new VirtualTempFile((java.io.File) value);
            setExtendendContentLength(vfile.length());
            wasSet = true;
        } else if (value instanceof InputSource) {
            inputSource = (InputSource) value;
            wasSet = true;
        } else if (value instanceof byte[]) {
            vfile = new VirtualTempFile((byte[]) value);
            setExtendendContentLength(vfile.length());
            wasSet = true;
        } else if (value instanceof String) {
            vfile = new VirtualTempFile(((String) value).getBytes(UTF_8));
            setExtendendContentLength(vfile.length());
            wasSet = true;
        }

        return wasSet;
    }

    void setExtendendContentLength(final long len) {
        this.contentLen = len;
    }

    public void setContentLength(final int len) {
        this.contentLen = len;
    }

    public void setContentLength(final long len) {
        this.contentLen = len;
    }

    @Override
    public void setMimeType(final String mimeType) {
        this.mimeType = mimeType;
    }

    public void setPermissions(final Permission perms) {
        permissions = perms;
    }

    @Override
    public void getContentIntoAFile(final Path localfile)
            throws XMLDBException {
        try(final OutputStream os = Files.newOutputStream(localfile)) {
            getContentIntoAStream(os);
        } catch (final IOException ioe) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe);
        }
    }

    void getRemoteContentIntoLocalFile(final OutputStream os, final boolean isRetrieve, final int handle, final int pos) throws XMLDBException {
        try {
            final VirtualTempFile vtmpfile = new VirtualTempFile();
            vtmpfile.setTempPrefix("eXistARR");
            vtmpfile.setTempPostfix("XMLResource".equals(getResourceType()) ? ".xml" : ".bin");

            Map<String, Object> table;
            if (isRetrieve) {
                table = collection.getClient().retrieveFirstChunk(handle, pos, asMap(getProperties()));
            } else {
                table = collection.getClient().getDocumentData(path.toString(), asMap(getProperties()));
            }

            final BiFunction2E<String, Long, Map<String, Object>, EXistException, PermissionDeniedException> fnNextChunk;
            final boolean useLongOffset;
            if (table.containsKey("supports-long-offset") && (Boolean)table.get("supports-long-offset")) {
                fnNextChunk = (strHandle, longOffset) -> collection.getClient().getNextExtendedChunk(strHandle,  Long.toString(longOffset));
                useLongOffset = true;
            } else {
                useLongOffset = false;
                fnNextChunk = (strHandle, longOffset) -> collection.getClient().getNextChunk(strHandle,  longOffset.intValue());
            }

            byte[] data = (byte[]) table.get("data");
            final boolean isCompressed = "yes".equals(getProperties().getProperty(EXistOutputKeys.COMPRESS_OUTPUT, "no"));

            // One for the local cached file
            Inflater dec = null;
            byte[] decResult = null;
            int decLength;
            if (isCompressed) {
                dec = new Inflater();
                decResult = new byte[65536];
                dec.setInput(data);
                do {
                    decLength = dec.inflate(decResult);
                    vtmpfile.write(decResult, 0, decLength);
                    // And other for the stream where we want to save it!
                    if (os != null) {
                        os.write(decResult, 0, decLength);
                    }
                } while (decLength == decResult.length || !dec.needsInput());

            } else {
                vtmpfile.write(data);
                // And other for the stream where we want to save it!
                if (os != null) {
                    os.write(data);
                }
            }

            long offset = ((Integer) table.get("offset")).intValue();
            while (offset > 0) {

                table = fnNextChunk.apply(table.get("handle").toString(), offset);
                offset = useLongOffset ? Long.parseLong((String) table.get("offset")) : ((Integer) table.get("offset"));
                data = (byte[]) table.get("data");

                // One for the local cached file
                if (isCompressed) {
                    dec.setInput(data);
                    do {
                        decLength = dec.inflate(decResult);
                        vtmpfile.write(decResult, 0, decLength);
                        // And other for the stream where we want to save it!
                        if (os != null) {
                            os.write(decResult, 0, decLength);
                        }
                    } while (decLength == decResult.length || !dec.needsInput());
                } else {
                    vtmpfile.write(data);
                    // And other for the stream where we want to save it!
                    if (os != null) {
                        os.write(data);
                    }
                }
            }

            if (dec != null) {
                dec.end();
            }

            isLocal = false;
            contentVFile = vtmpfile;
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        } catch (final IOException | DataFormatException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } finally {
            if (contentVFile != null) {
                try {
                    contentVFile.close();
                } catch (final IOException ioe) {
                    //IgnoreIT(R)
                }
            }
        }
    }

    static Map<String, Object> asMap(final Properties properties) {
        final Map<String, Object> map = new HashMap<>(properties.size());
        for(final Map.Entry<Object, Object> entry : properties.entrySet()) {
            map.put(entry.getKey().toString(), entry.getValue());
        }
        return map;
    }

    private static InputStream getAnyStream(final Object obj)
            throws XMLDBException {
        if (obj instanceof String) {
            return new ByteArrayInputStream(((String) obj).getBytes(UTF_8));
        } else if (obj instanceof byte[]) {
            return new ByteArrayInputStream((byte[]) obj);
        } else {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "don't know how to handle value of type " + obj.getClass().getName());
        }
    }

    void getContentIntoAStreamInternal(final OutputStream os, final Object obj, final boolean isRetrieve, final int handle, final int pos)
            throws XMLDBException {
        if (vfile != null || contentVFile != null || inputSource != null || obj != null) {
            InputStream bis = null;
            try {
                // First, the local content, then the remote one!!!!
                if (vfile != null) {
                    bis = vfile.getByteStream();
                } else if (inputSource != null) {
                    bis = inputSource.getByteStream();
                } else if (obj != null) {
                    bis = getAnyStream(obj);
                } else {
                    bis = contentVFile.getByteStream();
                }
                copy(bis, os);
            } catch (final IOException ioe) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe);
            } finally {
                if (inputSource != null) {
                    if (bis != null) {
                        // As it comes from an input source, we cannot blindly close it,
                        // but at least let's reset it! (if it is possible)
                        if (bis.markSupported()) {
                            try {
                                bis.reset();
                            } catch (final IOException ioe) {
                                //IgnoreIT(R)
                            }
                        }
                    }
                } else {
                    if (bis != null) {
                        try {
                            bis.close();
                        } catch (final IOException ioe) {
                            //IgnoreIT(R)
                        }
                    }
                }
            }
        } else {
            // Let's fetch it, and save just in time!!!
            getRemoteContentIntoLocalFile(os, isRetrieve, handle, pos);
        }
    }

    Object getExtendedContentInternal(final Object obj, final boolean isRetrieve, final int handle, final int pos)
            throws XMLDBException {
        if (obj != null) {
            return obj;
        } else if (vfile != null) {
            return vfile.getContent();
        } else if (inputSource != null) {
            return inputSource;
        } else {
            if (contentVFile == null) {
                getRemoteContentIntoLocalFile(null, isRetrieve, handle, pos);
            }
            return contentVFile.getContent();
        }
    }

    InputStream getStreamContentInternal(final Object obj, final boolean isRetrieve, final int handle, final int pos)
            throws XMLDBException {
        final InputStream retval;
        try {
            if (vfile != null) {
                retval = vfile.getByteStream();
            } else if (inputSource != null) {
                retval = inputSource.getByteStream();
            } else if (obj != null) {
                retval = getAnyStream(obj);
            } else {
                // At least one value, please!!!
                if (contentVFile == null) {
                    getRemoteContentIntoLocalFile(null, isRetrieve, handle, pos);
                }
                retval = contentVFile.getByteStream();
            }
        } catch (final IOException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }

        return retval;
    }

    long getStreamLengthInternal(final Object obj)
            throws XMLDBException {

        final long retval;
        if (vfile != null) {
            retval = vfile.length();
        } else if (inputSource != null && inputSource instanceof EXistInputSource) {
            retval = ((EXistInputSource) inputSource).getByteStreamLength();
        } else if (obj != null) {
            if (obj instanceof String) {
                retval = ((String) obj).getBytes(UTF_8).length;
            } else if (obj instanceof byte[]) {
                retval = ((byte[]) obj).length;
            } else {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "don't know how to handle value of type " + obj.getClass().getName());
            }
        } else if (contentVFile != null) {
            retval = contentVFile.length();
        } else {
            try {
                final Map<String, Object> table = collection.getClient().describeResource(path.toString());
                if (table.containsKey("content-length-64bit")) {
                    final Object o = table.get("content-length-64bit");
                    if (o instanceof Long) {
                        retval = ((Long) o);
                    } else {
                        retval = Long.parseLong((String) o);
                    }
                } else {
                    retval = ((Integer) table.get("content-length"));
                }
            } catch (final EXistException e) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
            } catch (final PermissionDeniedException e) {
                throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
            }
        }

        return retval;
    }


    protected byte[] readFile(final Path file)
            throws XMLDBException {
        try(final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            Files.copy(file, os);
            return os.toByteArray();
        } catch (final IOException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    protected byte[] readFile(final InputSource in) throws XMLDBException {
        final InputStream bis = in.getByteStream();
        try {
            return readFile(bis);
        } finally {
            //TODO(AR) why do we do this? should probably close it?

            // As it comes from an input source, we cannot blindly close it,
            // but at least let's reset it! (if it is possible)
            if (bis.markSupported()) {
                try {
                    bis.reset();
                } catch (final IOException ioe) {
                    //IgnoreIT(R)
                }
            }
        }
    }

    private byte[] readFile(final InputStream is)
            throws XMLDBException {
        try(final ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            copy(is, bos);
            return bos.toByteArray();
        } catch (final IOException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    private void copy(final InputStream is, final OutputStream os) throws IOException {
        int read;
        final byte buffer[] = new byte[65536]; //64KB
        while ((read = is.read(buffer)) > -1) {
            os.write(buffer, 0, read);
        }
    }

    @Override
    public void close() throws IOException {
        freeResources();
    }
}
