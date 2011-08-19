package it.dexy.jsondoclet;

import com.sun.javadoc.*;
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

                /// @export "package-comment-text"
                package_info.put("comment-text", package_doc.commentText());
                package_info.put("raw-comment-text", package_doc.getRawCommentText());
                /// @end
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
        System.out.println("About to read source file " + source_file_name);
        ANTLRFileStream input = new ANTLRFileStream(source_file_name);
        JavaLexer lexer = new JavaLexer(input);
        TokenRewriteStream tokens = new TokenRewriteStream(lexer);
        JavaParser parser = new JavaParser(tokens);
        JSONObject source_code = parser.compilationUnit();
        System.out.println(((JSONObject)source_code.get("methods")).keySet());

        // Store references to classes, e.g. @see and automatically detected.
        JSONObject references = new JSONObject();

        if (cls.superclass() != null) {
            class_info.put("superclass", cls.superclass().toString());
        }
        class_info.put("package", cls.containingPackage().name());
        class_info.put("qualified-name", cls.qualifiedName());
        /// @export "class-comment-text"
        class_info.put("comment-text", cls.commentText());
        class_info.put("raw-comment-text", cls.getRawCommentText());
        /// @export "class-file-info"
        class_info.put("line-start", cls.position().line());
        class_info.put("source-file", cls.position().file().toString());
        /// @end

        String class_source_code = (String)((JSONObject)source_code.get("classes")).get(cls.qualifiedName());
        if (class_source_code == null) {
            class_source_code = (String)((JSONObject)source_code.get("classes")).get(cls.name());
        }
        if (class_source_code == null) {
            System.out.println("class source code not found under " + cls.qualifiedName() + " or " + cls.name());
        }
        class_info.put("source", class_source_code);

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
            } else if (tag_info.get("kind").equals("@code")) {
                tag_text.add("<code>"+tag_info.get("text")+"</code>");
            } else if (tag_info.get("kind").equals("@see")) {
                references.put(tag_info.get("ref"), tag_info.get("label"));
            } else {
                System.out.println("Using default option for tag type " + tag_info.get("kind"));
                tag_text.add(tag_info.get("text"));
            }
        }

        StringBuffer buffer = new StringBuffer();
        Iterator iter = tag_text.iterator();
        while (iter.hasNext()) {
            buffer.append(iter.next());
        }
        class_info.put("fulltext", buffer.toString());

        class_info.put("inline-tags", inline_tag_info);

        /// @export "class-interfaces"
        ClassDoc interfaces[] = cls.interfaces();
        JSONArray interfaces_info = new JSONArray();
        for (int j = 0; j < interfaces.length; j++) {
            interfaces_info.add(interfaces[j].qualifiedName());
        }
        class_info.put("interfaces", interfaces_info);

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

        /// @export "class-constructors"
        ConstructorDoc constructors[] = cls.constructors();
        JSONObject constructors_info = new JSONObject();
        for (int j = 0; j < constructors.length; j++) {
            // Get javadoc info.
            JSONObject constructor_info = constructorInfo(constructors[j]);
            String full_constructor_name = (String)constructor_info.get("full-constructor-name");
            String constructor_source_code = (String)((JSONObject)source_code.get("methods")).get(full_constructor_name);
            if (constructor_source_code == null) {
                System.out.println("constructor source code not found under " + full_constructor_name);
                System.out.println(constructors[j].qualifiedName() + constructors[j].signature());
                System.out.println(((JSONObject)source_code.get("methods")).keySet());
            }

            // Add our parsed source code to javadoc info.
            constructor_info.put("source", constructor_source_code);

            constructors_info.put(full_constructor_name, constructor_info);
        }

        /// @export "class-methods"
        MethodDoc methods[] = cls.methods();
        JSONObject methods_info = new JSONObject();
        for (int j = 0; j < methods.length; j++) {
            // Get javadoc info.
            JSONObject method_info = methodInfo(methods[j]);
            String full_method_name = (String)method_info.get("full-method-name");
            String method_source_code = (String)((JSONObject)source_code.get("methods")).get(full_method_name);
            if (method_source_code == null) {
                System.out.println("method source code not found under " + full_method_name);
                System.out.println(methods[j].qualifiedName() + methods[j].signature());
                System.out.println(((JSONObject)source_code.get("methods")).keySet());
            }

            // Add our parsed source code to javadoc info.
            method_info.put("source", method_source_code);

            methods_info.put(full_method_name, method_info);
        }

        class_info.put("methods", methods_info);
        class_info.put("constructors", constructors_info);
        class_info.put("references", references);
        return class_info;
    }

    public static JSONObject constructorInfo(ConstructorDoc constructor) throws java.io.IOException {
        JSONObject constructor_info = new JSONObject();
        constructor_info.put("comment-text", constructor.commentText());
        constructor_info.put("flat-signature", constructor.flatSignature());
        constructor_info.put("modifiers", constructor.modifiers());
        constructor_info.put("name", constructor.name());
        constructor_info.put("qualified-name", constructor.qualifiedName());
        constructor_info.put("raw-comment-text", constructor.getRawCommentText());
        constructor_info.put("signature", constructor.signature());

        String simpleParamList = "";
        Parameter[] params = constructor.parameters();
        for (int j = 0; j < params.length; j++) {
            Parameter p = params[j];
            simpleParamList = simpleParamList + p.type().simpleTypeName() + p.type().dimension();
            if (j < params.length - 1) {
              simpleParamList = simpleParamList + ",";
            }
        }
        // Uniquely identify this constructor within the scope of the class
        constructor_info.put("full-constructor-name", constructor.name() + "(" + simpleParamList + ")");

        return constructor_info;
    }

    public static JSONObject methodInfo(MethodDoc method) throws java.io.IOException {
        JSONObject method_info = new JSONObject();

        method_info.put("comment-text", method.commentText());
        method_info.put("flat-signature", method.flatSignature());
        method_info.put("modifiers", method.modifiers());
        method_info.put("name", method.name());
        method_info.put("qualified-name", method.qualifiedName());
        method_info.put("raw-comment-text", method.getRawCommentText());
        method_info.put("return-type", method.returnType().toString());
        method_info.put("signature", method.signature());

        // Store references to classes, e.g. @see and automatically detected.
        JSONObject references = new JSONObject();

        String simpleParamList = "";
        Parameter[] params = method.parameters();
        for (int j = 0; j < params.length; j++) {
            Parameter p = params[j];
            simpleParamList = simpleParamList + p.type().simpleTypeName() + p.type().dimension();
            if (j < params.length - 1) {
              simpleParamList = simpleParamList + ",";
            }
        }
        // Uniquely identify this method within the scope of the class
        method_info.put("full-method-name", method.name() + "(" + simpleParamList + ")");

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
            } else if (tag_info.get("kind").equals("@code")) {
                tag_text.add("<code>"+tag_info.get("text")+"</code>");
            } else if (tag_info.get("kind").equals("@see")) {
                references.put(tag_info.get("ref"), tag_info.get("label"));
            } else {
                System.out.println("Using default option for tag type " + tag_info.get("kind"));
                System.out.println(tag_info.toString());
                tag_text.add(tag_info.get("text"));
            }
        }
        StringBuffer buffer = new StringBuffer();
        Iterator iter = tag_text.iterator();
        while (iter.hasNext()) {
            buffer.append(iter.next());
        }
        method_info.put("fulltext", buffer.toString());
        method_info.put("inline-tags", inline_tag_info);
        method_info.put("references", references);

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
        } else if (tag_kind.equals("@see")) {
            SeeTag stag = (SeeTag)tag;
            tag_info.put("label", stag.label());
            tag_info.put("ref", stag.referencedClassName());
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
