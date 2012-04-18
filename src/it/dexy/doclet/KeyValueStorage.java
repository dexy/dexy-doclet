package it.dexy.doclet;

import java.sql.*;
import java.io.File;
import java.io.FileWriter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class KeyValueStorage {
    private File storage_file;
    public boolean isJson = false;
    public boolean isSqlite3 = false;

    // JSON
    private JSONObject json_object;

    // SQLite
    private Connection sqlite_db;
    private PreparedStatement insert_statement;

    public KeyValueStorage(String destdir, String destfile) throws SQLException, RuntimeException, java.lang.ClassNotFoundException {
        storage_file = new File(destdir, destfile);

        if (storage_file.getName().contains(".json")) {
            isJson = true;
            setup_json();
        } else if (storage_file.getName().contains(".sqlite3")) {
            isSqlite3 = true;
            setup_sqlite();
        } else {
            throw new RuntimeException("Unknown storage type " + storage_file.getName());
        }
    }

    public void setup_json() {
        json_object = new JSONObject();
    }

    public void setup_sqlite() throws SQLException, java.lang.ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
        sqlite_db = DriverManager.getConnection("jdbc:sqlite:" + storage_file);
        Statement statement = sqlite_db.createStatement();
        statement.executeUpdate("CREATE TABLE kvstore (key TEXT, value TEXT)");
        insert_statement = sqlite_db.prepareStatement("INSERT INTO kvstore VALUES (?, ?)");
    }

    public void append(String key, String value) throws SQLException, RuntimeException {
        if (isJson) {
            json_object.put(key, value);

        } else if (isSqlite3) {
            insert_statement.setString(1, key);
            insert_statement.setString(2, value);
            insert_statement.addBatch();

        } else {
            throw new RuntimeException("Unknown storage type " + storage_file.getName());

        }
    }

    public void persist() throws SQLException, java.io.IOException, RuntimeException {
        if (isJson) {
            FileWriter file = new FileWriter(storage_file);
            json_object.writeJSONString(file);
            file.close();
        } else if (isSqlite3) {
            sqlite_db.setAutoCommit(false);
            insert_statement.executeBatch();
            sqlite_db.setAutoCommit(true);
            sqlite_db.close();
        } else {
            throw new RuntimeException("Unknown storage type " + storage_file.getName());
        }
    }
}
