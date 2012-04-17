package it.dexy.doclet;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

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

import it.dexy.doclet.JavaLexer;
import it.dexy.doclet.JavaParser;
import org.antlr.runtime.*;

public class DexyDoclet {
    public static boolean start(RootDoc root) throws java.io.IOException, RecognitionException, com.almworks.sqlite4java.SQLiteException {
        // Get options from option parser.
        HashMap options = readOptions(root.options());
        String sourcepath = (String)options.get("sourcepath");

        String destdir = ".";
        String destfile = "javadoc-data.json";

        // We use the ant javadoc task to call this, which supports a 'destdir'
        // option but not 'destfile', so if 'destdir' has a file extension
        // assume that includes dest file.
        if (options.containsKey("destdir")) {
            String specified_destdir = (String)options.get("destdir");
            System.out.println("specified " + specified_destdir + "as destination");
            if (specified_destdir.contains(".")) {
                File destdir_file = new File(specified_destdir);
                destdir = destdir_file.getParent().toString();
                destfile = specified_destdir.replace(destdir, "");
                System.out.println("Will save content in " + destfile + " in " + destdir);
            } else {
                destdir = specified_destdir;
            }
        }

        KeyValueStorage storage = new KeyValueStorage(destdir, destfile);

        ClassDoc[] classes = root.classes();
        for (int i = 0; i < classes.length; i++) {
            PackageDoc package_doc = classes[i].containingPackage();
            String class_name = classes[i].name();
            String package_name = package_doc.name();

            storage.append(package_name + ":comment-text", package_doc.commentText());
            storage.append(package_name + ":raw-comment-text", package_doc.getRawCommentText());

            classInfo(sourcepath, classes[i], storage);
        }

        storage.persist();
        return true;
    }

