/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.catacombae.hfsexplorer.tools;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.HashMap;
import org.catacombae.hfsexplorer.types.alias.AliasHeader;
import org.catacombae.hfsexplorer.types.dsstore.DSStoreFreeList;
import org.catacombae.hfsexplorer.types.dsstore.DSStoreHeader;
import org.catacombae.hfsexplorer.types.dsstore.DSStoreRootBlock;
import org.catacombae.hfsexplorer.types.dsstore.DSStoreTableOfContents;
import org.catacombae.hfsexplorer.types.dsstore.DSStoreTableOfContentsEntry;
import org.catacombae.hfsexplorer.types.dsstore.DSStoreTreeBlock;
import org.catacombae.util.Util;

/**
 *
 * @author erik
 */
public class DSStoreInfo {
    private static void hexDump(byte[] data, int dataOffset, int dataSize,
            String insetString)
    {
        int hexdigits = 0;
        for(int j = dataSize; j != 0; j >>>= 8) {
            hexdigits += 2;
        }

        for(int j = 0; j < dataSize; j += 16) {
            int kMax = ((j + 16) <= dataSize) ? 16 : (dataSize - j);
            System.out.print(insetString);
            System.out.printf("%0" + hexdigits + "X | ", j);
            for(int k = 0; k < 16; ++k) {
                if(k != 0) {
                    System.out.print(" ");
                }
                if(k < kMax) {
                    System.out.print(Util.toHexStringBE(
                            (byte) data[dataOffset + j + k]));
                }
                else {
                    System.out.print("  ");
                }
            }
            System.out.print(" | ");
            for(int k = 0; k < 16; ++k) {
                if(k < kMax) {
                    byte curByte = data[dataOffset + j + k];
                    if(curByte < 0x20 || curByte >= 127) {
                        /* ASCII control character or outside ASCII range.
                         Represented by '.'. */
                        System.out.print(".");
                    }
                    else {
                        System.out.print((char) curByte);
                    }
                }
                else {
                    System.out.print("  ");
                }
            }

            System.out.println();
        }
    }

    private static enum EntryType {
        EntryTypeInvalid(null),
        EntryTypeExtendedInfoEnd(-1),
        EntryTypeDirectoryName(0),
        EntryTypeDirectoryIDs(1),
        EntryTypeAbsolutePath(2),
        EntryTypeAppleShareZoneName(3),
        EntryTypeAppleShareServerName(4),
        EntryTypeAppleShareUserName(5),
        EntryTypeDriverName(6),
        EntryTypeRevisedAppleShareInfo(9),
        EntryTypeAppleRemoteAccessDialupInfo(10),
        EntryTypeFileNameUnicode(14),
        EntryTypeDirectoryNameUnicode(15),
        EntryTypeUNIXRelativePath(18),
        EntryTypeUNIXVolumePath(19),
        /**
         * Nested alias referencing the disk image where this file is located.
         */
        EntryTypeDiskImageAlias(20);

        private final Integer value;

        private EntryType(Integer value) {
            this.value = value;
        }

        public static EntryType lookupByValue(int value) {
            for(EntryType e : EntryType.class.getEnumConstants()) {
                if(e.value != null && e.value == value) {
                    return e;
                }
            }

            return EntryTypeInvalid;
        }
    };

