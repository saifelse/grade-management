/* Copyright (c) 2010 Richard Chan */
package reader;

import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import exception.ReaderException;

import reader.common.KeyDataReader;

public class ConfigReader extends KeyDataReader {

    private static final String KEY = "KEY";
    private static final String VALUE = "TOTAL";

    public class ConfigRow {
        public final String key;
        public final String value;
        public ConfigRow(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
    
    /**
     * @throws ReaderException if sheet missing keys or IOException.
     */
    public ConfigReader(Reader r) throws ReaderException {
        super(r);
        
        try {
            Collection<String> keys = getKeys();
            if (!(keys.contains(KEY) &&
                  keys.contains(VALUE))) {
                throw new ReaderException("Config missing keys.", true);
            }
        } catch(IOException e) {
            throw new ReaderException(e.getMessage(), true);
        }
    }
    
    /**
     * @throws ReaderException if row missing KEY or TOTAL or IOException.
     */
    public ConfigRow getNextRow() throws ReaderException {
        try {
            Map<String, String> row = getNextDataRow();
            System.out.println("My new row is "+row);
            if (row == null) return null;
            if (row.containsKey(KEY) && row.containsKey(VALUE)) {
                return new ConfigRow(row.get(KEY).toUpperCase(), row.get(VALUE).toUpperCase());
            }
            System.out.println(row.containsKey(KEY) );
            System.out.println(row.containsKey(VALUE));
            throw new ReaderException("Config missing keys.", false);
        } catch(IOException e) {
            throw new ReaderException(e.getMessage(), true);
        }
    }
    
}
