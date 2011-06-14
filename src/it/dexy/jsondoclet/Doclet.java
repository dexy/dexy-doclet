package it.dexy.jsondoclet;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.PackageDoc;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.Tag;
import com.sun.javadoc.SourcePosition;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.ParameterizedType;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.LineNumberReader;
import java.io.StringWriter;
import java.lang.StringBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Pattern;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;

import latexlet.LaTeXlet;
import latexlet.InlineBlockLaTeXlet;
import latexlet.InlineLaTeXlet;
import latexlet.BlockLaTeXlet;

import it.dexy.jsondoclet.JavaLexer;
import it.dexy.jsondoclet.JavaParser;
import org.antlr.runtime.*;

public class Doclet {

    public static boolean start(RootDoc root) throws java.io.IOException, RecognitionException {
        // Get options from option parser.
        HashMap options = readOptions(root.options());

        String destdir = ".";
        String destfile = "javadoc-data.json";

        if (options.containsKey("destdir")) {
            destdir = (String)options.get("destdir");
        }

        JSONObject packages_info = new JSONObject();
        JSONObject package_info;
        JSONObject package_classes_info;

        // There doesn't seem to be a way to get list of all packages directly
        // from RootDot, so we loop over classes and thereby get package info.
        // Would be more intuitive to get list of packages then loop over classes
        // in that package.

        ClassDoc[] classes = root.classes();

        for (int i = 0; i < classes.length; i++) {
            PackageDoc package_doc = classes[i].containingPackage();
            String class_name = classes[i].name();
            String package_name = package_doc.name();


            // Get the existing package_info or create a new one if this is the
            // first time we've seen this package.
            if (packages_info.containsKey(package_name)) {
                // It already exists...
                package_info = (JSONObject)packages_info.get(package_name);
                package_classes_info = (JSONObject)package_info.get("classes");
            } else {
                // It doesn't exist yet, initialize and populate...
                package_info = new JSONObject();
                package_classes_info = new JSONObject();

                // Package-level info
                package_info.put("comment-text", package_doc.commentText());
                package_info.put("raw-comment-text", package_doc.commentText());
            }

            // Get info for this class, put it in the list of classes for this
            // package.
            JSONObject class_info = classInfo(classes[i]);
            package_classes_info.put(class_name, class_info);

            // Now put the list of classes under 'classes' label in package
            // info.
            package_info.put("classes", package_classes_info);

            // And finally update the package information with what we've
            // extracted here.
            packages_info.put(package_name, package_info);
        }

        File f = new File(destdir, destfile);
        FileWriter file = new FileWriter(f);
        System.out.println("Putting generated docs in: " + f.toString());

        JSONObject obj = new JSONObject();
        obj.put("packages", packages_info);
        obj.writeJSONString(file);

        file.close();

        return true;
    }

    public static JSONObject classInfo(ClassDoc cls) throws java.io.IOException, RecognitionException {
        JSONObject class_info = new JSONObject();

        String source_file_name = cls.position().file().toString();
        ANTLRFileStream input = new ANTLRFileStream(source_file_name);
        JavaLexer lexer = new JavaLexer(input);
        TokenRewriteStream tokens = new TokenRewriteStream(lexer);
        JavaParser parser = new JavaParser(tokens);
        JSONObject source_code = parser.compilationUnit();

        if (cls.superclass() != null) {
            class_info.put("superclass", cls.superclass().toString());
        }
        class_info.put("comment-text", cls.commentText());
        class_info.put("package", cls.containingPackage().name());
        class_info.put("qualified-name", cls.qualifiedName());
        class_info.put("source-file", cls.position().file().toString());
        class_info.put("line-start", cls.position().line());
        class_info.put("source", source_code.toString());

        /// @export "class-tags"
        Tag[] tags = cls.tags();
        JSONObject tags_info = new JSONObject();
        for (int j = 0; j < tags.length; j++) {
            tags_info.put(tags[j].name(), tagInfo(tags[j]));
        }
        class_info.put("tags", tags_info);

        /// @export "class-inline-tags"
        Tag[] inline_tags = cls.inlineTags();
        JSONArray tag_text = new JSONArray();
        JSONObject inline_tag_info = new JSONObject();
        for (int j = 0; j < inline_tags.length; j++) {
            JSONObject tag_info = tagInfo(inline_tags[j]);
            tags_info.put(inline_tags[j].name(), tag_info);

            if (tag_info.get("kind").equals("Text")) {
                tag_text.add(tag_info.get("text"));
            } else if (tag_info.get("kind").equals("@latex.ilb")) {
                tag_text.add(tag_info.get("latex"));
            } else if (tag_info.get("kind").equals("@latex.inline")) {
                tag_text.add(tag_info.get("latex"));
            }
        }

        StringBuffer buffer = new StringBuffer();
        Iterator iter = tag_text.iterator();
        while (iter.hasNext()) {
            buffer.append(iter.next());
        }
        class_info.put("fulltext", buffer.toString());

        class_info.put("inline-tags", inline_tag_info);

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
            // Get javadoc info.
            JSONObject method_info = methodInfo(methods[j]);
            String full_method_name = (String)method_info.get("full-method-name");
            System.out.println("Full method name: " + full_method_name);

            String method_source_code = (String)((JSONObject)source_code.get("methods")).get(full_method_name);

            // Add our parsed source code to javadoc info.
            method_info.put("source", method_source_code);

            methods_info.put(full_method_name, method_info);
        }