    private static void printAlias(byte[] data, int offset, int size,
            String insetString)
    {
        AliasHeader ah = new AliasHeader(data, offset);
        System.out.println(insetString + "    Alias data:");
        ah.printFields(System.out, insetString + "     ");

        /* After the fixed-size header follows variable sized
         * entries each starting with a 2-byte type and a 2-byte
         * size field. */
        for(int j = AliasHeader.STRUCTSIZE; j < size; ) {
            short entryTypeValue =
                    Util.readShortBE(data, offset + j);
            j += 2;

            int entrySize =
                    Util.unsign(Util.readShortBE(data,
                    offset + j));
            j += 2;

            EntryType entryType =
                    EntryType.lookupByValue(entryTypeValue);

            System.out.print(insetString + "      ");
            switch(entryType) {
            case EntryTypeExtendedInfoEnd:
                System.out.println("Extended info end:");
                break;
            case EntryTypeDirectoryName:
                System.out.println("Directory name:");
                break;
            case EntryTypeDirectoryIDs:
                System.out.println("Directory IDs:");
                break;
            case EntryTypeAbsolutePath:
                System.out.println("Absolute path:");
                break;
            case EntryTypeAppleShareZoneName:
                System.out.println("AppleShare zone name:");
                break;
            case EntryTypeAppleShareServerName:
                System.out.println("AppleShare server name:");
                break;
            case EntryTypeAppleShareUserName:
                System.out.println("AppleShare user name:");
                break;
            case EntryTypeDriverName:
                System.out.println("Driver name:");
                break;
            case EntryTypeRevisedAppleShareInfo:
                System.out.println("Revised AppleShare info:");
                break;
            case EntryTypeAppleRemoteAccessDialupInfo:
                System.out.println("AppleRemoteAccess dialup " +
                        "info:");
                break;
            case EntryTypeFileNameUnicode:
                System.out.println("File name (Unicode):");
                break;
            case EntryTypeDirectoryNameUnicode:
                System.out.println("Directory name (Unicode):");
                break;
            case EntryTypeUNIXRelativePath:
                System.out.println("Relative path (UNIX-style):");
                break;
            case EntryTypeUNIXVolumePath:
                System.out.println("Volume path (UNIX-style):");
                break;
            case EntryTypeDiskImageAlias:
                System.out.println("Source disk image alias:");
                break;
            case EntryTypeInvalid:
            default:
                System.out.println("Unknown entry type " +
                        entryTypeValue + ":");
                break;
            }

            System.out.println(insetString + "        Size: " +
                    entrySize + " " +
                    "(0x" + Util.toHexStringBE((short) entrySize) +
                    ")");

            switch(entryType) {
            case EntryTypeExtendedInfoEnd:
                break;
            case EntryTypeDirectoryName:
                System.out.println(insetString + "        Name: " +
                        Util.readString(data, offset + j,
                        entrySize, "MacRoman"));
                break;
            case EntryTypeDirectoryIDs:
                hexDump(data, offset + j, entrySize,
                        insetString + "        ");
                break;
            case EntryTypeAbsolutePath:
                System.out.println(insetString + "        Path: " +
                        Util.readString(data, offset + j,
                        entrySize, "MacRoman"));
                break;
            case EntryTypeAppleShareZoneName:
                hexDump(data, offset + j, entrySize,
                        insetString + "        ");
                break;
            case EntryTypeAppleShareServerName:
                hexDump(data, offset + j, entrySize,
                        insetString + "        ");
                break;
            case EntryTypeAppleShareUserName:
                hexDump(data, offset + j, entrySize,
                        insetString + "        ");
                break;
            case EntryTypeDriverName:
                hexDump(data, offset + j, entrySize,
                        insetString + "        ");
                break;
            case EntryTypeRevisedAppleShareInfo:
                hexDump(data, offset + j, entrySize,
                        insetString + "        ");
                break;
            case EntryTypeAppleRemoteAccessDialupInfo:
                hexDump(data, offset + j, entrySize,
                        insetString + "        ");
                break;
            case EntryTypeFileNameUnicode:
            case EntryTypeDirectoryNameUnicode:
                System.out.println(insetString + "        Name " +
                        "length: " + Util.readShortBE(data,
                        offset + j));
                System.out.println(insetString + "        Name: " +
                        Util.readString(data,
                        offset + j + 2, entrySize, "UTF-16BE"));
                break;
            case EntryTypeUNIXRelativePath:
                System.out.println(insetString + "        Path: " +
                        Util.readString(data, offset + j,
                        entrySize, "UTF-8"));
                break;
            case EntryTypeUNIXVolumePath:
                System.out.println(insetString + "        Path: " +
                        Util.readString(data, offset + j,
                        entrySize, "UTF-8"));
                break;
            case EntryTypeDiskImageAlias:
                printAlias(data, offset + j, entrySize,
                        insetString + "    ");
                break;
            case EntryTypeInvalid:
            default:
                hexDump(data, offset + j, entrySize,
                        insetString + "        ");
                break;
            }

            j += entrySize;
            if((entrySize % 2) != 0) {
                /* Odd lengths are padded with an extra 0x0 byte. */
                ++j;
            }
        }
    }

