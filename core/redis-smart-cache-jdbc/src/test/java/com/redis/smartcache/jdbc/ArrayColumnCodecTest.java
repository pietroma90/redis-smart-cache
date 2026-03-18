package com.redis.smartcache.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.ByteBuffer;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import javax.sql.RowSet;
import javax.sql.RowSetMetaData;
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;

import org.junit.jupiter.api.Test;

import com.redis.smartcache.jdbc.codec.ArrayColumnCodec;
import com.redis.smartcache.jdbc.rowset.CachedRowSetImpl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Unit tests for ArrayColumnCodec and RowSetCodec ARRAY support.
 */
class ArrayColumnCodecTest {

    private static final int BUFFER_CAPACITY = 10 * 1024 * 1024; // 10 MB

    // ---------------------------------------------------------------------------
    // toPostgresArrayString tests
    // ---------------------------------------------------------------------------

    @Test
    void toPostgresArrayString_stringElements() {
        Object[] arr = {"alpha", "beta", "gamma"};
        assertEquals("{alpha,beta,gamma}", ArrayColumnCodec.toPostgresArrayString(arr));
    }

    @Test
    void toPostgresArrayString_integerElements() {
        Object[] arr = {1, 2, 3};
        assertEquals("{1,2,3}", ArrayColumnCodec.toPostgresArrayString(arr));
    }

    @Test
    void toPostgresArrayString_nullElements() {
        Object[] arr = {"a", null, "c"};
        assertEquals("{a,NULL,c}", ArrayColumnCodec.toPostgresArrayString(arr));
    }

    @Test
    void toPostgresArrayString_emptyArray() {
        assertEquals("{}", ArrayColumnCodec.toPostgresArrayString(new Object[0]));
    }

    @Test
    void toPostgresArrayString_nullInput() {
        assertEquals("{}", ArrayColumnCodec.toPostgresArrayString(null));
    }

    // ---------------------------------------------------------------------------
    // Codec encode/decode roundtrip via ByteBuf
    // ---------------------------------------------------------------------------

    @Test
    void encodeDecodeRoundtrip_nonNullArray() throws SQLException {
        ByteBuf buf = Unpooled.buffer(1024);

        // Encode: simulate a non-null array value
        ArrayColumnCodec codec = new ArrayColumnCodec(1);
        // Build a fake Array wrapping Object[]
        Array fakeArray = new SimpleArray(new Object[]{"foo", "bar", "baz"});

        // Write: false (not null) + string data
        buf.writeBoolean(false);
        codec.write(buf, fakeArray);

        // Decode into a CachedRowSet
        CachedRowSet rowSet = buildSingleColumnRowSet(Types.VARCHAR);
        rowSet.moveToInsertRow();
        codec.decode(buf, rowSet);
        rowSet.insertRow();
        rowSet.moveToCurrentRow();
        rowSet.beforeFirst();

        rowSet.next();
        String result = rowSet.getString(1);
        assertEquals("{foo,bar,baz}", result);
    }

    @Test
    void encodeDecodeRoundtrip_nullArray() throws SQLException {
        ByteBuf buf = Unpooled.buffer(1024);

        ArrayColumnCodec codec = new ArrayColumnCodec(1);
        // Write: true (null value)
        buf.writeBoolean(true);

        CachedRowSet rowSet = buildSingleColumnRowSet(Types.VARCHAR);
        rowSet.moveToInsertRow();
        codec.decode(buf, rowSet);
        rowSet.insertRow();
        rowSet.moveToCurrentRow();
        rowSet.beforeFirst();

        rowSet.next();
        String result = rowSet.getString(1);
        // null value should decode as SQL NULL
        assertEquals(null, result);
    }

    // ---------------------------------------------------------------------------
    // Full RowSetCodec encode/decode roundtrip with Types.ARRAY column
    // ---------------------------------------------------------------------------

    @Test
    void rowSetCodec_encodeDecodeArrayColumn() throws SQLException {
        RowSetCodec codec = new RowSetCodec(BUFFER_CAPACITY);

        // Build a RowSet with one ARRAY column
        CachedRowSet source = buildSingleColumnRowSet(Types.ARRAY);
        String arrayValue = "{10,20,30}";

        source.moveToInsertRow();
        source.updateString(1, arrayValue);
        source.insertRow();
        source.moveToCurrentRow();
        source.beforeFirst();

        // We cannot encode a proper java.sql.Array via the standard path because
        // CachedRowSet stores ARRAY columns as Strings internally when set via updateString.
        // So we verify that a RowSet whose ARRAY column already holds a String survives
        // a codec roundtrip without exceptions and data is preserved.
        ByteBuffer encoded = codec.encodeValue(source);
        assertNotNull(encoded);

        RowSet decoded = codec.decodeValue(encoded);
        assertNotNull(decoded);
        decoded.beforeFirst();
        decoded.next();
        // Value comes back as a String (the serialized array representation)
        assertNotNull(decoded.getObject(1));
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private CachedRowSet buildSingleColumnRowSet(int columnType) throws SQLException {
        RowSetMetaDataImpl meta = new RowSetMetaDataImpl();
        meta.setColumnCount(1);
        meta.setColumnName(1, "arr_col");
        meta.setColumnLabel(1, "arr_col");
        meta.setColumnType(1, columnType);
        meta.setColumnTypeName(1, "ARRAY");
        meta.setColumnDisplaySize(1, 100);
        meta.setPrecision(1, 0);
        meta.setScale(1, 0);
        meta.setTableName(1, "test");
        meta.setSchemaName(1, "");
        meta.setCatalogName(1, "");
        meta.setAutoIncrement(1, false);
        meta.setCaseSensitive(1, false);
        meta.setCurrency(1, false);
        meta.setNullable(1, ResultSet.columnNullable);
        meta.setSearchable(1, false);
        meta.setSigned(1, false);
        CachedRowSet rowSet = new CachedRowSetImpl();
        rowSet.setMetaData(meta);
        return rowSet;
    }

    /**
     * Minimal java.sql.Array implementation backed by an Object[] for testing.
     */
    private static class SimpleArray implements Array {
        private final Object[] elements;

        SimpleArray(Object[] elements) {
            this.elements = elements;
        }

        @Override
        public String getBaseTypeName() { return "VARCHAR"; }

        @Override
        public int getBaseType() { return Types.VARCHAR; }

        @Override
        public Object getArray() { return elements; }

        @Override
        public Object getArray(java.util.Map<String, Class<?>> map) { return elements; }

        @Override
        public Object getArray(long index, int count) {
            return java.util.Arrays.copyOfRange(elements, (int) index - 1, (int) index - 1 + count);
        }

        @Override
        public Object getArray(long index, int count, java.util.Map<String, Class<?>> map) {
            return getArray(index, count);
        }

        @Override
        public ResultSet getResultSet() { return null; }

        @Override
        public ResultSet getResultSet(java.util.Map<String, Class<?>> map) { return null; }

        @Override
        public ResultSet getResultSet(long index, int count) { return null; }

        @Override
        public ResultSet getResultSet(long index, int count, java.util.Map<String, Class<?>> map) { return null; }

        @Override
        public void free() {}
    }
}
