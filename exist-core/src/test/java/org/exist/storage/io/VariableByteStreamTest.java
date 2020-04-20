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
package org.exist.storage.io;

import org.junit.Test;

import java.io.IOException;
import java.util.Random;

import static org.junit.Assert.assertEquals;

public class VariableByteStreamTest {

	@Test
	public void inOutLong() throws IOException {
		final int size = 3000;

		// create test data
		final long[] values = new long[size];
		final Random rand = new Random(System.currentTimeMillis());
		for (int i = 0; i < size; i++) {
			values[i++] = rand.nextInt();
			values[i++] = rand.nextInt() & 0xffffff;
			values[i] = rand.nextInt() & 0xff;
		}

		// Write variable byte encoded `values` to `vbeValues`
		final VariableByteOutputStream os = new VariableByteOutputStream();
		for (int i = 0; i < size; i++) {
			os.writeLong(values[i++]);
			os.writeInt((int)values[i++]);
			os.writeShort((short)values[i]);
		}
		final byte[] vbeValues = os.toByteArray();

		// Read variable byte encoded `values` from `vbeValues`
		final VariableByteArrayInput is = new VariableByteArrayInput(vbeValues);
		for (int j = 0; j < size; j++) {
			final long l = is.readLong();
			assertEquals(values[j++], l);
			final int i = is.readInt();
			assertEquals(values[j++], i);
			final long s = is.readShort();
			assertEquals(values[j], s);
		}
	}

	@Test
	public void copyTo() throws IOException {

		// create test data - 1000 random length arrays of short values
		final short[][] values = new short[1000][];
		final Random rand = new Random(System.currentTimeMillis());
		for (int i = 0; i < 1000; i++) {
			final short arrayLen = (short) rand.nextInt(Short.MAX_VALUE);
			values[i] = new short[arrayLen];
			for (int j = 0; j < arrayLen; j++) {
				final short arrayValue = (short) rand.nextInt(Short.MAX_VALUE);
				values[i][j] = arrayValue;
			}
		}

		// Write short `values` as variable byte encoded data to `vbeValues`. Format is: [arrayLen, arrayValue1..arrayValueN]+
		final VariableByteOutputStream os = new VariableByteOutputStream();
		for (int i = 0; i < values.length; i++) {
			final short arrayLen = (short) values[i].length;
			os.writeShort(arrayLen);
			for (int j = 0; j < arrayLen; j++) {
				final short arrayValue = values[i][j];
				os.writeShort(arrayValue);
			}
		}
		final byte[] vbeValues = os.toByteArray();

		// Copy shorts in `vbeValues` via `src` to `dest` into `copiedVbeValues`
		final VariableByteArrayInput src = new VariableByteArrayInput(vbeValues);
		final VariableByteOutputStream dest = new VariableByteOutputStream();
		int valuesCopied = 0;
		while (src.available() > 0) {
			final short arrayLen = src.readShort();  // read arrayLen from src
			final boolean skip = rand.nextBoolean();
			if (skip) {
				// don't copy array to dest
				src.skip(arrayLen);	 // skip over arrayValue(s) in src
			} else {
				// copy to array to dest
				dest.writeShort(arrayLen);	 // write arrayLen to dest
				src.copyTo(dest, arrayLen);  // copy arrayValue(s) to dest
				valuesCopied += arrayLen;
			}
		}
		final byte[] copiedVbeValues = dest.toByteArray();

		// compare number of copied values against values read
		int valuesRead = 0;
		final VariableByteArrayInput is = new VariableByteArrayInput(copiedVbeValues);
		while (is.available() > 0) {
			int count = is.readShort();
			for (int i = 0; i < count; i++) {
				is.readShort();
				valuesRead++;
			}
		}
		assertEquals(valuesRead, valuesCopied);
	}
}
