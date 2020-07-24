package com.shijingsh.core.storage.lucene.converter.index;

import com.shijingsh.core.storage.StorageCondition;
import com.shijingsh.core.storage.exception.StorageQueryException;
import com.shijingsh.core.storage.lucene.annotation.LuceneIndex;
import com.shijingsh.core.storage.lucene.converter.IndexConverter;
import com.shijingsh.core.storage.lucene.converter.LuceneContext;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.*;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.LinkedList;

/**
 * 枚举索引转换器
 *
 * @author Birdy
 *
 */
public class EnumerationIndexConverter implements IndexConverter {

    @Override
    public Iterable<IndexableField> convert(LuceneContext context, String path, Field field, LuceneIndex annotation, Type type, Object data) {
        Collection<IndexableField> indexables = new LinkedList<>();
        indexables.add(new StringField(path, data.toString(), Store.NO));
        return indexables;
    }

    @Override
    public Query query(LuceneContext context, String path, Field field, LuceneIndex annotation, Type type, StorageCondition condition, Object... data) {
        if (!condition.checkValues(data)) {
            throw new StorageQueryException();
        }
        Query query = null;
        switch (condition) {
        case All:
            query = new MatchAllDocsQuery();
            break;
        case Between:
            query = TermRangeQuery.newStringRange(path, data[0].toString(), data[1].toString(), true, true);
            break;
        case Equal:
            query = new TermQuery(new Term(path, data[0].toString()));
            break;
        case Higher:
            query = TermRangeQuery.newStringRange(path, data[0].toString(), null, false, true);
            break;
        case In:
            BooleanQuery.Builder buffer = new BooleanQuery.Builder();
            for (int index = 0, size = data.length; index < size; index++) {
                query = new TermQuery(new Term(path, data[index].toString()));
                buffer.add(query, Occur.SHOULD);
            }
            query = buffer.build();
            break;
        case Lower:
            query = TermRangeQuery.newStringRange(path, null, data[0].toString(), true, false);
            break;
        case Unequal:
            query = new TermQuery(new Term(path, data[0].toString()));
            query = new BooleanQuery.Builder().add(query, Occur.MUST_NOT).build();
            break;
        default:
            throw new UnsupportedOperationException();
        }
        return query;
    }

}
