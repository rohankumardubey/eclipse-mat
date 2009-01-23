/*******************************************************************************
 * Copyright (c) 2008 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.hprof;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;

import org.eclipse.mat.parser.io.PositionInputStream;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;
import org.eclipse.mat.snapshot.model.ObjectReference;

// Hprof binary format as defined here:
// https://heap-snapshot.dev.java.net/files/documents/4282/31543/hprof-binary-format.html

/* package */abstract class AbstractParser
{
    /* package */enum Version
    {
        JDK12BETA3("JAVA PROFILE 1.0"), //$NON-NLS-1$
        JDK12BETA4("JAVA PROFILE 1.0.1"),  //$NON-NLS-1$
        JDK6("JAVA PROFILE 1.0.2");//$NON-NLS-1$

        private String label;

        private Version(String label)
        {
            this.label = label;
        }

        public static final Version byLabel(String label)
        {
            for (Version v : Version.values())
            {
                if (v.label.equals(label))
                    return v;
            }
            return null;
        }

        public String getLabel()
        {
            return label;
        }
    }

    interface Constants
    {
        interface Record
        {
            int STRING_IN_UTF8 = 0x01;
            int LOAD_CLASS = 0x02;
            int UNLOAD_CLASS = 0x03;
            int STACK_FRAME = 0x04;
            int STACK_TRACE = 0x05;
            int ALLOC_SITES = 0x06;
            int HEAP_SUMMARY = 0x07;
            int START_THREAD = 0x0a;
            int END_THREAD = 0x0b;
            int HEAP_DUMP = 0x0c;
            int HEAP_DUMP_SEGMENT = 0x1c;
            int HEAP_DUMP_END = 0x2c;
            int CPU_SAMPLES = 0x0d;
            int CONTROL_SETTINGS = 0x0e;
        }

        interface DumpSegment
        {
            int ROOT_UNKNOWN = 0xff;
            int ROOT_JNI_GLOBAL = 0x01;
            int ROOT_JNI_LOCAL = 0x02;
            int ROOT_JAVA_FRAME = 0x03;
            int ROOT_NATIVE_STACK = 0x04;
            int ROOT_STICKY_CLASS = 0x05;
            int ROOT_THREAD_BLOCK = 0x06;
            int ROOT_MONITOR_USED = 0x07;
            int ROOT_THREAD_OBJECT = 0x08;
            int CLASS_DUMP = 0x20;
            int INSTANCE_DUMP = 0x21;
            int OBJECT_ARRAY_DUMP = 0x22;
            int PRIMITIVE_ARRAY_DUMP = 0x23;
        }
    }

    protected PositionInputStream in;
    protected Version version;
    protected int idSize;

    /* package */AbstractParser()
    {}

    /* protected */static Version readVersion(InputStream in) throws IOException
    {
        StringBuilder version = new StringBuilder();

        int bytesRead = 0;
        while (bytesRead < 20)
        {
            byte b = (byte) in.read();
            bytesRead++;

            if (b != 0)
            {
                version.append((char) b);
            }
            else
            {
                Version answer = Version.byLabel(version.toString());
                if (answer == null)
                {
                    if (bytesRead <= 13) // did not read "JAVA PROFILE "
                        throw new IOException(Messages.AbstractParser_Error_NotHeapDump);
                    else
                        throw new IOException(MessageFormat.format(Messages.AbstractParser_Error_UnknownHPROFVersion, version.toString()));
                }

                if (answer == Version.JDK12BETA3) // not supported by MAT
                    throw new IOException(MessageFormat.format(Messages.AbstractParser_Error_UnsupportedHPROFVersion, answer.getLabel()));
                return answer;
            }
        }

        throw new IOException(Messages.AbstractParser_Error_InvalidHPROFHeader);
    }

    protected long readUnsignedInt() throws IOException
    {
        return (0x0FFFFFFFFL & in.readInt());
    }

    protected long readID() throws IOException
    {
        return idSize == 4 ? (0x0FFFFFFFFL & in.readInt()) : in.readLong();
    }

    protected Object readValue(ISnapshot snapshot) throws IOException
    {
        byte type = in.readByte();
        return readValue(snapshot, type);
    }

    protected Object readValue(ISnapshot snapshot, int type) throws IOException
    {
        switch (type)
        {
            case IObject.Type.OBJECT:
                long id = readID();
                return id == 0 ? null : new ObjectReference(snapshot, id);
            case IObject.Type.BOOLEAN:
                return in.readByte() != 0;
            case IObject.Type.CHAR:
                return in.readChar();
            case IObject.Type.FLOAT:
                return in.readFloat();
            case IObject.Type.DOUBLE:
                return in.readDouble();
            case IObject.Type.BYTE:
                return in.readByte();
            case IObject.Type.SHORT:
                return in.readShort();
            case IObject.Type.INT:
                return in.readInt();
            case IObject.Type.LONG:
                return in.readLong();
            default:
                throw new IOException(MessageFormat.format(Messages.AbstractParser_Error_IllegalType, type));
        }
    }

    protected void skipValue() throws IOException
    {
        byte type = in.readByte();
        skipValue(type);
    }

    protected void skipValue(int type) throws IOException
    {
        if (type == 2)
            in.skipBytes(idSize);
        else
            in.skipBytes(IPrimitiveArray.ELEMENT_SIZE[type]);
    }

}