    public static void classInfo(String sourcepath, ClassDoc cls, KeyValueStorage storage) throws java.io.IOException, RecognitionException, com.almworks.sqlite4java.SQLiteException {
        String source_file_name = cls.position().file().toString();

        if (source_file_name.indexOf("/") < 0) {
            source_file_name = sourcepath + "/" + cls.containingPackage().name().replace(".", "/") + "/" + cls.position().file().toString();
        }

        ANTLRFileStream input = new ANTLRFileStream(source_file_name);
        JavaLexer lexer = new JavaLexer(input);
        TokenRewriteStream tokens = new TokenRewriteStream(lexer);
        JavaParser parser = new JavaParser(tokens);
        JSONObject source_code = parser.compilationUnit();

        System.out.println(((JSONObject)source_code.get("methods")).keySet());

        String class_source_code = (String)((JSONObject)source_code.get("classes")).get(cls.qualifiedName());
        if (class_source_code == null) {
            class_source_code = (String)((JSONObject)source_code.get("classes")).get(cls.name());
        }
        if (class_source_code == null) {
            System.out.println("class source code not found under " + cls.qualifiedName() + " or " + cls.name());
        }
        storage.append(cls.qualifiedName() + "::source", class_source_code);

        /// @export "class-constructors"
        ConstructorDoc constructors[] = cls.constructors();
        for (int j = 0; j < constructors.length; j++) {
            // Get javadoc info.
            constructorInfo(cls.containingPackage().name(), storage, source_code, constructors[j]);

        }

        /// @export "class-methods"
        MethodDoc methods[] = cls.methods();
        JSONObject methods_info = new JSONObject();
        for (int j = 0; j < methods.length; j++) {
            methodInfo(cls.qualifiedName(), storage, source_code, methods[j]);
        }


        if (cls.superclass() != null) {
            storage.append(cls.qualifiedName() + "::superclass", cls.superclass().toString());
        }

        storage.append(cls.qualifiedName() + "::package", cls.containingPackage().name());
        storage.append(cls.qualifiedName() + "::qualified-name", cls.qualifiedName());
        storage.append(cls.qualifiedName() + "::comment-text", cls.commentText());
        storage.append(cls.qualifiedName() + "::raw-comment-text", cls.getRawCommentText());
        storage.append(cls.qualifiedName() + "::line-start", "" + cls.position().line());
        storage.append(cls.qualifiedName() + "::source-file", "" + cls.position().file().toString());


        /// @export "class-tags"
        Tag[] tags = cls.tags();
        String tags_info = "";
        for (int j = 0; j < tags.length; j++) {
            tags_info = tags_info + "," + tags[j];
        }
        storage.append(cls.qualifiedName() + ":tags", tags_info);

        /// @export "class-inline-tags"
        //        Tag[] inline_tags = cls.inlineTags();
        //        JSONArray tag_text = new JSONArray();
        //        JSONObject inline_tag_info = new JSONObject();
        //        for (int j = 0; j < inline_tags.length; j++) {
        //            JSONObject tag_info = tagInfo(inline_tags[j]);
        //            tags_info.put(inline_tags[j].name(), tag_info);
        //
        //            if (tag_info.get("kind").equals("Text")) {
        //                tag_text.add(tag_info.get("text"));
        //            } else if (tag_info.get("kind").equals("@latex.ilb")) {
        //                tag_text.add(tag_info.get("latex"));
        //            } else if (tag_info.get("kind").equals("@latex.inline")) {
        //                tag_text.add(tag_info.get("latex"));
        //            } else if (tag_info.get("kind").equals("@code")) {
        //                tag_text.add("<code>"+tag_info.get("text")+"</code>");
        //            } else if (tag_info.get("kind").equals("@see")) {
        //                references.put(tag_info.get("ref"), tag_info.get("label"));
        //            } else {
        //                System.out.println("Using default option for tag type " + tag_info.get("kind"));
        //                tag_text.add(tag_info.get("text"));
        //            }
        //        }
        //
        //        StringBuffer buffer = new StringBuffer();
        //        Iterator iter = tag_text.iterator();
        //        while (iter.hasNext()) {
        //            buffer.append(iter.next());
        //        }
        //        storage.append(cls.qualifiedName() + ":fulltext", buffer.toString());
        //        storage.append(cls.qualifiedName() + ":inline-tags", inline_tag_info.toString());

        /// @export "class-interfaces"
        ClassDoc interfaces[] = cls.interfaces();
        JSONArray interfaces_info = new JSONArray();
        for (int j = 0; j < interfaces.length; j++) {
            interfaces_info.add(interfaces[j].qualifiedName());
        }
        storage.append(cls.qualifiedName() + ":interfaces", interfaces_info.toString());

        /// @export "class-fields"
        FieldDoc fields[] = cls.fields();
        for (int j = 0; j < fields.length; j++) {
            storage.append(cls.qualifiedName() + ":" + fields[j].name() + ":type", fields[j].type().toString());
            storage.append(cls.qualifiedName() + ":" + fields[j].name() + ":comment-text", fields[j].commentText());
        }

    }

    // This returns a simpler format than signature() which returns fully
    // qualified class names.
    public static String simpleParamList(ExecutableMemberDoc member) {
        String simpleParamList = "";
        Parameter[] params = member.parameters();
        for (int j = 0; j < params.length; j++) {
            Parameter p = params[j];
            simpleParamList = simpleParamList + p.type().simpleTypeName() + p.type().dimension();
            if (j < params.length - 1) {
                simpleParamList = simpleParamList + ",";
            }
        }
        return simpleParamList;
    }

