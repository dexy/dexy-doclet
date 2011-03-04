package it.dexy.jsondoclet;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.Tag;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.LineNumberReader;
import java.io.StringWriter;
import java.lang.StringBuffer;
import java.util.HashMap;
import java.util.regex.Pattern;
import org.json.simple.JSONObject;

public class Doclet {
  private static int countLines(String str){
    if (str.length() == 0) {
      return 0;
    } else {
      String[] lines = str.split("\r\n|\r|\n");
      return lines.length;
    }
  }

  public static boolean start(RootDoc root) throws java.io.IOException {
    HashMap options = readOptions(root.options());
    String destdir = ".";
    String destfile = "javadoc-data.json";

    if (options.containsKey("destdir")) {
      destdir = (String)options.get("destdir");
    }

    ClassDoc[] classes = root.classes();
    JSONObject obj= new JSONObject();

    for (int i = 0; i < classes.length; i++) {
      JSONObject class_info = new JSONObject();

      FileReader fr = new FileReader(classes[i].position().file());
      LineNumberReader br = new LineNumberReader(fr);

      if (classes[i].superclass() != null) {
        class_info.put("superclass", classes[i].superclass().toString());
      }
      class_info.put("comment-text", classes[i].commentText());
      class_info.put("source-file", classes[i].position().file().toString());
      class_info.put("line-start", classes[i].position().line());
      if (i+1 < classes.length) {
        class_info.put("line-end", classes[i+1].position().line());
      }

      Tag[] tags = classes[i].tags();
      JSONObject tags_info = new JSONObject();
      for (int j = 0; j < tags.length; j++) {
        JSONObject tag_info = new JSONObject();

        tags_info.put(tags[j].toString(), tag_info);
      }
      class_info.put("tags", tags_info);

      FieldDoc fields[] = classes[i].fields();
      JSONObject fields_info = new JSONObject();
      for (int j = 0; j < fields.length; j++) {
        JSONObject field_info = new JSONObject();

        field_info.put("type", fields[j].type().toString());

        fields_info.put(fields[j].toString(), field_info);
      }
      class_info.put("fields", fields_info);

      MethodDoc methods[] = classes[i].methods();
      JSONObject methods_info = new JSONObject();
      for (int j = 0; j < methods.length; j++) {
        JSONObject method_info = new JSONObject();

        method_info.put("raw-comment-text", methods[j].getRawCommentText());
        method_info.put("comment-text", methods[j].commentText());
        method_info.put("return-type", methods[j].returnType().toString());
        // TODO source-file should always be same as for class... make assertion?
        method_info.put("source-file", methods[j].position().file().toString());

        int line_start = methods[j].position().line();
        method_info.put("line-start", line_start);

        int line_end = -1;
        if (j+1 < methods.length) {
          int next_method_comment_lines = countLines(methods[j+1].getRawCommentText());

          // 'raw' comment text doesn't include leading /** and trailing */
          if (next_method_comment_lines > 0)
            next_method_comment_lines += 2;

          int next_method_start = methods[j+1].position().line();
          line_end = next_method_start - next_method_comment_lines;
          method_info.put("line-end", line_end);
        }

        // Initialize vars to hold this method's source code.
        JSONObject lines = new JSONObject();
        StringBuffer source_code = new StringBuffer();

        while(br.getLineNumber() < line_start-1) {
          String line = br.readLine();
        }

        if (line_end == -1) {
          // read to end of file
          boolean c = true;
          while(c) {
            String line = br.readLine();
            c = (line != null);
            if (c) {
              method_info.put(br.getLineNumber(), line);
              source_code.append(line + "\n");
            }
          }
        } else {
          while(br.getLineNumber() < line_end-1) {
            String line = br.readLine();
            lines.put(br.getLineNumber(), line);
            source_code.append(line + "\n");
          }
        }
        method_info.put("lines", lines);
        method_info.put("source", source_code.toString());

        Tag[] method_tags = methods[j].tags();
        JSONObject method_tags_info = new JSONObject();
        for (int k = 0; k < method_tags.length; k++) {
          JSONObject tag_info = new JSONObject();
          tag_info.put("kind", method_tags[k].kind());
          tag_info.put("text", method_tags[k].text());
          method_tags_info.put(method_tags[k].name(), tag_info);
        }
        method_info.put("tags", method_tags_info);

        methods_info.put(methods[j].name(), method_info);
      }
      class_info.put("methods", methods_info);

      obj.put(classes[i].qualifiedName(), class_info);
      fr.close();
      br.close();
    }

    File f = new File(destdir, destfile);
    FileWriter file = new FileWriter(f);
    System.out.println("Putting generated docs in: " + f.toString());
    obj.writeJSONString(file);
    file.close();
    return true;
  }

  private static HashMap readOptions(String[][] options) {
    HashMap options_hash = new HashMap();

    for (int i = 0; i < options.length; i++) {
      String[] opt = options[i];
      if (opt[0].equals("-d")) {
        options_hash.put("destdir",  opt[1]);
      }
    }
    return options_hash;
  }

  public static int optionLength(String option) {
    if (option.equals("-d")) {
      return 2;
    } else {
      return 0;
    }
  }
}
