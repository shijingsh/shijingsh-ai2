package com.shijingsh.core.codec.csv.converter;

import java.lang.reflect.Type;
import java.util.Iterator;

import org.apache.commons.csv.CSVPrinter;

import com.shijingsh.core.codec.csv.CsvReader;
import com.shijingsh.core.codec.csv.CsvWriter;
import com.shijingsh.core.utility.StringUtility;

/**
 * 字符串转换器
 *
 * <pre>
 * 为了兼容null与AWK搜索,所有字符串以分号结束.
 * </pre>
 *
 * @author Birdy
 *
 */
public class StringConverter implements CsvConverter<Object> {

    @Override
    public Object readValueFrom(CsvReader context, Type type) throws Exception {
        Iterator<String> in = context.getInputStream();
        String element = in.next();
        if (StringUtility.isEmpty(element)) {
            return null;
        }
        element = element.substring(0, element.length() - 1);
        if (type == char.class || type == Character.class) {
            return element.charAt(0);
        } else {
            return element;
        }
    }

    @Override
    public void writeValueTo(CsvWriter context, Type type, Object value) throws Exception {
        CSVPrinter out = context.getOutputStream();
        if (value == null) {
            out.print(StringUtility.EMPTY);
            return;
        }
        value = value + StringUtility.SEMICOLON;
        out.print(value);
    }

}
