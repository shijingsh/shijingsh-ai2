package com.shijingsh.core.codec.csv.converter;

import java.lang.reflect.Type;
import java.util.Iterator;

import org.apache.commons.csv.CSVPrinter;

import com.shijingsh.core.codec.csv.CsvReader;
import com.shijingsh.core.codec.csv.CsvWriter;
import com.shijingsh.core.common.reflection.TypeUtility;
import com.shijingsh.core.utility.NumberUtility;
import com.shijingsh.core.utility.StringUtility;

/**
 * 数值转换器
 *
 * @author Birdy
 *
 */
public class NumberConverter implements CsvConverter<Number> {

    @Override
    public Number readValueFrom(CsvReader context, Type type) throws Exception {
        // TODO 处理null
        Iterator<String> in = context.getInputStream();
        String element = in.next();
        if (StringUtility.isEmpty(element)) {
            return null;
        }
        Class<Number> clazz = (Class<Number>) TypeUtility.getRawType(type, null);
        return NumberUtility.convert(element, clazz);
    }

    @Override
    public void writeValueTo(CsvWriter context, Type type, Number value) throws Exception {
        // TODO 处理null
        CSVPrinter out = context.getOutputStream();
        if (value == null) {
            out.print(StringUtility.EMPTY);
            return;
        }
        out.print(value);
    }

}
