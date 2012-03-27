package it.dexy.doclet;

import com.almworks.sqlite4java.SQLiteBackup;
import com.almworks.sqlite4java.SQLiteConnection;
import com.almworks.sqlite4java.SQLiteStatement;
import java.io.File;
import java.io.FileWriter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class KeyValueStorage {
    private JSONObject json_object;
    private SQLiteConnection sqlite_db;
    private SQLiteStatement insert_statement;
    private File storage_file;
    public boolean isJson;
    public boolean isSqlite3;

    public KeyValueStorage(String destdir, String destfile) throws com.almworks.sqlite4java.SQLiteException, RuntimeException {
        storage_file = new File(destdir, destfile);
        isJson = storage_file.getName().contains(".json");
        isSqlite3 = storage_file.getName().contains(".sqlite3");
        setup();
    }

    public void setup() throws com.almworks.sqlite4java.SQLiteException, RuntimeException {
        if (isJson) {
            json_object = new JSONObject();

        } else if (isSqlite3) {
            sqlite_db = new SQLiteConnection(storage_file);
            sqlite_db.open(true);
            sqlite_db.exec("CREATE TABLE kvstore (key TEXT, value TEXT)");
            sqlite_db.exec("BEGIN");
            insert_statement = sqlite_db.prepare("INSERT INTO kvstore VALUES (?, ?)");

        } else {
            throw new RuntimeException("Unknown storage type " + storage_file.getName());

        }
    }

    public void append(String key, String value) throws com.almworks.sqlite4java.SQLiteException, RuntimeException {
        if (isJson) {
            json_object.put(key, value);

        } else if (isSqlite3) {
            insert_statement.reset();
            insert_statement.bind(1, key);
            insert_statement.bind(2, value);
            insert_statement.step();

        } else {
            throw new RuntimeException("Unknown storage type " + storage_file.getName());

        }
    }

    public void persist() throws com.almworks.sqlite4java.SQLiteException, java.io.IOException, RuntimeException {
        if (isJson) {
            FileWriter file = new FileWriter(storage_file);
            json_object.writeJSONString(file);
            file.close();

        } else if (isSqlite3) {
            sqlite_db.exec("COMMIT");
            insert_statement.dispose();
            sqlite_db.dispose();
            System.out.println("Completed.");

        } else {
            throw new RuntimeException("Unknown storage type " + storage_file.getName());

        }
    }
}