    public static void constructorInfo(String package_name, KeyValueStorage storage, JSONObject source_code, ConstructorDoc constructor) throws java.io.IOException, com.almworks.sqlite4java.SQLiteException {
        String full_constructor_name = constructor.name() + "(" + simpleParamList(constructor) + ")";
        String constructor_source_code = (String)((JSONObject)source_code.get("methods")).get(full_constructor_name);

        if (constructor_source_code == null) {
            System.out.println("==================================================");
            System.out.println("constructor source code not found under " + full_constructor_name);
            System.out.println(((JSONObject)source_code.get("methods")).keySet());
            System.out.println("==================================================");
        } else {
            storage.append(package_name + ":" + full_constructor_name + ":source", constructor_source_code);
        }

        storage.append(package_name + ":" + full_constructor_name + ":comment-text", constructor.commentText());
        storage.append(package_name + ":" + full_constructor_name + ":flat-signature", constructor.flatSignature());
        storage.append(package_name + ":" + full_constructor_name + ":modifiers", constructor.modifiers());
        storage.append(package_name + ":" + full_constructor_name + ":name", constructor.name());
        storage.append(package_name + ":" + full_constructor_name + ":qualified-name", constructor.qualifiedName());
        storage.append(package_name + ":" + full_constructor_name + ":raw-comment-text", constructor.getRawCommentText());
        storage.append(package_name + ":" + full_constructor_name + ":signature", constructor.signature());
    }

    public static void methodInfo(String class_name, KeyValueStorage storage, JSONObject source_code, MethodDoc method) throws java.io.IOException, com.almworks.sqlite4java.SQLiteException {
        String full_method_name = method.name() + "(" + simpleParamList(method) + ")";

        String method_source_code = (String)((JSONObject)source_code.get("methods")).get(full_method_name);
        if (method_source_code == null) {
            System.out.println("==================================================");
            System.out.println("method source code not found under " + full_method_name);
            System.out.println(((JSONObject)source_code.get("methods")).keySet());
            System.out.println("==================================================");
        } else {
            storage.append(class_name + ":" + full_method_name + ":source", method_source_code);
        }

        storage.append(class_name + ":" + full_method_name + ":comment-text", method.commentText());
        storage.append(class_name + ":" + full_method_name + ":flat-signature", method.flatSignature());
        storage.append(class_name + ":" + full_method_name + ":modifiers", method.modifiers());
        storage.append(class_name + ":" + full_method_name + ":name", method.name());
        storage.append(class_name + ":" + full_method_name + ":qualified-name", method.qualifiedName());
        storage.append(class_name + ":" + full_method_name + ":raw-comment-text", method.getRawCommentText());
        storage.append(class_name + ":" + full_method_name + ":signature", method.signature());
        storage.append(class_name + ":" + full_method_name + ":return-type", method.returnType().toString());
        storage.append(class_name + ":" + full_method_name + ":class-name", method.containingClass().qualifiedName());
        storage.append(class_name + ":" + full_method_name + ":package-name", method.containingPackage().name());


        Tag[] method_tags = method.tags();
        JSONObject method_tags_info = new JSONObject();
        for (int k = 0; k < method_tags.length; k++) {
            JSONObject tag_info = tagInfo(method_tags[k]);
            method_tags_info.put(method_tags[k].name(), tag_info);

        }
        storage.append(class_name + ":" + full_method_name + ":tags", method_tags_info.toString());

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
                //references.put(tag_info.get("ref"), tag_info.get("label"));
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
        storage.append(class_name + ":" + full_method_name + ":fulltext", buffer.toString());
        storage.append(class_name + ":" + full_method_name + ":inline-tags", inline_tag_info.toString());
    }

    public static JSONObject tagInfo(Tag tag) {
        JSONObject tag_info = new JSONObject();
        String tag_kind = tag.kind().toString();

        tag_info.put("kind", tag_kind);
        tag_info.put("text", tag.text());
        tag_info.put("string", tag.toString());

        if (tag_kind.equals("@see")) {
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
            if (opt[0].equals("-sourcepath")) {
                System.out.println("found sourcepath " + opt[1]);
                options_hash.put("sourcepath",  opt[1]);
            }
        }
        return options_hash;
    }

    public static int optionLength(String option) {
        if (option.equals("-d")) {
            return 2;
        } else if (option.equals("-sourcepath")) {
            return 2;
        } else {
            return 0;
        }
    }

}
