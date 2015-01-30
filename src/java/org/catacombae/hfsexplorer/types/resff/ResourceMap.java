/*-
 * Copyright (C) 2008 Erik Larsson
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

package org.catacombae.hfsexplorer.types.resff;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import org.catacombae.csjc.PrintableStruct;
import org.catacombae.io.RuntimeIOException;
import org.catacombae.util.Util;
import org.catacombae.io.SynchronizedReadableRandomAccess;
import org.catacombae.util.Util.Pair;

/** This class was generated by CStructToJavaClass. */
public class ResourceMap implements PrintableStruct {
    /*
     * struct ResourceMap
     * size: minimum 30 or 38 bytes
     * description:
     *
     * BP  Size  Type                       Identifier              Description
     * -------------------------------------------------------------------------------------------------------------------
     * 0   1*16  UInt8[16]                  reserved1               // Reserved for copy of resource header.
     * 16  4     UInt32                     reserved2               // Reserved for handle to next resource map.
     * 20  2     UInt16                     reserved3               // Reserved for file reference number.
     * 22  2     UInt16                     resourceForkAttributes  // Resource fork attributes
     * 24  2     UInt16                     typeListOffset          // Offset from beginning of map to resource type list.
     * 26  2     UInt16                     nameListOffset          // Offset from beginning of map to resource name list.
     * 28  2     SInt16                     typeCount               // Number of types in the map minus 1.
     * 30  8*?   ResourceType[typeCount+1]  resourceTypeList        // Resource type list.
     */

    private static final boolean DEBUG = Util.booleanEnabledByProperties(true,
            "org.catacombae.debug",
            "org.catacombae.hfsexplorer.debug",
            "org.catacombae.hfsexplorer.types.debug",
            "org.catacombae.hfsexplorer.types.resff.debug",
            "org.catacombae.hfsexplorer.types.resff." +
            ResourceMap.class.getSimpleName() + ".debug");

    public static final int STRUCTSIZE = 46;

    private final byte[] reserved1 = new byte[1*16];
    private final byte[] reserved2 = new byte[4];
    private final byte[] reserved3 = new byte[2];
    private final byte[] resourceForkAttributes = new byte[2];
    private final byte[] typeListOffset = new byte[2];
    private final byte[] nameListOffset = new byte[2];
    private final byte[] typeCount = new byte[2];
    private final ResourceType[] resourceTypeList;
    private final List<Pair<ResourceType, ReferenceListEntry[]>> referenceList;
    private final List<Pair<ReferenceListEntry, ResourceName>> resourceNameList;

