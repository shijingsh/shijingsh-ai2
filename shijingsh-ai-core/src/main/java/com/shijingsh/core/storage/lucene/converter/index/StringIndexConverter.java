package com.shijingsh.core.storage.lucene.converter.index;

import com.shijingsh.core.storage.StorageCondition;
import com.shijingsh.core.storage.exception.StorageQueryException;
import com.shijingsh.core.storage.lucene.annotation.LuceneIndex;
import com.shijingsh.core.storage.lucene.annotation.LuceneTerm;
import com.shijingsh.core.storage.lucene.converter.IndexConverter;
import com.shijingsh.core.storage.lucene.converter.LuceneContext;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.*;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.LinkedList;

/**
 * 字符串索引转换器
 *
 * @author Birdy
 *
 */
public class StringIndexConverter implements IndexConverter {

    @Override
    public Iterable<IndexableField> convert(LuceneContext context, String path, Field field, LuceneIndex annotation, Type type, Object data) {
        Collection<IndexableField> indexables = new LinkedList<>();
        FieldType configuration = new FieldType();
        configuration.setIndexOptions(IndexOptions.DOCS);
        if (annotation.analyze()) {
            configuration.setTokenized(true);

            LuceneTerm negative = annotation.negative();
            if (negative.offset()) {
                configuration.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
            } else if (negative.position()) {
                configuration.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
            } else if (negative.frequency()) {
                configuration.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
            }

            LuceneTerm positive = annotation.positive();
            if (positive.offset()) {
                configuration.setStoreTermVectorOffsets(true);
            }
            if (positive.position()) {
                configuration.setStoreTermVectorPositions(true);
            }
            if (positive.frequency()) {
                configuration.setStoreTermVectors(true);
            }
        }
        if(data==null){
            data = "";
        }
        indexables.add(new org.apache.lucene.document.Field(path, (String) data, configuration));
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
            query = TermRangeQuery.newStringRange(path, (String) data[0], (String) data[1], true, true);
            break;
        case Equal:
            query = new TermQuery(new Term(path, (String) data[0]));
            break;
        case Higher:
            query = TermRangeQuery.newStringRange(path, (String) data[0], null, false, true);
            break;
        case In:
            BooleanQuery.Builder buffer = new BooleanQuery.Builder();
            for (int index = 0, size = data.length; index < size; index++) {
                query = new TermQuery(new Term(path, (String) data[index]));
                buffer.add(query, Occur.SHOULD);
            }
            query = buffer.build();
            break;
        case Lower:
            query = TermRangeQuery.newStringRange(path, null, (String) data[0], true, false);
            break;
        case Unequal:
            query = new TermQuery(new Term(path, (String) data[0]));
            query = new BooleanQuery.Builder().add(query, Occur.MUST_NOT).build();
            break;
        default:
            throw new UnsupportedOperationException();
        }
        return query;
    }

}
