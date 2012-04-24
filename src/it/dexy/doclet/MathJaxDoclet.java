package it.dexy.doclet;

import org.json.simple.JSONObject;
import com.sun.javadoc.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import org.antlr.runtime.*;

/* 
 * A doclet intended for use on a single .java source file which displays
 * comments + source code and includes MathJax stylesheet.
 */
public class MathJaxDoclet {
    public static boolean start(RootDoc root) throws java.io.IOException, RecognitionException {
        HashMap options = readOptions(root.options());
        String sourcepath = (String)options.get("sourcepath");

        String destdir = ".";
        String destfile = "index.html";

        if (options.containsKey("destdir")) {
            destdir = (String)options.get("destdir");
        }

        FileWriter file_writer = new FileWriter(new File(destdir, destfile));
        BufferedWriter out = new BufferedWriter(file_writer);

        out.write("<html>\n");
        out.write("<head>\n");
        out.write("<script type=\"text/x-mathjax-config\">\n");
        out.write("MathJax.Hub.Config({ tex2jax: { inlineMath: [ ['$','$'], [\"\\\\(\",\"\\\\)\"] ],processEscapes: true} });");
        out.write("</script>\n");
        out.write("<script type=\"text/javascript\" src=\"http://cdn.mathjax.org/mathjax/latest/MathJax.js?config=default\">");
        out.write("</script>\n");
        out.write("<script type=\"text/javascript\" src=\"prettify.js\"></script>\n");
        out.write("<link href=\"prettify.css\" type=\"text/css\" rel=\"stylesheet\" />\n");
        out.write("</head>\n");
        out.write("<body onload=\"prettyPrint()\">\n");

        ClassDoc[] classes = root.classes();
        for (int i = 0; i < classes.length; i++) {
            String class_name = classes[i].name();
            out.write("<h2 id=\"" + class_name + "\">" + class_name + "</h2>\n");
            out.write(classes[i].commentText());
            out.newLine();

            // Parse source code
            System.out.println("Using source path: " + sourcepath);
            ANTLRFileStream input = new ANTLRFileStream(sourcepath);
            JavaLexer lexer = new JavaLexer(input);
            TokenRewriteStream tokens = new TokenRewriteStream(lexer);
            JavaParser parser = new JavaParser(tokens);
            JSONObject source_code = parser.compilationUnit();


            MethodDoc methods[] = classes[i].methods();
            for (int j = 0; j < methods.length; j++) {
                MethodDoc method = methods[j];
                String full_method_name = method.name() + "(" + simpleParamList(method) + ")";
                String method_source_code = (String)((JSONObject)source_code.get("methods")).get(full_method_name);

                out.write("<h4>" + method.name() + "</h4>");
                out.newLine();
                out.write(method.commentText());
                out.newLine();
                out.write("<pre class=\"prettyprint\">\n" + method_source_code + "</pre>\n\n");
            }
        }

        out.write("</body>\n");
        out.write("</html>\n");

        out.close();
        file_writer.close();

        return true;
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

    private static HashMap readOptions(String[][] options) {
        HashMap options_hash = new HashMap();

        for (int i = 0; i < options.length; i++) {
            String[] opt = options[i];
            System.out.println("Processing option " + i + ":" + opt[0]);
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
        System.out.println("Determining option length for " + option);
        if (option.equals("-d")) {
            return 2;
        } else if (option.equals("-sourcepath")) {
            return 2;
        } else {
            return 0;
        }
    }
}