        class_info.put("methods", methods_info);
        return class_info;
    }

    public static JSONObject methodInfo(MethodDoc method) throws java.io.IOException {
        JSONObject method_info = new JSONObject();

        method_info.put("raw-comment-text", method.getRawCommentText());
        method_info.put("comment-text", method.commentText());
        method_info.put("return-type", method.returnType().toString());
        method_info.put("qualified-name", method.qualifiedName());
        method_info.put("name", method.name());
        method_info.put("modifiers", method.modifiers());
        method_info.put("signature", method.signature());
        method_info.put("flat-signature", method.flatSignature());

        // Uniquely identify this method within the scope of the class
        method_info.put("full-method-name", method.name() + method.signature());

        Parameter[] params = method.parameters();
        for (int j = 0; j < params.length; j++) {
            Parameter p = params[j];
            System.out.println("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
            System.out.println("type: " + p.type());
            System.out.println("type.string: " + p.type().toString());
            System.out.println("type.simple: " + p.type().simpleTypeName());
            System.out.println("type.qualified: " + p.type().qualifiedTypeName());
            System.out.println("--------------------------------------------------");
            System.out.println("typename: " + p.typeName());
            System.out.println("name: " + p.name());
            System.out.println("string: " + p.toString());
        }

        Tag[] method_tags = method.tags();
        JSONObject method_tags_info = new JSONObject();
        for (int k = 0; k < method_tags.length; k++) {
            JSONObject tag_info = tagInfo(method_tags[k]);
            method_tags_info.put(method_tags[k].name(), tag_info);

        }
        method_info.put("tags", method_tags_info);

        /// @export "method-inline-tags"
        Tag[] inline_tags = method.inlineTags();
        JSONArray tag_text = new JSONArray();
        JSONObject inline_tag_info = new JSONObject();
        for (int j = 0; j < inline_tags.length; j++) {
            JSONObject tag_info = tagInfo(inline_tags[j]);
            inline_tag_info.put(inline_tags[j].name(), tag_info);

            if (tag_info.get("kind").equals("Text")) {
                tag_text.add(tag_info.get("text"));
            } else if (tag_info.get("kind").equals("@latex.ilb")) {
                tag_text.add(tag_info.get("latex"));
            } else if (tag_info.get("kind").equals("@latex.inline")) {
                tag_text.add(tag_info.get("latex"));
            }
        }
        StringBuffer buffer = new StringBuffer();
        Iterator iter = tag_text.iterator();
        while (iter.hasNext()) {
            buffer.append(iter.next());
        }
        method_info.put("fulltext", buffer.toString());
        method_info.put("inline-tags", inline_tag_info);

        return method_info;
    }

    public static JSONObject tagInfo(Tag tag) {
        JSONObject tag_info = new JSONObject();
        String tag_kind = tag.kind().toString();

        tag_info.put("kind", tag_kind);
        tag_info.put("text", tag.text());
        tag_info.put("string", tag.toString());

        // Special handling for custom taglets
        if (tag_kind.equals("@latex.ilb")) {
            LaTeXlet ltag = new InlineBlockLaTeXlet();
            String[] tag_out = ltag.extractLaTeXAndPreambleAndResolutionFrom(tag);
            tag_info.put("latex", tag_out[1]);
        } else if (tag_kind.equals("@latex.inline")) {
            LaTeXlet ltag = new InlineLaTeXlet();
            String[] tag_out = ltag.extractLaTeXAndPreambleAndResolutionFrom(tag);
            tag_info.put("latex", tag_out[1]);
        } else if (tag_kind.equals("@latex.block")) {
            LaTeXlet ltag = new BlockLaTeXlet();
            String[] tag_out = ltag.extractLaTeXAndPreambleAndResolutionFrom(tag);
            tag_info.put("latex", tag_out[1]);
        }

        return tag_info;
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