    public ResourceMap(SynchronizedReadableRandomAccess stream, final long offset) {
        byte[] data = new byte[30];

        final int bytesRead = stream.readFrom(offset, data);
        if(bytesRead != data.length) {
            throw new RuntimeIOException("Error while reading the resource " +
                    "map header from offset " + offset + " (" +
                    (bytesRead == -1 ? "End of file" :
                    (bytesRead + " / " + data.length + " bytes read")) + ").");
        }

        System.arraycopy(data, 0, reserved1, 0, 1 * 16);
        System.arraycopy(data, 16, reserved2, 0, 4);
        System.arraycopy(data, 20, reserved3, 0, 2);
        System.arraycopy(data, 22, resourceForkAttributes, 0, 2);
        System.arraycopy(data, 24, typeListOffset, 0, 2);
        System.arraycopy(data, 26, nameListOffset, 0, 2);
        System.arraycopy(data, 28, typeCount, 0, 2);

        if(DEBUG) {
            System.err.println("ResourceMap(): typeListOffset = " +
                    getTypeListOffset());
            System.err.println("ResourceMap(): nameListOffset = " +
                    getNameListOffset());
            System.err.println("ResourceMap(): typeCount = " +
                    getTypeCount());
        }

        final int resourceTypeListOffset = getTypeListOffset();
        final int resourceNameListOffset = getNameListOffset();

        {
            long curOffset = offset + resourceTypeListOffset + 2; // +2 for typeCount, which is included in the offset
            byte[] curResTypeData = new byte[ResourceType.length()];
            resourceTypeList = new ResourceType[getTypeCount() + 1]; // typeCount is a SIGNED integer.
            for(int i = 0; i < resourceTypeList.length; ++i) {
                stream.readFullyFrom(curOffset, curResTypeData);
                resourceTypeList[i] = new ResourceType(curResTypeData, 0);
                curOffset += curResTypeData.length;

                if(DEBUG) {
                    System.err.println("Read type:");
                    resourceTypeList[i].print(System.err, "  ");
                }
            }
        }

        int numberOfRefListEntries = 0;
        {
            byte[] curListEntryData = new byte[ReferenceListEntry.length()];
            referenceList = new ArrayList<Pair<ResourceType, ReferenceListEntry[]>>(resourceTypeList.length);
            for(int i = 0; i < resourceTypeList.length; ++i) {
                ResourceType curType = resourceTypeList[i];
                if(DEBUG) {
                    System.err.println("Processing instances of type:");
                    curType.print(System.err, "  ");
                }

                ReferenceListEntry[] curList = new ReferenceListEntry[curType.getInstanceCount() + 1];

                long curOffset = offset + resourceTypeListOffset + Util.unsign(curType.getReferenceListOffset());
                for(int j = 0; j < curList.length; ++j) {
                    stream.readFullyFrom(curOffset, curListEntryData);
                    curList[j] = new ReferenceListEntry(curListEntryData, 0);
                    curOffset += curListEntryData.length;

                    if(DEBUG) {
                        System.err.println("Read ReferenceListEntry:");
                        curList[j].print(System.err, "  ");
                    }
                }

                numberOfRefListEntries += curList.length;
                referenceList.add(new Pair<ResourceType, ReferenceListEntry[]>(curType, curList));
            }
        }

        {
            resourceNameList = new ArrayList<Pair<ReferenceListEntry, ResourceName>>(numberOfRefListEntries);

            for(Pair<ResourceType, ReferenceListEntry[]> p : referenceList) {
                for(ReferenceListEntry e : p.getB()) {
                    long resNameOffset = e.getResourceNameOffset();
                    if(resNameOffset != -1) {
                        long nameOffset = offset + resourceNameListOffset + resNameOffset;
                        ResourceName resName = new ResourceName(stream, nameOffset);
                        resourceNameList.add(new Pair<ReferenceListEntry, ResourceName>(e, resName));

                        if(DEBUG) {
                            System.err.println("Read ResourceName:");
                            resName.print(System.err, "  ");
                        }
                    }
                }
            }
        }
    }

    public static int length() { return STRUCTSIZE; }

    public int maxSize() {
        /* Non-variable fields: 30 bytes
         * Number of ResourceTypes can be from 1 (typeCount=0) to
         * 65536 (typeCount=65535) */
        return 30 + ResourceType.length()*(65535+1);
    }

    public int occupiedSize() {
        return 30 + resourceTypeList.length*ResourceType.length();
    }

    /** // Reserved for copy of resource header. */
    public byte[] getReserved1() { return Util.readByteArrayBE(reserved1); }
    /** // Reserved for handle to next resource map. */
    public int getReserved2() { return Util.readIntBE(reserved2); }
    /** // Reserved for file reference number. */
    public short getReserved3() { return Util.readShortBE(reserved3); }
    /** // Resource fork attributes */
    public short getResourceForkAttributes() { return Util.readShortBE(resourceForkAttributes); }
    /** // Offset from beginning of map to resource type list. */
    public int getTypeListOffset() {
        return Util.unsign(getRawTypeListOffset());
    }
    /** // Offset from beginning of map to resource name list. */
    public int getNameListOffset() {
        return Util.unsign(getRawNameListOffset());
    }
    /** // Number of types in the map minus 1. */
    public short getTypeCount() { return Util.readShortBE(typeCount); }

    public short getRawTypeListOffset() {
        return Util.readShortBE(typeListOffset);
    }
    public short getRawNameListOffset() {
        return Util.readShortBE(nameListOffset);
    }
    /** Resource type list. */
    public ResourceType[] getResourceTypeList() {
        return Util.arrayCopy(resourceTypeList, new ResourceType[resourceTypeList.length]);
    }