    private static void printTreeBlockRecursive(byte[] dsStoreData,
            DSStoreRootBlock rootBlock, int inset, int blockID)
    {
        String insetString = "";
        while(inset != 0) {
            insetString += "        ";
            --inset;
        }

        long dataBlockLocator = rootBlock.getOffsetList()[blockID];
        long dataBlockOffset = (dataBlockLocator & ~0x1FL);
        long dataBlockSize = 1L << (dataBlockLocator & 0x1F);

        System.out.println(insetString + "  First data block:");
        System.out.println(insetString + "    (offset: " + dataBlockOffset +
                ")");
        System.out.println(insetString + "    (size: " + dataBlockSize + ")");

        int blockMode =
                Util.readIntBE(dsStoreData, 4 + (int) dataBlockOffset + 0);
        int recordCount =
                Util.readIntBE(dsStoreData, 4 + (int) dataBlockOffset + 4);
        System.out.println(insetString + "    block mode: 0x" +
                Util.toHexStringBE(blockMode));
        System.out.println(insetString + "    record count: 0x" +
                Util.toHexStringBE(recordCount));

        /* Read the records. */
        int curOffset = 4 + (int) dataBlockOffset + 8;
        for(int i = 0; i < recordCount; ++i) {
            System.out.println(insetString + "    Record " + (i + 1) + ":");
            int childNodeBlockID = 0;
            if(blockMode != 0x0) {
                /* "index nodes" have the child node block id prepended to
                 * the record so that we can descend further into the
                 * tree. */
                childNodeBlockID = Util.readIntBE(dsStoreData, curOffset);
                System.out.println(insetString + "      Child node block ID: " +
                        childNodeBlockID);
                curOffset += 4;
            }

            int filenameLength = Util.readIntBE(dsStoreData, curOffset);
            System.out.println(insetString + "      Filename length: " +
                    filenameLength);
            curOffset += 4;

            String filename =
                    Util.readString(dsStoreData, curOffset, filenameLength * 2,
                    "UTF-16BE");
            System.out.println(insetString + "      Filename: " + filename);
            curOffset += filenameLength * 2;

            int structID = Util.readIntBE(dsStoreData, curOffset);
            System.out.println(insetString + "      Structure ID: " +
                    Util.toASCIIString(structID) + " " +
                    "(0x" + Util.toHexStringBE(structID) + ")");
            curOffset += 4;

            int structType = Util.readIntBE(dsStoreData, curOffset);
            System.out.println(insetString + "      Structure type: " +
                    Util.toASCIIString(structType) + " " +
                    "(0x" + Util.toHexStringBE(structType) + ")");
            curOffset += 4;

            if(Util.toASCIIString(structType).equals("long")) {
                /* A long is a 4 byte (32-bit) big-endian integer. */
                int value = Util.readIntBE(dsStoreData, curOffset);
                System.out.println(insetString + "      Value: " + value + " " +
                        "/ 0x" + Util.toHexStringBE(value));
                curOffset += 4;
            }
            else if(Util.toASCIIString(structType).equals("shor")) {
                /* A long is a 2 byte (16-bit) big-endian integer, padded to 4
                 * bytes. */
                short padding = Util.readShortBE(dsStoreData, curOffset);
                System.out.println(insetString + "      Padding: " + padding +
                        " / 0x" + Util.toHexStringBE(padding));
                curOffset += 2;

                short value = Util.readShortBE(dsStoreData, curOffset);
                System.out.println(insetString + "      Value: " + value + " " +
                        "/ 0x" + Util.toHexStringBE(value));
                curOffset += 2;
            }
            else if(Util.toASCIIString(structType).equals("blob")) {
                /* A blob has a size (32 bits) followed by variable-size binary
                 * data. */
                int blobSize = Util.readIntBE(dsStoreData, curOffset);
                System.out.println(insetString + "      Blob size: " +
                        blobSize);
                curOffset += 4;

                System.out.println(insetString + "      Blob data:");
                hexDump(dsStoreData, curOffset, blobSize,
                        insetString + "        ");

                if(Util.toASCIIString(structID).equals("pict")) {
                    /* This data is in Mac OS alias format, pointing at the
                     * location of the background image. */
                    printAlias(dsStoreData, curOffset, blobSize, insetString);
                }

                curOffset += blobSize;
            }
            else if(Util.toASCIIString(structType).equals("type")) {
                /* A long is a 4 byte (32-bit) big-endian integer. */
                int value = Util.readIntBE(dsStoreData, curOffset);
                System.out.println(insetString + "      Value: '" +
                        Util.toASCIIString(value) + "'");
                curOffset += 4;
            }
            else if(Util.toASCIIString(structType).equals("ustr")) {
                /* A long is a 4 byte (32-bit) big-endian integer. */
                int length = Util.readIntBE(dsStoreData, curOffset);
                System.out.println(insetString + "      String length: " +
                        length + " / 0x" + Util.toHexStringBE(length));
                curOffset += 4;

                String string =
                        Util.readString(dsStoreData, curOffset, length * 2,
                        "UTF-16BE");
                System.out.println(insetString + "      String: " + string);
                curOffset += length * 2;
            }
            else if(Util.toASCIIString(structType).equals("comp")) {
                /* A comp is an 8 byte (64-bit) big-endian integer. */
                long value = Util.readLongBE(dsStoreData, curOffset);
                System.out.println(insetString + "      Value: " + value + " " +
                        "/ 0x" + Util.toHexStringBE(value));
                curOffset += 8;
            }
            else if(Util.toASCIIString(structType).equals("dutc")) {
                /* A dutc is a UTC timestamp, 8 bytes and stored as 1/65536
                 * second intervals since 1904 (start of the pre-UNIX Mac OS
                 * epoch). */
                long value = Util.readLongBE(dsStoreData, curOffset);
                System.out.println(insetString + "      Timestamp: " + value +
                        " / 0x" + Util.toHexStringBE(value));
                curOffset += 8;
            }
            else if(Util.toASCIIString(structType).equals("bool")) {
                /* A bool is a one-byte boolean value expected to be 0 or 1. */
                byte value = dsStoreData[curOffset];
                System.out.println(insetString + "      Value: " + value +
                        " / 0x" + Util.toHexStringBE((byte) value));
                curOffset += 1;
            }
            else {
                throw new RuntimeException("Unknown struct type " +
                        "\"" + Util.toASCIIString(structType) + "\" " +
                        "(0x" + Util.toHexStringBE(structType) + ").");
            }

            if(blockMode != 0x0) {
                /* Print the contents of the child node in a depth-first
                 * manner. */
                System.out.println(insetString + "      Child node " +
                        childNodeBlockID + ":");
                printTreeBlockRecursive(dsStoreData, rootBlock, inset + 1,
                        childNodeBlockID);
            }
        }
    }

