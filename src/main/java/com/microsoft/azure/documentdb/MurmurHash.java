/*
The MIT License (MIT)
Copyright (c) 2014 Microsoft Corporation

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package com.microsoft.azure.documentdb;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The MurmurHash3 algorithm was created by Austin Appleby and placed in the public domain.
 * This java port was authored by Yonik Seeley and also placed into the public domain.
 * The author hereby disclaims copyright to this source code.
 * <p>
 * This produces exactly the same hash values as the final C++
 * version of MurmurHash3 and is thus suitable for producing the same hash values across
 * platforms.
 * <p>
 * The 32 bit x86 version of this hash should be the fastest variant for relatively short keys like ids.
 * See http://github.com/yonik/java_util for future updates to this file.
 */
public final class MurmurHash implements HashGenerator {
    /**
     * Returns the MurmurHash3_x86_32 hash.
     *
     * @param data a byte array containing the data to be hashed
     * @param len  an integer indicating the length of data
     * @param seed an integer to be used as hash seed
     * @return     the hash value
     */
    public static int computeHash(byte[] data, int len, int seed) {
        final int c1 = 0xcc9e2d51;
        final int c2 = 0x1b873593;

        int h1 = seed;
        int roundedEnd = (len & 0xfffffffc);  // round down to 4 byte block

        for (int i = 0; i < roundedEnd; i += 4) {
            // little endian load order
            int k1 = (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8) | ((data[i + 2] & 0xff) << 16) | (data[i + 3] << 24);
            k1 *= c1;
            k1 = (k1 << 15) | (k1 >>> 17);  // ROTL32(k1,15);
            k1 *= c2;

            h1 ^= k1;
            h1 = (h1 << 13) | (h1 >>> 19);  // ROTL32(h1,13);
            h1 = h1 * 5 + 0xe6546b64;
        }

        // tail
        int k1 = 0;

        switch (len & 0x03) {
            case 3:
                k1 = (data[roundedEnd + 2] & 0xff) << 16;
                // fallthrough
            case 2:
                k1 |= (data[roundedEnd + 1] & 0xff) << 8;
                // fallthrough
            case 1:
                k1 |= (data[roundedEnd] & 0xff);
                k1 *= c1;
                k1 = (k1 << 15) | (k1 >>> 17);  // ROTL32(k1,15);
                k1 *= c2;
                h1 ^= k1;
        }

        // finalization
        h1 ^= len;

        // fmix(h1);
        h1 ^= h1 >>> 16;
        h1 *= 0x85ebca6b;
        h1 ^= h1 >>> 13;
        h1 *= 0xc2b2ae35;
        h1 ^= h1 >>> 16;

        return h1;
    }

    @Override
    public byte[] computeHash(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("data");
        }

        int hashValue = computeHash(data, data.length, 0);
        // Java's default "Endianess" is BigEndian but for all other SDKs
        // the default is LittleEndian, so changing the ByteOrder to be LittleEndian
        // here so that we can be consistent across all SDKs.
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(hashValue).array();
    }
}