    public List<Pair<ResourceType, ReferenceListEntry[]>> getReferenceList() {
        return new ArrayList<Pair<ResourceType, ReferenceListEntry[]>>(referenceList);
    }
    public List<Pair<ReferenceListEntry, ResourceName>> getResourceNameList() {
        return new ArrayList<Pair<ReferenceListEntry, ResourceName>>(resourceNameList);
    }

    /**
     * Searches for the reference list entries associated with the supplied ResourceType.
     *
     * @param resType
     * @return the requested entries, or null if none were found. NOTE: null should not normally be
     * returned by this method for a valid resType which has been retrieved through the current map
     * instance. null can be treated as an internal error.
     */
    public ReferenceListEntry[] getReferencesByType(ResourceType resType) {
        for(Pair<ResourceType, ReferenceListEntry[]> entry : referenceList) {
            if(entry.getA() == resType)
                return Util.arrayCopy(entry.getB(), new ReferenceListEntry[entry.getB().length]);
        }
        return null;
    }

    /**
     * Searches for the ResourceName associated with the supplied ReferenceListEntry.
     *
     * @param entry
     * @return the requested name, or null if no name was defined for this resource.
     */
    public ResourceName getNameByReferenceListEntry(ReferenceListEntry entry) {
        for(Pair<ReferenceListEntry, ResourceName> listEntry : resourceNameList) {
            if(listEntry.getA() == entry)
                return listEntry.getB();
        }
        return null;
    }

    public void printFields(PrintStream ps, String prefix) {
        ps.println(prefix + " reserved1: " + getReserved1());
        ps.println(prefix + " reserved2: " + getReserved2());
        ps.println(prefix + " reserved3: " + getReserved3());
        ps.println(prefix + " resourceForkAttributes: " + getResourceForkAttributes());
        ps.println(prefix + " typeListOffset: " + getTypeListOffset());
        ps.println(prefix + " nameListOffset: " + getNameListOffset());
        ps.println(prefix + " typeCount: " + getTypeCount());
        ps.println(prefix + " resourceTypeList: ");
        for(int i = 0; i < resourceTypeList.length; ++i) {
            ps.println(prefix + "  [" + i + "]:");
            resourceTypeList[i].print(ps, prefix + "   ");
        }

        ps.println(prefix + " referenceList: ");
        {
            int i = 0;
            for(Pair<ResourceType, ReferenceListEntry[]> entry : referenceList) {
                for(ReferenceListEntry refListEntry : entry.getB()) {
                    ps.println(prefix + "  [" + i++ + "]:");
                    refListEntry.print(ps, prefix + "   ");
                }
            }
        }

        ps.println(prefix + " resourceNameList: ");
        {
            int i = 0;
            for(Pair<ReferenceListEntry, ResourceName> entry : resourceNameList) {
                ps.println(prefix + "  [" + i++ + "]:");
                entry.getB().print(ps, prefix + "   ");
            }
        }
    }

    public void print(PrintStream ps, String prefix) {
        ps.println(prefix + "ResourceMap:");
        printFields(ps, prefix);
    }

    /*
    public byte[] getBytes() {
	byte[] result = new byte[length()];
	int offset = 0;
	System.arraycopy(this.reserved1, 0, result, offset, this.reserved1.length); offset += this.reserved1.length;
	System.arraycopy(this.reserved2, 0, result, offset, this.reserved2.length); offset += this.reserved2.length;
	System.arraycopy(this.reserved3, 0, result, offset, this.reserved3.length); offset += this.reserved3.length;
	System.arraycopy(this.resourceForkAttributes, 0, result, offset, this.resourceForkAttributes.length); offset += this.resourceForkAttributes.length;
	System.arraycopy(this.typeListOffset, 0, result, offset, this.typeListOffset.length); offset += this.typeListOffset.length;
	System.arraycopy(this.nameListOffset, 0, result, offset, this.nameListOffset.length); offset += this.nameListOffset.length;
	System.arraycopy(this.typeCount, 0, result, offset, this.typeCount.length); offset += this.typeCount.length;
	for(int i = 0; i < resourceTypeList.length; ++i) {
	    byte[] tempData = this.resourceTypeList[i].getBytes();
	    System.arraycopy(tempData, 0, result, offset, tempData.length); offset += tempData.length;
	}
	return result;
    }*/
}
