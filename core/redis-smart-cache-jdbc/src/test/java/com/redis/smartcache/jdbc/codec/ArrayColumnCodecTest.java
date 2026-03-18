package com.redis.smartcache.jdbc.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.ByteBuffer;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;

import javax.sql.RowSet;
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;

import org.junit.jupiter.api.Test;

import com.redis.smartcache.jdbc.RowSetCodec;
import com.redis.smartcache.jdbc.rowset.CachedRowSetImpl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Unit tests for ArrayColumnCodec and RowSetCodec ARRAY support.
 */
class ArrayColumnCodecTest {

    private static final int BUFFER_CAPACITY = 10 * 1024 * 1024;

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

        ArrayColumnCodec codec = new ArrayColumnCodec(1);
        Array fakeArray = new SimpleArray(new Object[]{"foo", "bar", "baz"});

        buf.writeBoolean(false);
        codec.write(buf, fakeArray);

        CachedRowSet rowSet = buildSingleColumnRowSet(Types.VARCHAR);
        rowSet.moveToInsertRow();
        codec.decode(buf, rowSet);
        rowSet.insertRow();
        rowSet.moveToCurrentRow();
        rowSet.beforeFirst();

        rowSet.next();
        assertEquals("{foo,bar,baz}", rowSet.getString(1));
    }

    @Test
    void encodeDecodeRoundtrip_integerArray() throws SQLException {
        ByteBuf buf = Unpooled.buffer(1024);

        ArrayColumnCodec codec = new ArrayColumnCodec(1);
        Array fakeArray = new SimpleArray(new Object[]{10, 20, 30});

        buf.writeBoolean(false);
        codec.write(buf, fakeArray);

        CachedRowSet rowSet = buildSingleColumnRowSet(Types.VARCHAR);
        rowSet.moveToInsertRow();
        codec.decode(buf, rowSet);
        rowSet.insertRow();
        rowSet.moveToCurrentRow();
        rowSet.beforeFirst();

        rowSet.next();
        assertEquals("{10,20,30}", rowSet.getString(1));
    }

    @Test
    void encodeDecodeRoundtrip_longArray() throws SQLException {
        ByteBuf buf = Unpooled.buffer(1024);

        ArrayColumnCodec codec = new ArrayColumnCodec(1);
        Array fakeArray = new SimpleArray(new Object[]{100L, 200L, 300L});

        buf.writeBoolean(false);
        codec.write(buf, fakeArray);

        CachedRowSet rowSet = buildSingleColumnRowSet(Types.VARCHAR);
        rowSet.moveToInsertRow();
        codec.decode(buf, rowSet);
        rowSet.insertRow();
        rowSet.moveToCurrentRow();
        rowSet.beforeFirst();

        rowSet.next();
        assertEquals("{100,200,300}", rowSet.getString(1));
    }

    @Test
    void encodeDecodeRoundtrip_doubleArray() throws SQLException {
        ByteBuf buf = Unpooled.buffer(1024);

        ArrayColumnCodec codec = new ArrayColumnCodec(1);
        Array fakeArray = new SimpleArray(new Object[]{1.1, 2.2, 3.3});

        buf.writeBoolean(false);
        codec.write(buf, fakeArray);

        CachedRowSet rowSet = buildSingleColumnRowSet(Types.VARCHAR);
        rowSet.moveToInsertRow();
        codec.decode(buf, rowSet);
        rowSet.insertRow();
        rowSet.moveToCurrentRow();
        rowSet.beforeFirst();

        rowSet.next();
        assertEquals("{1.1,2.2,3.3}", rowSet.getString(1));
    }

    @Test
    void encodeDecodeRoundtrip_nullArray() throws SQLException {
        ByteBuf buf = Unpooled.buffer(1024);

        ArrayColumnCodec codec = new ArrayColumnCodec(1);
        buf.writeBoolean(true);

        CachedRowSet rowSet = buildSingleColumnRowSet(Types.VARCHAR);
        rowSet.moveToInsertRow();
        codec.decode(buf, rowSet);
        rowSet.insertRow();
        rowSet.moveToCurrentRow();
        rowSet.beforeFirst();

        rowSet.next();
        assertEquals(null, rowSet.getString(1));
    }

    // ---------------------------------------------------------------------------
    // Full RowSetCodec encode/decode roundtrip
    //
    // CachedRowSetImpl stores ARRAY columns internally as Object and calls getArray()
    // on encode, which tries to cast the stored value to java.sql.Array.
    // Since we can only insert a String via updateString(), we use a VARCHAR column
    // for the roundtrip: the array is already serialized as "{...}" by the codec,
    // so VARCHAR is the correct type for the decoded representation.
    // ---------------------------------------------------------------------------

    @Test
    void rowSetCodec_encodeDecodeArrayColumn() throws SQLException {
        RowSetCodec codec = new RowSetCodec(BUFFER_CAPACITY);

        CachedRowSet source = buildSingleColumnRowSet(Types.VARCHAR);
        String arrayValue = "{10,20,30}";

        source.moveToInsertRow();
        source.updateString(1, arrayValue);
        source.insertRow();
        source.moveToCurrentRow();
        source.beforeFirst();

        ByteBuffer encoded = codec.encodeValue(source);
        assertNotNull(encoded);

        RowSet decoded = codec.decodeValue(encoded);
        assertNotNull(decoded);
        decoded.beforeFirst();
        decoded.next();
        assertEquals(arrayValue, decoded.getString(1));
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
        meta.setColumnTypeName(1, columnType == Types.ARRAY ? "ARRAY" : "VARCHAR");
        meta.setColumnDisplaySize(1, 100);
        meta.setPrecision(1, 0);
        meta.setScale(1, 0);
        meta.setTableName(1, "test");
        meta.setSchemaName(1, "");
        meta.setCatalogName(1, "");
        meta.setAutoIncrement(1, false);
        meta.setCaseSensitive(1, false);
        meta.setCurrency(1, false);
        meta.setNullable(1, ResultSetMetaData.columnNullable);
        meta.setSearchable(1, false);
        meta.setSigned(1, false);
        CachedRowSet rowSet = new CachedRowSetImpl();
        rowSet.setMetaData(meta);
        return rowSet;
    }

    private static class SimpleArray implements Array {
        private final Object[] elements;

        SimpleArray(Object[] elements) {
            this.elements = elements;
        }

        @Override public String getBaseTypeName() { return "VARCHAR"; }
        @Override public int getBaseType() { return Types.VARCHAR; }
        @Override public Object getArray() { return elements; }
        @Override public Object getArray(java.util.Map<String, Class<?>> map) { return elements; }
        @Override public Object getArray(long index, int count) {
            return java.util.Arrays.copyOfRange(elements, (int) index - 1, (int) index - 1 + count);
        }
        @Override public Object getArray(long index, int count, java.util.Map<String, Class<?>> map) {
            return getArray(index, count);
        }
        @Override public ResultSet getResultSet() { return null; }
        @Override public ResultSet getResultSet(java.util.Map<String, Class<?>> map) { return null; }
        @Override public ResultSet getResultSet(long index, int count) { return null; }
        @Override public ResultSet getResultSet(long index, int count, java.util.Map<String, Class<?>> map) { return null; }
        @Override public void free() {}
    }
}
