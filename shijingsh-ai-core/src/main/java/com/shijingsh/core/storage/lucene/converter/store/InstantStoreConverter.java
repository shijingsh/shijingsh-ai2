package com.shijingsh.core.storage.lucene.converter.store;

import com.shijingsh.core.common.reflection.TypeUtility;
import com.shijingsh.core.storage.exception.StorageException;
import com.shijingsh.core.storage.lucene.annotation.LuceneStore;
import com.shijingsh.core.storage.lucene.converter.LuceneContext;
import com.shijingsh.core.storage.lucene.converter.StoreConverter;
import com.shijingsh.core.utility.ClassUtility;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.IndexableField;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.time.*;
import java.util.Date;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * 时间存储转换器
 *
 * @author Birdy
 *
 */
public class InstantStoreConverter implements StoreConverter {

    @Override
    public Object decode(LuceneContext context, String path, Field field, LuceneStore annotation, Type type, NavigableMap<String, IndexableField> indexables) {
        String from = path;
        char character = path.charAt(path.length() - 1);
        character++;
        String to = path.substring(0, path.length() - 1) + character;
        indexables = indexables.subMap(from, true, to, false);
        IndexableField indexable = indexables.firstEntry().getValue();
        Class<?> clazz = TypeUtility.getRawType(type, null);
        clazz = ClassUtility.primitiveToWrapper(clazz);
        Number number = indexable.numericValue();
        if (Instant.class.isAssignableFrom(clazz)) {
            return Instant.ofEpochMilli(number.longValue());
        }
        if (Date.class.isAssignableFrom(clazz)) {
            return new Date(number.longValue());
        }
        if (LocalDate.class.isAssignableFrom(clazz)) {

        }
        if (LocalTime.class.isAssignableFrom(clazz)) {

        }
        if (LocalDateTime.class.isAssignableFrom(clazz)) {

        }
        if (ZonedDateTime.class.isAssignableFrom(clazz)) {

        }
        if (ZoneOffset.class.isAssignableFrom(clazz)) {

        }
        throw new StorageException();
    }

    @Override
    public NavigableMap<String, IndexableField> encode(LuceneContext context, String path, Field field, LuceneStore annotation, Type type, Object instance) {
        NavigableMap<String, IndexableField> indexables = new TreeMap<>();
        Class<?> clazz = TypeUtility.getRawType(type, null);
        if (Instant.class.isAssignableFrom(clazz)) {
            Instant instant = (Instant) instance;
            indexables.put(path, new StoredField(path, instant.toEpochMilli()));
            return indexables;
        }
        if (Date.class.isAssignableFrom(clazz)) {
            Date instant = (Date) instance;
            indexables.put(path, new StoredField(path, instant.getTime()));
            return indexables;
        }
        if (LocalDate.class.isAssignableFrom(clazz)) {

        }
        if (LocalTime.class.isAssignableFrom(clazz)) {

        }
        if (LocalDateTime.class.isAssignableFrom(clazz)) {

        }
        if (ZonedDateTime.class.isAssignableFrom(clazz)) {

        }
        if (ZoneOffset.class.isAssignableFrom(clazz)) {

        }
        throw new StorageException();
    }

}
