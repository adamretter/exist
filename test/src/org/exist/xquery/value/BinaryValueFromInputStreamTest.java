package org.exist.xquery.value;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;
import org.exist.xquery.XPathException;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;

/**
 *
 * @author Adam Retter <adam@existsolutions.com>
 */
public class BinaryValueFromInputStreamTest {

    private final static int _4KB = 4 * 1024;
    private final static int _6KB = 6 * 1024;
    private final static int _12KB = 12 * 1024;
    
    @Test
    public void getInputStream() throws XPathException, IOException {
        final BinaryValueManager binaryValueManager = new MockBinaryValueManager();

        try {
            final byte[] testData = generateRandomData(_12KB);

            final InputStream bais = new ByteArrayInputStream(testData) {
                @Override
                public boolean markSupported() {
                    return false;
                }
            };

            final BinaryValue binaryValue = BinaryValueFromInputStream.getInstance(binaryValueManager, new Base64BinaryValueType(), bais);
            final InputStream is = binaryValue.getInputStream();

            assertArrayEquals(testData, consumeInputStream(is));
        } finally {
            binaryValueManager.runCleanupTasks();
        }
    }
    
    @Test
    public void getInputStreams() throws XPathException, IOException {
        final BinaryValueManager binaryValueManager = new MockBinaryValueManager();

        try {
            final byte[] testData = generateRandomData(_12KB);

            final InputStream bais = new ByteArrayInputStream(testData) {
                @Override
                public boolean markSupported() {
                    return false;
                }
            };

            final BinaryValue binaryValue = BinaryValueFromInputStream.getInstance(binaryValueManager, new Base64BinaryValueType(), bais);
            
            //stream once
            final InputStream result1 = binaryValue.getInputStream();
            assertArrayEquals(testData, consumeInputStream(result1));
            
            //stream twice
            final InputStream result2 = binaryValue.getInputStream();
            assertArrayEquals(testData, consumeInputStream(result2));
            
        } finally {
            binaryValueManager.runCleanupTasks();
        }
    }
    
    @Test
    public void repeatable_streamTo() throws XPathException, IOException {
        final BinaryValueManager binaryValueManager = new MockBinaryValueManager();

        try {
            final byte[] testData = generateRandomData(_6KB);

            final InputStream bais = new ByteArrayInputStream(testData) {
                @Override
                public boolean markSupported() {
                    return false;
                }
            };

            final BinaryValueType base64Type = new Base64BinaryValueType();
            final BinaryValue binaryValue = BinaryValueFromInputStream.getInstance(binaryValueManager, base64Type, bais);
            
            //stream once
            final ByteArrayOutputStream result1 = new ByteArrayOutputStream();
            binaryValue.streamTo(base64Type.getDecoder(result1));
            assertArrayEquals(testData, result1.toByteArray());
            
            //stream twice
            final ByteArrayOutputStream result2 = new ByteArrayOutputStream();
            binaryValue.streamTo(base64Type.getDecoder(result2));
            assertArrayEquals(testData, result2.toByteArray());
            
        } finally {
            binaryValueManager.runCleanupTasks();
        }
    }
    
    @Test
    public void repeatable_streamBinaryTo() throws XPathException, IOException {
        final BinaryValueManager binaryValueManager = new MockBinaryValueManager();

        try {
            final byte[] testData = generateRandomData(_6KB);

            final InputStream bais = new ByteArrayInputStream(testData) {
                @Override
                public boolean markSupported() {
                    return false;
                }
            };

            final BinaryValue binaryValue = BinaryValueFromInputStream.getInstance(binaryValueManager, new Base64BinaryValueType(), bais);
            
            //stream once
            final ByteArrayOutputStream result1 = new ByteArrayOutputStream();
            binaryValue.streamBinaryTo(result1);
            assertArrayEquals(testData, result1.toByteArray());
            
            //stream twice
            final ByteArrayOutputStream result2 = new ByteArrayOutputStream();
            binaryValue.streamBinaryTo(result2);
            assertArrayEquals(testData, result2.toByteArray());
            
        } finally {
            binaryValueManager.runCleanupTasks();
        }
    }
    
    private byte[] consumeInputStream(final InputStream is) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            int read = -1;
            final byte buf[] = new byte[_4KB];
            while((read = is.read(buf)) > -1) {
                baos.write(buf, 0, read);
            }
            return baos.toByteArray();
        } finally {
            baos.close();
        }
    }
    
    private byte[] generateRandomData(final int bytes) {
        final byte[] data = new byte[bytes];
        new Random().nextBytes(data);
        return data;
    }
}