    public static void main(String[] args) {
        final RandomAccessFile dsStoreFile;
        final byte[] dsStoreData;

        if(args.length < 1) {
            System.err.println("usage: DSStoreInfo <file>");
            System.exit(1);
            return;
        }

        try {
            dsStoreFile = new RandomAccessFile(args[0], "r");
        } catch(IOException e) {
            System.err.println("Error while opening .DS_Store file: " +
                    e.getMessage());
            System.exit(1);
            return;
        }

        try {
            final long dsStoreFileLength = dsStoreFile.length();
            if(dsStoreFileLength < 0 || dsStoreFileLength > Integer.MAX_VALUE) {
                System.err.println(".DS_Store file is unreasonably large " +
                        "(" + dsStoreFileLength + "). Aborting...");
                System.exit(1);
                return;
            }
            dsStoreData = new byte[(int) dsStoreFileLength];
        } catch(IOException e) {
            System.err.println("Error while querying .DS_Store file size: " +
                    e.getMessage());
            System.exit(1);
            return;
        }

        try {
            int bytesRead = dsStoreFile.read(dsStoreData);
            if(bytesRead < dsStoreData.length) {
                System.err.println("Partial read while reading .DS_Store " +
                        "file data: " + bytesRead + " / " + dsStoreData.length +
                        " read");
                System.exit(1);
                return;
            }
        } catch(IOException e) {
            System.err.println("Error while reading .DS_Store file data: " +
                    e.getMessage());
            System.exit(1);
            return;
        }

        DSStoreHeader h = new DSStoreHeader(dsStoreData, 0);
        h.print(System.out, "");

        if(!Arrays.equals(h.getRawSignature(), DSStoreHeader.SIGNATURE)) {
            System.err.println("Mismatching root block offsets, this is not " +
                    "a valid .DS_Store file.");
            System.exit(1);
            return;
        }

        if(h.getRawRootBlockOffset1() != h.getRawRootBlockOffset2()) {
            System.err.println("Mismatching root block offsets, this is not " +
                    "a valid .DS_Store file.");
            System.exit(1);
            return;
        }

        DSStoreRootBlock rootBlock =
                new DSStoreRootBlock(dsStoreData, h.getRawRootBlockOffset1());
        rootBlock.print(System.out, "");

        DSStoreTableOfContents toc =
                new DSStoreTableOfContents(dsStoreData,
                h.getRawRootBlockOffset1() + rootBlock.maxSize());
        toc.print(System.out, "");

        DSStoreFreeList freeList =
                new DSStoreFreeList(dsStoreData, h.getRawRootBlockOffset1() +
                rootBlock.maxSize() + toc.occupiedSize());
        freeList.print(System.out, "");

        for(int i = 0; i < toc.getTocCount(); ++i) {
            DSStoreTableOfContentsEntry tocEntry = toc.getTocEntry(i);
            long treeLocator =
                    rootBlock.getOffsetList()[tocEntry.getRawTocValue()];
            long treeOffset = (treeLocator & ~0x1FL);
            long treeSize = 1L << (treeLocator & 0x1F);
            System.out.println("Printing contents of tree " + (i + 1) + " " +
                    "(\"" + Util.toASCIIString(tocEntry.getTocName()) + "\") " +
                    "/ raw locator 0x" + Util.toHexStringBE(treeLocator) + ":");
            System.out.println("  (offset: " + treeOffset + ")");
            System.out.println("  (size: " + treeSize + ")");

            DSStoreTreeBlock b =
                    new DSStoreTreeBlock(dsStoreData, 4 + (int) treeOffset);
            b.print(System.out, "  ");

            printTreeBlockRecursive(dsStoreData, rootBlock, 0,
                    b.getRawFirstDataBlockID());
        }

        try {
            dsStoreFile.close();
        } catch(IOException e) {
            System.err.println("Error while closing .DS_Store file: " +
                    e.getMessage());
            System.exit(1);
            return;
        }
    }
}
