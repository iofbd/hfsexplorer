/*-
 * Copyright (C) 2011-2012 Erik Larsson
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.catacombae.hfs.types.hfsplus;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.math.BigInteger;
import org.catacombae.csjc.PrintableStruct;
import org.catacombae.csjc.StaticStruct;
import org.catacombae.csjc.StructElements;
import org.catacombae.csjc.structelements.Dictionary;
import org.catacombae.util.Util;

/** This class was generated by CStructToJavaClass. */
public class JournalHeader implements StaticStruct, PrintableStruct,
        StructElements
{

    public static final int JOURNAL_HEADER_MAGIC_BE = 0x4a4e4c78;
    public static final int ENDIAN_MAGIC_BE = 0x12345678;
    public static final int JOURNAL_HEADER_MAGIC_LE = 0x784c4e4a;
    public static final int ENDIAN_MAGIC_LE = 0x78563412;

    /*
     * struct JournalHeader
     * size: 44 bytes
     * description:
     *
     * BP  Size  Type    Identifier  Description
     * -----------------------------------------
     * 0   4     UInt32  magic
     * 4   4     UInt32  endian
     * 8   8     UInt64  start
     * 16  8     UInt64  end
     * 24  8     UInt64  size
     * 32  4     UInt32  blhdrSize
     * 36  4     UInt32  checksum
     * 40  4     UInt32  jhdrSize
     */

    public static final int STRUCTSIZE = 44;

    private final boolean isLittleEndian;

    private int magic;
    private int endian;
    private long start;
    private long end;
    private long size;
    private int blhdrSize;
    private int checksum;
    private int jhdrSize;

    public JournalHeader(byte[] data, int offset) {
        magic = Util.readIntBE(data, offset+0);
        endian = Util.readIntBE(data, offset+4);

        if(magic == JOURNAL_HEADER_MAGIC_BE && endian == ENDIAN_MAGIC_BE) {
            isLittleEndian = false;
            start = Util.readLongBE(data, offset+8);
            end = Util.readLongBE(data, offset+16);
            size = Util.readLongBE(data, offset+24);
            blhdrSize = Util.readIntBE(data, offset+32);
            checksum = Util.readIntBE(data, offset+36);
            jhdrSize = Util.readIntBE(data, offset+40);
        }
        else if(magic == JOURNAL_HEADER_MAGIC_LE && endian == ENDIAN_MAGIC_LE) {
            isLittleEndian = true;
            magic = Util.byteSwap(magic);
            endian = Util.byteSwap(endian);
            start = Util.readLongLE(data, offset+8);
            end = Util.readLongLE(data, offset+16);
            size = Util.readLongLE(data, offset+24);
            blhdrSize = Util.readIntLE(data, offset+32);
            checksum = Util.readIntLE(data, offset+36);
            jhdrSize = Util.readIntLE(data, offset+40);
        }
        else {
            throw new RuntimeException("Unrecognized magic '0x" +
                    Util.toHexStringBE(magic) + "'.");
        }
    }

    public static int length() { return STRUCTSIZE; }

    public int size() { return length(); }

    /**  */
    public final long getMagic() { return Util.unsign(getRawMagic()); }
    /**  */
    public final long getEndian() { return Util.unsign(getRawEndian()); }
    /**  */
    public final BigInteger getStart() { return Util.unsign(getRawStart()); }
    /**  */
    public final BigInteger getEnd() { return Util.unsign(getRawEnd()); }
    /**  */
    public final BigInteger getSize() { return Util.unsign(getRawSize()); }
    /**  */
    public final long getBlhdrSize() { return Util.unsign(getRawBlhdrSize()); }
    /**  */
    public final long getChecksum() { return Util.unsign(getRawChecksum()); }
    /**  */
    public final long getJhdrSize() { return Util.unsign(getRawJhdrSize()); }

    /** <b>Note that the return value from this function should be interpreted as an unsigned integer, for instance using Util.unsign(...).</b> */
    public final int getRawMagic() { return magic; }
    /** <b>Note that the return value from this function should be interpreted as an unsigned integer, for instance using Util.unsign(...).</b> */
    public final int getRawEndian() { return endian; }
    /** <b>Note that the return value from this function should be interpreted as an unsigned integer, for instance using Util.unsign(...).</b> */
    public final long getRawStart() { return start; }
    /** <b>Note that the return value from this function should be interpreted as an unsigned integer, for instance using Util.unsign(...).</b> */
    public final long getRawEnd() { return end; }
    /** <b>Note that the return value from this function should be interpreted as an unsigned integer, for instance using Util.unsign(...).</b> */
    public final long getRawSize() { return size; }
    /** <b>Note that the return value from this function should be interpreted as an unsigned integer, for instance using Util.unsign(...).</b> */
    public final int getRawBlhdrSize() { return blhdrSize; }
    /** <b>Note that the return value from this function should be interpreted as an unsigned integer, for instance using Util.unsign(...).</b> */
    public final int getRawChecksum() { return checksum; }
    /** <b>Note that the return value from this function should be interpreted as an unsigned integer, for instance using Util.unsign(...).</b> */
    public final int getRawJhdrSize() { return jhdrSize; }

    public void printFields(PrintStream ps, String prefix) {
        ps.println(prefix + " magic: " + getMagic());
        ps.println(prefix + " endian: " + getEndian());
        ps.println(prefix + " start: " + getStart());
        ps.println(prefix + " end: " + getEnd());
        ps.println(prefix + " size: " + getSize());
        ps.println(prefix + " blhdrSize: " + getBlhdrSize());
        ps.println(prefix + " checksum: " + getChecksum());
        ps.println(prefix + " jhdrSize: " + getJhdrSize());
    }

    public void print(PrintStream ps, String prefix) {
        ps.println(prefix + "JournalHeader:");
        printFields(ps, prefix);
    }

    public byte[] getBytes() {
        byte[] result = new byte[length()];
        int offset = 0;

        if(!isLittleEndian) {
            Util.arrayPutBE(result, offset, magic); offset += 4;
            Util.arrayPutBE(result, offset, endian); offset += 4;
            Util.arrayPutBE(result, offset, start); offset += 8;
            Util.arrayPutBE(result, offset, end); offset += 8;
            Util.arrayPutBE(result, offset, size); offset += 8;
            Util.arrayPutBE(result, offset, blhdrSize); offset += 4;
            Util.arrayPutBE(result, offset, checksum); offset += 4;
            Util.arrayPutBE(result, offset, jhdrSize); offset += 4;
        }
        else {
            Util.arrayPutLE(result, offset, magic); offset += 4;
            Util.arrayPutLE(result, offset, endian); offset += 4;
            Util.arrayPutLE(result, offset, start); offset += 8;
            Util.arrayPutLE(result, offset, end); offset += 8;
            Util.arrayPutLE(result, offset, size); offset += 8;
            Util.arrayPutLE(result, offset, blhdrSize); offset += 4;
            Util.arrayPutLE(result, offset, checksum); offset += 4;
            Util.arrayPutLE(result, offset, jhdrSize); offset += 4;
        }

        return result;
    }

    private Field getPrivateField(String name) throws NoSuchFieldException {
        Field f = getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    public Dictionary getStructElements() {
        DictionaryBuilder db = new DictionaryBuilder("JournalInfoBlock",
                "Header for the journal data, describing what region of data " +
                "is valid and other properties.");

       try {
            db.addUIntBE("magic", getPrivateField("magic"), this, null, null,
                    HEXADECIMAL);
            db.addUIntBE("endian", getPrivateField("endian"), this, null, null,
                    HEXADECIMAL);
            db.addUIntBE("start", getPrivateField("start"), this, null, "bytes",
                    DECIMAL);
            db.addUIntBE("end", getPrivateField("end"), this, null, "bytes",
                    DECIMAL);
            db.addUIntBE("size", getPrivateField("size"), this, null, "bytes",
                    DECIMAL);
            db.addUIntBE("blhdrSize", getPrivateField("blhdrSize"), this, null,
                    "bytes", DECIMAL);
            db.addUIntBE("checksum", getPrivateField("checksum"), this, null,
                    null, HEXADECIMAL);
            db.addUIntBE("jhdrSize", getPrivateField("jhdrSize"), this, null,
                    "bytes", DECIMAL);
        } catch(NoSuchFieldException e) {
            throw new RuntimeException(e);
        }

        return db.getResult();
    }
}
