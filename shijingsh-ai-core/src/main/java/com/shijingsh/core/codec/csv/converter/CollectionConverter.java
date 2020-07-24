package com.shijingsh.core.codec.csv.converter;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Iterator;

import org.apache.commons.csv.CSVPrinter;

import com.shijingsh.core.codec.csv.CsvReader;
import com.shijingsh.core.codec.csv.CsvWriter;
import com.shijingsh.core.codec.specification.ClassDefinition;
import com.shijingsh.core.common.reflection.Specification;
import com.shijingsh.core.common.reflection.TypeUtility;
import com.shijingsh.core.utility.StringUtility;

/**
 * 集合转换器
 *
 * @author Birdy
 *
 */
public class CollectionConverter implements CsvConverter<Collection<Object>> {

    @Override
    public Collection<Object> readValueFrom(CsvReader context, Type type) throws Exception {
        // TODO 处理null
        Iterator<String> in = context.getInputStream();
        String check = in.next();
        if (StringUtility.isEmpty(check)) {
            return null;
        }
        int length = Integer.valueOf(check);
        Class<?> clazz = TypeUtility.getRawType(type, null);
        // 兼容UniMi
        type = TypeUtility.refineType(type, Collection.class);
        ParameterizedType parameterizedType = ParameterizedType.class.cast(type);
        Type[] types = parameterizedType.getActualTypeArguments();
        ClassDefinition definition = context.getClassDefinition(clazz);
        Collection<Object> collection = (Collection) definition.getInstance();
        Class<?> elementClazz = TypeUtility.getRawType(types[0], null);
        CsvConverter converter = context.getCsvConverter(Specification.getSpecification(elementClazz));
        for (int index = 0; index < length; index++) {
            Object element = converter.readValueFrom(context, types[0]);
            collection.add(element);
        }
        return collection;
    }

    @Override
    public void writeValueTo(CsvWriter context, Type type, Collection<Object> value) throws Exception {
        // TODO 处理null
        CSVPrinter out = context.getOutputStream();
        if (value == null) {
            out.print(StringUtility.EMPTY);
            return;
        }
        // 兼容UniMi
        type = TypeUtility.refineType(type, Collection.class);
        ParameterizedType parameterizedType = ParameterizedType.class.cast(type);
        Type[] types = parameterizedType.getActualTypeArguments();
        Collection<?> collection = Collection.class.cast(value);
        out.print(collection.size());
        Class<?> elementClazz = TypeUtility.getRawType(types[0], null);
        CsvConverter converter = context.getCsvConverter(Specification.getSpecification(elementClazz));
        for (Object element : collection) {
            converter.writeValueTo(context, types[0], element);
        }
        return;
    }

}
