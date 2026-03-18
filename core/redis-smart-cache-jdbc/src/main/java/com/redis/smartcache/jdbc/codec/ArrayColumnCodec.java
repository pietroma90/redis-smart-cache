package com.redis.smartcache.jdbc.codec;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.redis.smartcache.jdbc.RowSetCodec;

import io.netty.buffer.ByteBuf;

/**
 * Codec for SQL ARRAY columns (Types.ARRAY).
 *
 * Arrays are serialized as a PostgreSQL-style string: {val1,val2,val3}
 * On decode the value is stored back as a String via updateString().
 * This avoids the need for a live Connection (required to create java.sql.Array objects)
 * while preserving the full data for application-level parsing.
 */
public class ArrayColumnCodec extends NullableColumnCodec<Array> {

    public ArrayColumnCodec(int columnIndex) {
        super(columnIndex);
    }

    @Override
    protected Array getValue(ResultSet resultSet) throws SQLException {
        return resultSet.getArray(columnIndex);
    }

    @Override
    protected void write(ByteBuf byteBuf, Array value) throws SQLException {
        Object baseArray = value.getArray();
        String serialized = toPostgresArrayString(baseArray);
        RowSetCodec.writeString(byteBuf, serialized);
    }

    @Override
    protected void updateValue(ByteBuf byteBuf, ResultSet resultSet) throws SQLException {
        String serialized = RowSetCodec.readString(byteBuf);
        // Store as String: creating a real java.sql.Array requires a live Connection
        // which is not available inside the codec.
        resultSet.updateString(columnIndex, serialized);
    }

    /**
     * Converts a Java array (Object[]) or primitive array to PostgreSQL-style notation.
     * Examples:
     *   ["a","b","c"]   -> {a,b,c}
     *   [1, 2, 3]        -> {1,2,3}
     *   null elements    -> {1,NULL,3}
     */
    public static String toPostgresArrayString(Object baseArray) {
        if (baseArray == null) {
            return "{}";
        }
        if (baseArray instanceof Object[]) {
            Object[] arr = (Object[]) baseArray;
            if (arr.length == 0) {
                return "{}";
            }
            StringBuilder sb = new StringBuilder("{");
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) sb.append(',');
                if (arr[i] == null) {
                    sb.append("NULL");
                } else {
                    sb.append(arr[i].toString());
                }
            }
            sb.append('}');
            return sb.toString();
        }
        // Fallback for primitive arrays via toString
        return baseArray.toString();
    }
}
