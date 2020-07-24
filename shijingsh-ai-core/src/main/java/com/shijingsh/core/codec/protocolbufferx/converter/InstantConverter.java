package com.shijingsh.core.codec.protocolbufferx.converter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Date;

import com.shijingsh.core.codec.exception.CodecConvertionException;
import com.shijingsh.core.codec.protocolbufferx.ProtocolReader;
import com.shijingsh.core.codec.protocolbufferx.ProtocolWriter;
import com.shijingsh.core.codec.specification.ClassDefinition;
import com.shijingsh.core.common.reflection.Specification;

/**
 * 时间转换器
 *
 * @author Birdy
 *
 */
public class InstantConverter extends ProtocolConverter<Object> {

    /** 0000 0000(Null标记) */
    private static final byte NULL_MARK = (byte) 0x00;

    /** 0000 0000(日期标记) */
    private static final byte DATE_MARK = (byte) 0x01;

    /** 0000 0001(瞬间标记) */
    private static final byte INSTANT_MARK = (byte) 0x02;

    @Override
    public Object readValueFrom(ProtocolReader context, Type type, ClassDefinition definition) throws IOException {
        InputStream in = context.getInputStream();
        byte information = (byte) in.read();
        byte mark = getMark(information);
        if (mark == NULL_MARK) {
            return null;
        }
        long time = NumberConverter.readNumber(in).longValue();
        if (mark == DATE_MARK) {
            Date value = new Date(time);
            return value;
        } else if (mark == INSTANT_MARK) {
            Instant value = Instant.ofEpochMilli(time);
            return value;
        }
        throw new CodecConvertionException();
    }

    @Override
    public void writeValueTo(ProtocolWriter context, Type type, ClassDefinition definition, Object value) throws IOException {
        OutputStream out = context.getOutputStream();
        byte information = ClassDefinition.getCode(Specification.INSTANT);
        if (value == null) {
            out.write(information);
            return;
        }
        long time;
        if (value instanceof Date) {
            information |= DATE_MARK;
            Date date = (Date) value;
            time = date.getTime();
        } else if (value instanceof Instant) {
            information |= INSTANT_MARK;
            Instant instant = (Instant) value;
            time = instant.toEpochMilli();
        } else {
            throw new CodecConvertionException();
        }
        out.write(information);
        NumberConverter.writeNumber(out, time);
    }

}
