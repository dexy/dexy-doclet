package it.dexy.jsondoclet;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.PackageDoc;
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

  public static boolean start(RootDoc root) throws java.io.IOException {
    HashMap options = readOptions(root.options());

    String destdir = ".";
    String destfile = "javadoc-data.json";

    if (options.containsKey("destdir")) {
      destdir = (String)options.get("destdir");
    }

    JSONObject obj = new JSONObject();
    JSONObject packages_info = new JSONObject();

    ClassDoc[] classes = root.classes();
    for (int i = 0; i < classes.length; i++) {
      JSONObject package_info;
      JSONObject package_classes_info;

      String class_name = classes[i].name();
      PackageDoc package_doc = classes[i].containingPackage();
      String package_name = package_doc.name();

      if (packages_info.containsKey(package_name)) {
        package_info = (JSONObject)packages_info.get(package_name);
        package_classes_info = (JSONObject)package_info.get("classes");
      } else {
        package_info = new JSONObject();
        package_classes_info = new JSONObject();
      }

      package_info.put("comment-text", package_doc.commentText());

      package_classes_info.put(class_name, classInfo(classes[i]));
      package_info.put("classes", package_classes_info);
      packages_info.put(package_name, package_info);
    }

    obj.put("packages", packages_info);

    File f = new File(destdir, destfile);
    FileWriter file = new FileWriter(f);
    System.out.println("Putting generated docs in: " + f.toString());
    obj.writeJSONString(file);
    file.close();
    return true;
  }

  public static JSONObject classInfo(ClassDoc cls) throws java.io.IOException {
    JSONObject class_info = new JSONObject();
    FileReader fr = new FileReader(cls.position().file());
    LineNumberReader br = new LineNumberReader(fr);

    if (cls.superclass() != null) {
      class_info.put("superclass", cls.superclass().toString());
    }
    class_info.put("comment-text", cls.commentText());
    class_info.put("package", cls.containingPackage().name());
    class_info.put("qualified-name", cls.qualifiedName());
    class_info.put("source-file", cls.position().file().toString());
    class_info.put("line-start", cls.position().line());

    /// @export "class-tags"
    Tag[] tags = cls.tags();
    JSONObject tags_info = new JSONObject();
    for (int j = 0; j < tags.length; j++) {
      tags_info.put(tags[j].name(), tagInfo(tags[j]));
    }
    class_info.put("tags", tags_info);

    /// @export "class-fields"
    FieldDoc fields[] = cls.fields();
    JSONObject fields_info = new JSONObject();
    for (int j = 0; j < fields.length; j++) {
      JSONObject field_info = new JSONObject();
      field_info.put("type", fields[j].type().toString());
      field_info.put("qualified-name", fields[j].qualifiedName());
      field_info.put("comment-text", fields[j].commentText());
      fields_info.put(fields[j].name(), field_info);
    }
    class_info.put("fields", fields_info);

    /// @export "class-methods"
    MethodDoc methods[] = cls.methods();
    JSONObject methods_info = new JSONObject();
    for (int j = 0; j < methods.length; j++) {
      JSONObject method_info;
      if (j < (methods.length - 1)) {
        method_info = methodInfo(methods[j], br, methods[j+1]);
      } else {
        method_info = methodInfo(methods[j], br, -1);
      }
      methods_info.put(methods[j].name(), method_info);
    }

    class_info.put("methods", methods_info);
    fr.close();
    br.close();
    return class_info;
  }

  public static JSONObject methodInfo(MethodDoc method, LineNumberReader br, MethodDoc nextMethod) throws java.io.IOException {
    int next_method_comment_lines = countLines(nextMethod.getRawCommentText());
    // 'raw' comment text doesn't include leading /** and trailing */
    if (next_method_comment_lines > 0) {
      next_method_comment_lines += 2;
    }
    int next_method_start = nextMethod.position().line();
    int line_end = next_method_start - next_method_comment_lines;
    return methodInfo(method, br, line_end);
  }

  public static JSONObject methodInfo(MethodDoc method, LineNumberReader br, int line_end) throws java.io.IOException {
      JSONObject method_info = new JSONObject();

      method_info.put("raw-comment-text", method.getRawCommentText());
      method_info.put("comment-text", method.commentText());
      method_info.put("return-type", method.returnType().toString());
      method_info.put("qualified-name", method.qualifiedName());
      method_info.put("modifiers", method.modifiers());
      method_info.put("signature", method.signature());
      method_info.put("flat-signature", method.flatSignature());

      int line_start = method.position().line();
      method_info.put("line-start", line_start);
      if (line_end > -1) {
        method_info.put("line-end", line_end);
      }

      JSONObject lines = new JSONObject();
      StringBuffer source_code = new StringBuffer();

      // skip over any lines before starting point
      while(br.getLineNumber() < line_start-1) {
        String line = br.readLine();
      }

      if (line_end == -1) {
        // read to end of file
        boolean c = true;
        while (c) {
          String line = br.readLine();
          c = (line != null);
          if (c) {
            method_info.put(br.getLineNumber(), line);
            source_code.append(line + "\n");
          }
        }
      } else {
        // read to line_end
        while (br.getLineNumber() < line_end-1) {
          String line = br.readLine();
          lines.put(br.getLineNumber(), line);
          source_code.append(line + "\n");
        }
      }
      method_info.put("lines", lines);
      method_info.put("source", source_code.toString());

      Tag[] method_tags = method.tags();
      JSONObject method_tags_info = new JSONObject();
      for (int k = 0; k < method_tags.length; k++) {
        method_tags_info.put(method_tags[k].name(), tagInfo(method_tags[k]));
      }
      method_info.put("tags", method_tags_info);

      return method_info;
  }

  public static JSONObject tagInfo(Tag tag) {
    JSONObject tag_info = new JSONObject();
    tag_info.put("kind", tag.kind());
    tag_info.put("text", tag.text());
    return tag_info;
  }

  /**
   * Utility method to count the number of lines in a String.
   **/
  protected static int countLines(String str){
    if (str.length() == 0) {
      return 0;
    } else {
      String[] lines = str.split("\r\n|\r|\n");
      return lines.length;
    }
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
