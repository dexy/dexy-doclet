Documentation for the JSON doclet.

# intro

This doclet renders Javadoc data in a reusable JSON format. It also augments the information provided by Javadoc by parsing java source code using ANTLR. Eventually it may be possible to bypass the Javadoc entirely and obtain all information via a custom ANTLR parser.

The doclet generates a JSON file, and there is a custom dexy filter which does some additional processing, such as applying syntax highlighting.

# usage

A bash script can be used to call ant tasks to run the Javadoc tool with the custom doclet.

{{ d['run-ant.sh|pyg'] }}

This bash script can be run using dexy's bash filter. The resulting file will be called javadoc-data.json and this should be put through the javadocs filter to take advantage of the extra processing that happens there. Here is the .dexy file for this documentation which gives an example:

<pre>
{{ d['.dexy|ppjson|pyg'] }}
</pre>

And here is the build.xml file which defines the ant tasks used:

{{ d['build.xml|pyg'] }}

# example

To show how this is used, here is a simple Java class:

{{ d['helloworld.java|pyg'] }}

The class gives this output when run:

<pre>
{{ d['helloworld.java|java'] }}
</pre>

# raw output

Here is the raw JSON generated by the JSON doclet for this example:

{{ d['build/example/javadoc-data.json|ppjson|pyg'] }}

If you don't want to use the custom features described in the next section, you can still have dexy automatically parse this JSON and make it available in a dict by using the dexy filter.

At the top level you will see:

<pre>
{{ d['build/example/javadoc-data.json|dexy'].keys() }}
</pre>

Of course you could also parse this JSON using any tool you wish, within Dexy or not.

# using the enhanced output

The 'javadocs' filter reads the JSON generated by the doclet and places it in a dictionary. Then additional content is added to the dictionary by the post-processing. You can then access the contents of the dictionary as needed.

At the top level we have this key:

<pre>
{{ d['build/example/javadoc-data.json|javadocs'].keys() }}
</pre>

### package information

In this case we just have 1 package:

<pre>
{{ d['build/example/javadoc-data.json|javadocs']['packages'].keys() }}
</pre>

Beneath the package we have:

<pre>
{{ d['build/example/javadoc-data.json|javadocs']['packages']['hello'].keys() }}
</pre>

In this case the package comment is:

<pre>
{{ d['build/example/javadoc-data.json|javadocs']['packages']['hello']['comment-text'] }}
</pre>

And the raw comment is:

<pre>
{{ d['build/example/javadoc-data.json|javadocs']['packages']['hello']['raw-comment-text'] }}
</pre>

This comes from the package-info file:

{{ d['package-info.java|pyg'] }}

Here is the source code in Doclet.java which obtains package comments:

{{ d['src/it/dexy/jsondoclet/Doclet.java|idio']['package-comment-text'] }}

### class info

The classes under this package are:

<pre>
{{ d['build/example/javadoc-data.json|javadocs']['packages']['hello']['classes'].keys() }}
</pre>

And the following elements are available for this class:

{% for k in d['build/example/javadoc-data.json|javadocs']['packages']['hello']['classes']['helloworld'].keys() -%}
* {{ k }}
{% endfor %}

superclass:

<pre>
{{ d['build/example/javadoc-data.json|javadocs']['packages']['hello']['classes']['helloworld']['superclass'] }}
</pre>

interfaces:

<pre>
{{ d['build/example/javadoc-data.json|javadocs']['packages']['hello']['classes']['helloworld']['interfaces'] }}
</pre>

Here are constructors:

{% for c_name, c_data in d['build/example/javadoc-data.json|javadocs']['packages']['hello']['classes']['helloworld']['constructors'].iteritems() -%}
### {{ c_name }}

{{ c_data['source-html'] }}
{% endfor %}

methods:

{% for k in d['build/example/javadoc-data.json|javadocs']['packages']['hello']['classes']['helloworld']['methods'].keys() -%}
* {{ k }}
{% endfor %}



