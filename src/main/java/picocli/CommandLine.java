/*
   Copyright 2017 Remko Popma

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package picocli;

import java.awt.*;
import java.io.File;
import java.io.PrintStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.sql.Time;
import java.text.BreakIterator;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

import static java.util.Locale.ENGLISH;
import static picocli.CommandLine.Help.Ansi.*;
import static picocli.CommandLine.Help.Column.Overflow.*;

/**
 * <p>
 * CommandLine interpreter that uses reflection to initialize an annotated domain object with values obtained from the
 * command line arguments.
 * </p><h2>Example</h2>
 * <pre>import static picocli.CommandLine.*;
 *
 * &#064;Command(header = "Encrypt FILE(s), or standard input, to standard output or to the output file.",
 *          showDefaultValues = false)
 * public class Encrypt {
 *
 *     &#064;Parameters(type = File.class, description = "Any number of input files")
 *     private List&lt;File&gt; files = new ArrayList&lt;File&gt;();
 *
 *     &#064;Option(names = { "-o", "--out" }, description = "Output file (default: print to console)")
 *     private File outputFile;
 *
 *     &#064;Option(names = { "-v", "--verbose"}, description = "Verbosely list files processed")
 *     private boolean verbose;
 *
 *     &#064;Option(names = { "-h", "--help", "-?", "-help"}, help = true, description = "Display this help and exit")
 *     private boolean help;
 * }
 * </pre>
 * <p>
 * Use {@code CommandLine} to initialize a domain object as follows:
 * </p><pre>
 * public static void main(String... args) {
 *     try {
 *         Encrypt encrypt = CommandLine.parse(new Encrypt(), args);
 *         if (encrypt.help) {
 *             CommandLine.usage(Encrypt.class, System.out);
 *         } else {
 *             runProgram(encrypt);
 *         }
 *     } catch (ParameterException ex) { // command line arguments could not be parsed
 *         System.err.println(ex.getMessage());
 *         CommandLine.usage(Encrypt.class, System.err);
 *     }
 * }
 * </pre><p>
 * Invoke the above program with some command line arguments. The below are all equivalent:
 * </p>
 * <pre>
 * --verbose --out=outfile in1 in2
 * --verbose --out outfile in1 in2
 * -v --out=outfile in1 in2
 * -v -o outfile in1 in2
 * -v -o=outfile in1 in2
 * -vo outfile in1 in2
 * -vo=outfile in1 in2
 * -v -ooutfile in1 in2
 * -vooutfile in1 in2
 * </pre>
 *
 * @see <a href="http://catb.org/~esr/writings/taoup/html/ch10s05.html#id2948149">TAOUP's hints for designing good
 *        command line user interfaces</a>
 * @see <a href="https://www.gnu.org/prep/standards/html_node/Command_002dLine-Interfaces.html#Command_002dLine-Interfaces">GNU
 *        Standards for Command Line Interfaces</a>
 */
public class CommandLine {
    /** This is picocli version {@value}. */
    public static final String VERSION = "0.4.0";
    public static Boolean ansi = (System.getProperty("picocli.ansi") == null ? null : Boolean.getBoolean("picocli.ansi"));

    private final Interpreter interpreter;
    private final List<Object> parsedCommands = new ArrayList<Object>();
    private final Map<String, Object> commandMap = new LinkedHashMap<String, Object>();

    /**
     * Constructs a new {@code CommandLine} interpreter with the specified annotated object.
     * When the {@link #parse(String...)} method is called, fields of the specified object that are annotated
     * with {@code @Option} or {@code @Parameters} will be initialized based on command line arguments.
     * @param annotatedObject the object to initialize from the command line arguments
     */
    public CommandLine(Object annotatedObject) {
        interpreter = new Interpreter(annotatedObject);
    }

    /** Registers a sub-command with the specified name.
     * @param name the string to recognize on the command line as a sub-command
     * @param annotatedObject the object to initialize with command line arguments following the sub-command name
     * @return this CommandLine object, to allow method chaining
     */
    public CommandLine addCommand(String name, Object annotatedObject) {
        interpreter.commands.put(name, new Interpreter(annotatedObject));
        commandMap.put(name, annotatedObject);
        return this;
    }
    /** Returns a map with the registered sub-commands. */
    public Map<String, Object> getCommands() {
        return new LinkedHashMap<String, Object>(commandMap);
    }

    /**
     * <p>
     * Convenience method that initializes the specified annotated object from the specified command line arguments.
     * </p><p>
     * This is equivalent to
     * </p><pre>
     * CommandLine cli = new CommandLine(annotatedObject);
     * cli.parse(args);
     * return annotatedObject;
     * </pre>
     *
     * @param annotatedObject the object to initialize. This object contains fields annotated with
     *          {@code @Option} or {@code @Parameters}.
     * @param args the command line arguments to parse
     * @param <T> the type of the annotated object
     * @return the specified annotated object
     * @throws ParameterException if the specified command line arguments are invalid
     */
    public static <T> T parse(T annotatedObject, String... args) {
        CommandLine cli = new CommandLine(annotatedObject);
        cli.parse(args);
        return annotatedObject;
    }

    /**
     * <p>
     * Initializes the annotated object that this {@code CommandLine} was constructed with, based on
     * the specified command line arguments.
     * </p>
     *
     * @param args the command line arguments to parse
     * @return the annotated object that this {@code CommandLine} was constructed with
     * @throws ParameterException if the specified command line arguments are invalid
     */
    public List<Object> parse(String... args) {
        interpreter.parse(args);
        return new ArrayList<Object>(parsedCommands);
    }

    /**
     * Equivalent to {@code new CommandLine(annotatedObject).usage(out);}. See {@link #usage(PrintStream)} for details.
     * @param annotatedObject the object annotated with {@link Command}, {@link Option} and {@link Parameters}
     * @param out the print stream to print the help message to
     */
    public static void usage(Object annotatedObject, PrintStream out) {
        new CommandLine(annotatedObject).usage(out);
    }

    /**
     * Prints a usage help message for the specified annotated class to the specified {@code PrintStream}.
     * Delegates construction of the usage help message to the {@link Help} inner class and is equivalent to:
     * <pre>
     * Help help = new Help(annotatedObject).addAllCommands(getCommands());
     * StringBuilder sb = new StringBuilder()
     *         .append(help.headerHeading())
     *         .append(help.header())
     *         .append(help.synopsisHeading())      //e.g. Usage:
     *         .append(help.synopsis())             //e.g. &lt;main class&gt; [OPTIONS] &lt;command&gt; [COMMAND-OPTIONS] [ARGUMENTS]
     *         .append(help.descriptionHeading())   //e.g. %nDescription:%n%n
     *         .append(help.description())          //e.g. {"Converts foos to bars.", "Use options to control conversion mode."}
     *         .append(help.parameterListHeading()) //e.g. %nPositional parameters:%n%n
     *         .append(help.parameterList())        //e.g. [FILE...] the files to convert
     *         .append(help.optionListHeading())    //e.g. %nOptions:%n%n
     *         .append(help.optionList())           //e.g. -h, --help   displays this help and exits
     *         .append(help.commandListHeading())   //e.g. %nCommands:%n%n
     *         .append(help.commandList())          //e.g.    add       adds the frup to the frooble
     *         .append(help.footerHeading())
     *         .append(help.footer());
     * out.print(sb);
     * </pre>
     * <p>Annotate your class with {@link Command} to control many aspects of the usage help message, including
     * the program name, text of section headings and section contents, and some aspects of the auto-generated sections
     * of the usage help message.
     * <p>To customize the auto-generated sections of the usage help message, like how option details are displayed,
     * instantiate a {@link Help} object and use a {@link Help.TextTable} with more of fewer columns, a custom
     * {@linkplain Help.Layout layout}, and/or a custom option {@linkplain Help.IOptionRenderer renderer}
     * for ultimate control over which aspects of an Option or Field are displayed where.</p>
     * @param out the {@code PrintStream} to print the usage help message to
     */
    public void usage(PrintStream out) {
        Help help = new Help(interpreter.annotatedObject).addAllCommands(commandMap);
        StringBuilder sb = new StringBuilder()
                .append(help.headerHeading())
                .append(help.header())
                .append(help.synopsisHeading())      //e.g. Usage:
                .append(help.synopsis())             //e.g. &lt;main class&gt; [OPTIONS] &lt;command&gt; [COMMAND-OPTIONS] [ARGUMENTS]
                .append(help.descriptionHeading())   //e.g. %nDescription:%n%n
                .append(help.description())          //e.g. {"Converts foos to bars.", "Use options to control conversion mode."}
                .append(help.parameterListHeading()) //e.g. %nPositional parameters:%n%n
                .append(help.parameterList())        //e.g. [FILE...] the files to convert
                .append(help.optionListHeading())    //e.g. %nOptions:%n%n
                .append(help.optionList())           //e.g. -h, --help   displays this help and exits
                .append(help.commandListHeading())   //e.g. %nCommands:%n%n
                .append(help.commandList())          //e.g.    add       adds the frup to the frooble
                .append(help.footerHeading())
                .append(help.footer());
        out.print(sb);
    }

    /**
     * Convenience method to allow command line application authors to avoid some boilerplate code in their application.
     * The annotated object needs to implement {@link Runnable}. Calling this method is equivalent to:
     * <pre>
     * Runnable runnable = null;
     * try {
     *     runnable = parse(annotatedObject, args);
     * } catch (Exception ex) {
     *     System.err.println(ex.getMessage());
     *     usage(annotatedObject, System.err);
     *     return;
     * }
     * runnable.run();
     * </pre>
     * Note that this method is not suitable for commands with subcommands.
     * @param annotatedObject the command to run when {@linkplain #parse(Object, String...) parsing} succeeds.
     * @param args the command line arguments to parse
     * @param <R> the annotated object to initialize and run
     */
    public static <R extends Runnable> void run(R annotatedObject, String... args) {
        Runnable runnable = null;
        try {
            runnable = parse(annotatedObject, args);
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
            usage(annotatedObject, System.err);
            return;
        }
        runnable.run();
    }

    /**
     * Registers the specified type converter for the specified class. When initializing fields annotated with
     * {@link Option}, the field's type is used as a lookup key to find the associated type converter, and this
     * type converter converts the original command line argument string value to the correct type.
     * <p>
     * Java 8 lambdas make it easy to register custom type converters:
     * </p>
     * <pre>
     * commandLine.registerConverter(java.nio.file.Path.class, s -&gt; java.nio.file.Paths.get(s));
     * commandLine.registerConverter(java.time.Duration.class, s -&gt; java.time.Duration.parse(s));</pre>
     * <p>
     * Built-in type converters are pre-registered for the following java 1.5 types:
     * </p>
     * <ul>
     *   <li>all primitive types</li>
     *   <li>all primitive wrapper types: Boolean, Byte, Character, Double, Float, Integer, Long, Short</li>
     *   <li>any enum</li>
     *   <li>java.io.File</li>
     *   <li>java.math.BigDecimal</li>
     *   <li>java.math.BigInteger</li>
     *   <li>java.net.InetAddress</li>
     *   <li>java.net.URI</li>
     *   <li>java.net.URL</li>
     *   <li>java.nio.charset.Charset</li>
     *   <li>java.sql.Time</li>
     *   <li>java.util.Date</li>
     *   <li>java.util.UUID</li>
     *   <li>java.util.regex.Pattern</li>
     *   <li>StringBuilder</li>
     *   <li>CharSequence</li>
     *   <li>String</li>
     * </ul>
     *
     * @param cls the target class to convert parameter string values to
     * @param converter the class capable of converting string values to the specified target type
     * @param <K> the target type
     * @return this CommandLine object, to allow method chaining
     */
    public <K> CommandLine registerConverter(Class<K> cls, ITypeConverter<K> converter) {
        interpreter.converterRegistry.put(Assert.notNull(cls, "class"), Assert.notNull(converter, "converter"));
        return this;
    }

    /** Returns the String that separates option names from option values. {@code '='} by default. */
    public String getSeparator() {
        return interpreter.separator;
    }

    /** Sets the String that separates option names from option values to the specified value. */
    public void setSeparator(String separator) {
        interpreter.separator = Assert.notNull(separator, "separator");
    }
    private static boolean empty(String str) { return str == null || str.trim().length() == 0; }
    private static boolean empty(Object[] array) { return array == null || array.length == 0; }
    private static boolean empty(Text txt) { return txt == null || txt.plain.toString().trim().length() == 0; }
    private static String str(String[] arr, int i) { return (arr == null || arr.length == 0) ? "" : arr[i]; }
    private static boolean isBoolean(Class<?> type) { return type == Boolean.class || type == Boolean.TYPE; }

    /**
     * <p>
     * Annotate fields in your class with {@code @Option} and picocli will initialize these fields when matching
     * arguments are specified on the command line.
     * </p><p>
     * For example:
     * </p>
     * <pre>import static picocli.CommandLine.*;
     *
     * public class MyClass {
     *     &#064;Parameters(type = File.class, description = "Any number of input files")
     *     private List&lt;File&gt; files = new ArrayList&lt;File&gt;();
     *
     *     &#064;Option(names = { "-o", "--out" }, description = "Output file (default: print to console)")
     *     private File outputFile;
     *
     *     &#064;Option(names = { "-v", "--verbose"}, description = "Verbosely list files processed")
     *     private boolean verbose;
     *
     *     &#064;Option(names = { "-h", "--help", "-?", "-help"}, help = true, description = "Display this help and exit")
     *     private boolean help;
     *
     *     &#064;Option(names = { "-V", "--version"}, help = true, description = "Display version information and exit")
     *     private boolean version;
     * }
     * </pre>
     * <p>
     * A field cannot be annotated with both {@code @Parameters} and {@code @Option} or a
     * {@code ParameterException} is thrown.
     * </p>
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Option {
        /**
         * One or more option names. At least one option name is required.
         * <p>
         * Different environments have different conventions for naming options, but usually options have a prefix
         * that sets them apart from parameters.
         * Picocli supports all of the below styles. The default separator is {@code '='}, but this can be configured.
         * </p><p>
         * <b>*nix</b>
         * </p><p>
         * In Unix and Linux, options have a short (single-character) name, a long name or both.
         * Short options
         * (<a href="http://pubs.opengroup.org/onlinepubs/9699919799/basedefs/V1_chap12.html#tag_12_02">POSIX
         * style</a> are single-character and are preceded by the {@code '-'} character, e.g., {@code `-v'}.
         * <a href="https://www.gnu.org/software/tar/manual/html_node/Long-Options.html">GNU-style</a> long
         * (or <em>mnemonic</em>) options start with two dashes in a row, e.g., {@code `--file'}.
         * </p><p>Picocli supports the POSIX convention that short options can be grouped, with the last option
         * optionally taking a parameter, which may be attached to the option name or separated by a space or
         * a {@code '='} character. The below examples are all equivalent:
         * </p><pre>
         * -xvfFILE
         * -xvf FILE
         * -xvf=FILE
         * -xv --file FILE
         * -xv --file=FILE
         * -x -v --file FILE
         * -x -v --file=FILE
         * </pre><p>
         * <b>DOS</b>
         * </p><p>
         * DOS options mostly have upper case single-character names and start with a single slash {@code '/'} character.
         * Option parameters are separated by a {@code ':'} character. Options cannot be grouped together but
         * must be specified separately. For example:
         * </p><pre>
         * DIR /S /A:D /T:C
         * </pre><p>
         * <b>PowerShell</b>
         * </p><p>
         * Windows PowerShell options generally are a word preceded by a single {@code '-'} character, e.g., {@code `-Help'}.
         * Option parameters are separated by a space or by a {@code ':'} character.
         * </p>
         * @return one or more option names
         */
        String[] names();

        /**
         * Indicates whether this option is required. By default this is false.
         * If an option is required, but a user invokes the program without specifying the required option,
         * a {@link MissingParameterException} is thrown from the {@link #parse(String...)} method.
         * @return whether this option is required
         */
        boolean required() default false;

        /**
         * Set {@code help=true} if this option should disable validation of the remaining arguments:
         * If the {@code help} option is specified, no error message is generated for missing required options.
         * <p>
         * This attribute is useful for special options like help ({@code -h} and {@code --help} on unix,
         * {@code -?} and {@code -Help} on Windows) or version ({@code -V} and {@code --version} on unix,
         * {@code -Version} on Windows).
         * </p>
         * <p>
         * Note that the {@link #parse(String...)} method will not print help documentation. It will only set
         * the value of the annotated field. It is the responsibility of the caller to inspect the annotated fields
         * and take the appropriate action.
         * </p>
         * @return whether this option disables validation of the other arguments
         */
        boolean help() default false;

        /**
         * Description of this option, used when generating the usage documentation.
         * @return the description of this option
         */
        String[] description() default {};

        /**
         * Specifies the minimum number of required parameters and the maximum number of accepted parameters.
         * If an option declares a positive arity, and the user specifies an insufficient number of parameters on the
         * command line, a {@link MissingParameterException} is thrown by the {@link #parse(String...)} method.
         * <p>
         * In many cases picocli can deduce the number of required parameters from the field's type.
         * By default, flags (boolean options) have arity zero,
         * and single-valued type fields (String, int, Integer, double, Double, File, Date, etc) have arity one.
         * Generally, fields with types that cannot hold multiple values can omit the {@code arity} attribute.
         * </p><p>
         * Fields used to capture options with arity two or higher should have a type that can hold multiple values,
         * like arrays or Collections. See {@link #type()} for strongly-typed Collection fields.
         * </p><p>
         * For example, if an option has 2 required parameters and any number of optional parameters,
         * specify {@code @Option(names = "-example", arity = "2..*")}.
         * </p>
         * <b>A note on boolean options</b>
         * <p>
         * By default picocli does not expect boolean options (also called "flags" or "switches") to have a parameter.
         * You can make a boolean option take a required parameter by annotating your field with {@code arity="1"}.
         * For example: </p>
         * <pre>&#064;Option(names = "-v", arity = "1") boolean verbose;</pre>
         * <p>
         * Because this boolean field is defined with arity 1, the user must specify either {@code <program> -v false}
         * or {@code <program> -v true}
         * on the command line, or a {@link MissingParameterException} is thrown by the {@link #parse(String...)}
         * method.
         * </p><p>
         * To make the boolean parameter possible but optional, define the field with {@code arity = "0..1"}.
         * For example: </p>
         * <pre>&#064;Option(names="-v", arity="0..1") boolean verbose;</pre>
         * <p>This will accept any of the below without throwing an exception:</p>
         * <pre>
         * -v
         * -v true
         * -v false
         * </pre>
         * @return how many arguments this option requires
         */
        String arity() default "";

        /**
         * Specify a {@code paramLabel} for the option parameter to be used in the usage help message. If omitted,
         * picocli uses the field name in fish brackets ({@code '<'} and {@code '>'}) by default. Example:
         * <pre>class Example {
         *     &#064;Option(names = {"-o", "--output"}, paramLabel="FILE", description="path of the output file")
         *     private File out;
         *     &#064;Option(names = {"-j", "--jobs"}, arity="0..1", description="Allow N jobs at once; infinite jobs with no arg.")
         *     private int maxJobs = -1;
         * }</pre>
         * <p>By default, the above gives a usage help message like the following:</p><pre>
         * Usage: &lt;main class&gt; [OPTIONS]
         * -o, --output FILE       path of the output file
         * -j, --jobs [&lt;maxJobs&gt;]  Allow N jobs at once; infinite jobs with no arg.
         * </pre>
         * @return name of the option parameter used in the usage help message
         */
        String paramLabel() default "";

        /**
         * <p>
         * Specify a {@code type} if the annotated field is a {@code Collection} that should hold objects other than Strings.
         * </p><p>
         * If the field's type is a {@code Collection}, the generic type parameter of the collection is erased and
         * cannot be determined at runtime. Specify a {@code type} attribute to store values other than String in
         * the Collection. Picocli will use the {@link ITypeConverter}
         * that is {@linkplain #registerConverter(Class, ITypeConverter) registered} for that type to convert
         * the raw String values before they are added to the collection.
         * </p><p>
         * When the field's type is an array, the {@code type} attribute is ignored: the values will be converted
         * to the array component type and the array will be replaced with a new instance containing both the old and
         * the new values. </p>
         * @return the type to convert the raw String values to before adding them to the Collection
         */
        Class<?> type() default String.class;

        /**
         * Specify a regular expression to use to split option parameter values before applying them to the field.
         * All elements resulting from the split are added to the array or Collection. Ignored for single-value fields.
         * @return a regular expression to split option parameter values or {@code ""} if the value should not be split
         * @see String#split(String)
         */
        String split() default "";

        /**
         * Set {@code hidden=true} if this option should not be included in the usage documentation.
         * @return whether this option should be excluded from the usage message
         */
        boolean hidden() default false;
    }
    /**
     * <p>
     * Fields annotated with {@code @Parameters} will be initialized with positional parameters. By specifying the
     * {@link #index()} attribute you can pick which (or what range) of the positional parameters to apply. If no index
     * is specified, the field will get all positional parameters (so it should be an array or a collection).
     * </p><p>
     * When parsing the command line arguments, picocli first tries to match arguments to {@link Option Options}.
     * Positional parameters are the arguments that follow the options, or the arguments that follow a "--" (double
     * dash) argument on the command line.
     * </p><p>
     * For example:
     * </p>
     * <pre>import static picocli.CommandLine.*;
     *
     * public class MyCalcParameters {
     *     &#064;Parameters(type = BigDecimal.class, description = "Any number of input numbers")
     *     private List&lt;BigDecimal&gt; files = new ArrayList&lt;BigDecimal&gt;();
     *
     *     &#064;Option(names = { "-h", "--help", "-?", "-help"}, help = true, description = "Display this help and exit")
     *     private boolean help;
     * }
     * </pre><p>
     * A field cannot be annotated with both {@code @Parameters} and {@code @Option} or a {@code ParameterException}
     * is thrown.</p>
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Parameters {
        /** Specify an index ("0", or "1", etc.) to pick which of the command line arguments should be assigned to this
         * field. For array or Collection fields, you can also specify an index range ("0..3", or "2..*", etc.) to assign
         * a subset of the command line arguments to this field. The default is "*", meaning all command line arguments.
         * @return an index or range specifying which of the command line arguments should be assigned to this field
         */
        String index() default "*";

        /** Description of the parameter(s), used when generating the usage documentation.
         * @return the description of the parameter(s)
         */
        String[] description() default {};

        /**
         * Specifies the minimum number of required parameters and the maximum number of accepted parameters. If a
         * positive arity is declared, and the user specifies an insufficient number of parameters on the command line,
         * {@link MissingParameterException} is thrown by the {@link #parse(String...)} method.
         * <p>The default depends on the type of the parameter: booleans require no parameters, arrays and Collections
         * accept zero to any number of parameters, and any other type accepts one parameter.</p>
         * @return the range of minimum and maximum parameters accepted by this command
         */
        String arity() default "";

        /**
         * Specify a {@code paramLabel} for the parameter to be used in the usage help message. If omitted,
         * picocli uses the field name in fish brackets ({@code '<'} and {@code '>'}) by default. Example:
         * <pre>class Example {
         *     &#064;Parameters(paramLabel="FILE", description="path of the input FILE(s)")
         *     private File[] inputFiles;
         * }</pre>
         * <p>By default, the above gives a usage help message like the following:</p><pre>
         * Usage: &lt;main class&gt; [FILE...]
         * [FILE...]       path of the input FILE(s)
         * </pre>
         * @return name of the positional parameter used in the usage help message
         */
        String paramLabel() default "";

        /**
         * <p>
         * Specify a {@code type} if the annotated field is a {@code Collection} that should hold objects other than Strings.
         * </p><p>
         * If the field's type is a {@code Collection}, the generic type parameter of the collection is erased and
         * cannot be determined at runtime. Specify a {@code type} attribute to store values other than String in
         * the Collection. Picocli will use the {@link ITypeConverter}
         * that is {@linkplain #registerConverter(Class, ITypeConverter) registered} for that type to convert
         * the raw String values before they are added to the collection.
         * </p><p>
         * When the field's type is an array, the {@code type} attribute is ignored: the values will be converted
         * to the array component type and the array will be replaced with a new instance containing both the old and
         * the new values. </p>
         * @return the type to convert the raw String values to before adding them to the Collection
         */
        Class<?> type() default String.class;

        /**
         * Specify a regular expression to use to split positional parameter values before applying them to the field.
         * All elements resulting from the split are added to the array or Collection. Ignored for single-value fields.
         * @return a regular expression to split operand values or {@code ""} if the value should not be split
         * @see String#split(String)
         */
        String split() default "";

        /**
         * Set {@code hidden=true} if this parameter should not be included in the usage message.
         * @return whether this parameter should be excluded from the usage message
         */
        boolean hidden() default false;
    }

    /**
     * <p>Annotate your class with {@code @Command} when you want more control over the format of the generated help
     * message.
     * </p><pre>
     * &#064;Command(name      = "Encrypt",
     *        description = "Encrypt FILE(s), or standard input, to standard output or to the output file.",
     *        footer      = "Copyright (c) 2017")
     * public class Encrypt {
     *     &#064;Parameters(paramLabel = "FILE", type = File.class, description = "Any number of input files")
     *     private List&lt;File&gt; files     = new ArrayList&lt;File&gt;();
     *
     *     &#064;Option(names = { "-o", "--out" }, description = "Output file (default: print to console)")
     *     private File outputFile;
     * }</pre>
     * <p>
     * The structure of a help message looks like this:
     * </p><ul>
     *   <li>[header]</li>
     *   <li>[synopsis]: {@code Usage: <commandName> [OPTIONS] [FILE...]}</li>
     *   <li>[description]</li>
     *   <li>[parameter list]: {@code      [FILE...]   Any number of input files}</li>
     *   <li>[option list]: {@code   -h, --help   prints this help message and exits}</li>
     *   <li>[footer]</li>
     * </ul> */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Command {
        /** Program name to show in the synopsis. If omitted, {@code "<main class>"} is used.
         * @see Help#commandName */
        String name() default "<main class>";

        /** String that separates options from option parameters. Default is {@code "="}. Spaces are also accepted.
         * @see Help#separator */
        String separator() default "=";

        /** Set the heading preceding the header section. May contain embedded {@linkplain java.util.Formatter format specifiers}.
         * @see Help#headerHeading(Object...)  */
        String headerHeading() default "";

        /** Optional summary description of the command, shown before the synopsis.
         * @see Help#header
         * @see Help#header(Object...)  */
        String[] header() default {};

        /** Set the heading preceding the synopsis text. May contain embedded
         * {@linkplain java.util.Formatter format specifiers}. The default heading is {@code "Usage: "} (without a line
         * break between the heading and the synopsis text).
         * @see Help#synopsisHeading(Object...)  */
        String synopsisHeading() default "Usage: ";

        /** Specify {@code true} to generate an abbreviated synopsis like {@code "<main> [OPTIONS] [PARAMETERS...]"}.
         * By default, a detailed synopsis with individual option names and parameters is generated.
         * @see Help#abbreviateSynopsis
         * @see Help#abbreviatedSynopsis()
         * @see Help#detailedSynopsis(Comparator, boolean) */
        boolean abbreviateSynopsis() default false;

        /** Specify one or more custom synopsis lines to display instead of an auto-generated synopsis.
         * @see Help#customSynopsis
         * @see Help#customSynopsis(Object...) */
        String[] customSynopsis() default {};

        /** Set the heading preceding the description section. May contain embedded {@linkplain java.util.Formatter format specifiers}.
         * @see Help#descriptionHeading(Object...)  */
        String descriptionHeading() default "";

        /** Optional text to display between the synopsis line(s) and the list of options.
         * @see Help#description
         * @see Help#description(Object...) */
        String[] description() default {};

        /** Set the heading preceding the parameters list. May contain embedded {@linkplain java.util.Formatter format specifiers}.
         * @see Help#parameterListHeading(Object...)  */
        String parameterListHeading() default "";

        /** Set the heading preceding the options list. May contain embedded {@linkplain java.util.Formatter format specifiers}.
         * @see Help#optionListHeading(Object...)  */
        String optionListHeading() default "";

        /** Specify {@code false} to show Options in declaration order. The default is to sort alphabetically.
         * @see Help#sortOptions */
        boolean sortOptions() default true;

        /** Prefix required options with this character in the options list. The default is no marker: the synopsis
         * indicates which options and parameters are required.
         * @see Help#requiredOptionMarker */
        char requiredOptionMarker() default ' ';

        /** Specify {@code true} to show default values in the description column of the options list (except for
         * boolean options). False by default.
         * @see Help#showDefaultValues */
        boolean showDefaultValues() default false;

        /** Set the heading preceding the sub-commands list. May contain embedded {@linkplain java.util.Formatter format specifiers}.
         * The default heading is {@code "Commands:%n"} (with a line break at the end).
         * @see Help#commandListHeading(Object...)  */
        String commandListHeading() default "Commands:%n";

        /** Set the heading preceding the footer section. May contain embedded {@linkplain java.util.Formatter format specifiers}.
         * @see Help#footerHeading(Object...)  */
        String footerHeading() default "";

        /** Optional text to display after the list of options.
         * @see Help#footer
         * @see Help#footer(Object...) */
        String[] footer() default {};
    }
    /**
     * <p>
     * When parsing command line arguments and initializing
     * fields annotated with {@link Option @Option} or {@link Parameters @Parameters},
     * String values can be converted to any type for which a {@code ITypeConverter} is registered.
     * </p><p>
     * This interface defines the contract for classes that know how to convert a String into some domain object.
     * Custom converters can be registered with the {@link #registerConverter(Class, ITypeConverter)} method.
     * </p><p>
     * Java 8 lambdas make it easy to register custom type converters:
     * </p>
     * <pre>
     * commandLine.registerConverter(java.nio.file.Path.class, s -&gt; java.nio.file.Paths.get(s));
     * commandLine.registerConverter(java.time.Duration.class, s -&gt; java.time.Duration.parse(s));</pre>
     * <p>
     * Built-in type converters are pre-registered for the following java 1.5 types:
     * </p>
     * <ul>
     *   <li>all primitive types</li>
     *   <li>all primitive wrapper types: Boolean, Byte, Character, Double, Float, Integer, Long, Short</li>
     *   <li>any enum</li>
     *   <li>java.io.File</li>
     *   <li>java.math.BigDecimal</li>
     *   <li>java.math.BigInteger</li>
     *   <li>java.net.InetAddress</li>
     *   <li>java.net.URI</li>
     *   <li>java.net.URL</li>
     *   <li>java.nio.charset.Charset</li>
     *   <li>java.sql.Time</li>
     *   <li>java.util.Date</li>
     *   <li>java.util.UUID</li>
     *   <li>java.util.regex.Pattern</li>
     *   <li>StringBuilder</li>
     *   <li>CharSequence</li>
     *   <li>String</li>
     * </ul>
     * @param <K> the type of the object that is the result of the conversion
     */
    public interface ITypeConverter<K> {
        /**
         * Converts the specified command line argument value to some domain object.
         * @param value the command line argument String value
         * @return the resulting domain object
         * @throws Exception an exception detailing what went wrong during the conversion
         */
        K convert(String value) throws Exception;
    }
    /** Describes the number of parameters required and accepted by an option or a positional parameter. */
    public static class Arity {
        /** Required number of parameters for an option or positional parameter. */
        public int min;
        /** Maximum accepted number of parameters for an option or positional parameter. */
        public int max;
        public boolean isVariable;
        private final boolean isUnspecified;
        private final String originalValue;

        /** Constructs a new Arity object with the specified parameters.
         * @param min minimum number of required parameters
         * @param max maximum number of allowed parameters (or Integer.MAX_VALUE if variable)
         * @param variable {@code true} if any number or parameters is allowed, {@code false} otherwise
         * @param unspecified {@code true} if no arity was specified on the option/parameter (value is based on type)
         * @param originalValue the original value that was specified on the option or parameter
         */
        public Arity(int min, int max, boolean variable, boolean unspecified, String originalValue) {
            this.min = min;
            this.max = max;
            this.isVariable = variable;
            this.isUnspecified = unspecified;
            this.originalValue = originalValue;
        }
        /** Returns a new {@code Arity} based on the Option annotation on the specified field, or the field type if no
         * arity was specified. */
        public static Arity forOption(Field field) {
            return adjustForType(Arity.valueOf(field.getAnnotation(Option.class).arity()), field);
        }
        /** Returns a new {@code Arity} based on the Parameters annotation on the specified field, or the field type
         * if no arity was specified. */
        public static Arity forParameters(Field field) {
            return adjustForType(Arity.valueOf(field.getAnnotation(Parameters.class).arity()), field);
        }
        static Arity adjustForType(Arity result, Field field) {
            if (result.isUnspecified) {
                Arity def = forType(field.getType());
                result.min = def.min;
                result.max = def.max;
                result.isVariable = def.isVariable;
            }
            return result;
        }
        /** Returns a new {@code Arity} based on the specified type: booleans have arity 0, arrays or Collections have
         * arity "0..*", and other types have arity 1. */
        public static Arity forType(Class<?> type) {
            if (isBoolean(type)) {
                return Arity.valueOf("0");
            } else if (type.isArray() || Collection.class.isAssignableFrom(type)) {
                return Arity.valueOf("0..*");
            }
            return Arity.valueOf("1");// for single-valued fields
        }
        /** Leniently parses the specified String as an {@code Arity} value and return the result. An arity string can
         * be a fixed integer value or a range of the form {@code MIN_VALUE + ".." + MAX_VALUE}. If the
         * {@code MIN_VALUE} string is not numeric, the minimum is zero. If the {@code MAX_VALUE} is not numeric, the
         * arity is taken to be variable and the maximum is {@code Integer.MAX_VALUE}.*/
        public static Arity valueOf(String arity) {
            arity = arity.trim();
            boolean unspecified = arity.length() == 0 || arity.startsWith(".."); // || arity.endsWith("..");
            int min = -1, max = -1;
            boolean variable = false;
            int dots = -1;
            if ((dots = arity.indexOf("..")) >= 0) {
                min = parseInt(arity.substring(0, dots), 0);
                max = parseInt(arity.substring(dots + 2), Integer.MAX_VALUE);
                variable = max == Integer.MAX_VALUE;
            } else {
                max = parseInt(arity, Integer.MAX_VALUE);
                variable = max == Integer.MAX_VALUE;
                min = variable ? 0 : max;
            }
            Arity result = new Arity(min, max, variable, unspecified, arity);
            return result;
        }
        private static int parseInt(String str, int defaultValue) {
            try {
                return Integer.parseInt(str);
            } catch (Exception ex) {
                return defaultValue;
            }
        }
        public boolean equals(Object object) {
            if (!(object instanceof Arity)) { return false; }
            Arity other = (Arity) object;
            return other.max == this.max && other.min == this.min && other.isVariable == this.isVariable;
        }
        public int hashCode() {
            return ((17 * 37 + max) * 37 + min) * 37 + (isVariable ? 1 : 0);
        }
        public String toString() {
            String result = min == max ? String.valueOf(min) : min + ".." + (isVariable ? "*" : max);
            return result + (empty(originalValue) || result.equals(originalValue) ? "" : " ('" + originalValue + "')");
        }
    }
    private static void init(Class<?> cls,
                              List<Field> requiredFields,
                              Map<String, Field> optionName2Field,
                              Map<Character, Field> singleCharOption2Field,
                              List<Field> positionalParametersFields) {
        Field[] declaredFields = cls.getDeclaredFields();
        for (Field field : declaredFields) {
            if (field.isAnnotationPresent(Option.class)) {
                field.setAccessible(true);
                Option option = field.getAnnotation(Option.class);
                if (option.required()) {
                    requiredFields.add(field);
                }
                for (String name : option.names()) { // cannot be null or empty
                    Field existing = optionName2Field.put(name, field);
                    if (existing != null && existing != field) {
                        throw DuplicateOptionAnnotationsException.create(name, field, existing);
                    }
                    if (name.length() == 2 && name.startsWith("-")) {
                        char flag = name.charAt(1);
                        Field existing2 = singleCharOption2Field.put(flag, field);
                        if (existing2 != null && existing2 != field) {
                            throw DuplicateOptionAnnotationsException.create(name, field, existing2);
                        }
                    }
                }
            }
            if (field.isAnnotationPresent(Parameters.class)) {
                if (field.isAnnotationPresent(Option.class)) {
                    throw new ParameterException("A field can be either @Option or @Parameters, but '"
                            + field.getName() + "' is both.");
                }
                positionalParametersFields.add(field);
            }
        }
    }

    /**
     * Helper class responsible for processing command line arguments.
     */
    private class Interpreter {
        private final Map<String, Interpreter> commands = new LinkedHashMap<String, Interpreter>();
        private final Map<Class<?>, ITypeConverter<?>> converterRegistry = new HashMap<Class<?>, ITypeConverter<?>>();
        private final Map<String, Field> optionName2Field                = new HashMap<String, Field>();
        private final Map<Character, Field> singleCharOption2Field       = new HashMap<Character, Field>();
        private final List<Field> requiredFields                         = new ArrayList<Field>();
        private final List<Field> positionalParametersFields             = new ArrayList<Field>();
        private final Object annotatedObject;
        private boolean isHelpRequested;
        private String separator = "=";

        Interpreter(Object annotatedObject) {
            converterRegistry.put(String.class,        new BuiltIn.StringConverter());
            converterRegistry.put(StringBuilder.class, new BuiltIn.StringBuilderConverter());
            converterRegistry.put(CharSequence.class,  new BuiltIn.CharSequenceConverter());
            converterRegistry.put(Byte.class,          new BuiltIn.ByteConverter());
            converterRegistry.put(Byte.TYPE,           new BuiltIn.ByteConverter());
            converterRegistry.put(Boolean.class,       new BuiltIn.BooleanConverter());
            converterRegistry.put(Boolean.TYPE,        new BuiltIn.BooleanConverter());
            converterRegistry.put(Character.class,     new BuiltIn.CharacterConverter());
            converterRegistry.put(Character.TYPE,      new BuiltIn.CharacterConverter());
            converterRegistry.put(Short.class,         new BuiltIn.ShortConverter());
            converterRegistry.put(Short.TYPE,          new BuiltIn.ShortConverter());
            converterRegistry.put(Integer.class,       new BuiltIn.IntegerConverter());
            converterRegistry.put(Integer.TYPE,        new BuiltIn.IntegerConverter());
            converterRegistry.put(Long.class,          new BuiltIn.LongConverter());
            converterRegistry.put(Long.TYPE,           new BuiltIn.LongConverter());
            converterRegistry.put(Float.class,         new BuiltIn.FloatConverter());
            converterRegistry.put(Float.TYPE,          new BuiltIn.FloatConverter());
            converterRegistry.put(Double.class,        new BuiltIn.DoubleConverter());
            converterRegistry.put(Double.TYPE,         new BuiltIn.DoubleConverter());
            converterRegistry.put(File.class,          new BuiltIn.FileConverter());
            converterRegistry.put(URI.class,           new BuiltIn.URIConverter());
            converterRegistry.put(URL.class,           new BuiltIn.URLConverter());
            converterRegistry.put(Date.class,          new BuiltIn.ISO8601DateConverter());
            converterRegistry.put(Time.class,          new BuiltIn.ISO8601TimeConverter());
            converterRegistry.put(BigDecimal.class,    new BuiltIn.BigDecimalConverter());
            converterRegistry.put(BigInteger.class,    new BuiltIn.BigIntegerConverter());
            converterRegistry.put(Charset.class,       new BuiltIn.CharsetConverter());
            converterRegistry.put(InetAddress.class,   new BuiltIn.InetAddressConverter());
            converterRegistry.put(Pattern.class,       new BuiltIn.PatternConverter());
            converterRegistry.put(UUID.class,          new BuiltIn.UUIDConverter());

            this.annotatedObject    = Assert.notNull(annotatedObject, "annotatedObject");
            Class<?> cls = annotatedObject.getClass();
            String declaredSeparator = null;
            while (cls != null) {
                init(cls, requiredFields, optionName2Field, singleCharOption2Field, positionalParametersFields);
                if (cls.isAnnotationPresent(Command.class)) {
                    Command command = cls.getAnnotation(Command.class);
                    declaredSeparator = (declaredSeparator == null) ? command.separator() : declaredSeparator;
                }
                cls = cls.getSuperclass();
            }
            separator = declaredSeparator != null ? declaredSeparator : separator;
        }

        /**
         * Entry point into parsing command line arguments.
         * @param args the command line arguments
         * @return the annotated object, initialized with the command line arguments
         * @throws ParameterException if the specified command line arguments are invalid
         */
        Object parse(String... args) {
            Assert.notNull(args, "argument array");
            Stack<String> arguments = new Stack<String>();
            for (int i = args.length - 1; i >= 0; i--) {
                arguments.push(args[i]);
            }
            // first reset any state in case this CommandLine instance is being reused
            isHelpRequested = false;
            parse(arguments, args);
            return annotatedObject;
        }

        private void parse(Stack<String> argumentStack, String[] originalArgs) {
            parsedCommands.add(annotatedObject);
            Set<Field> required = new HashSet<Field>(requiredFields);
            try {
                processPositionalParameters0(true, argumentStack);
                processArguments(argumentStack, required, originalArgs);
            } catch (ParameterException ex) {
                throw ex;
            } catch (Exception ex) {
                int offendingArgIndex = originalArgs.length - argumentStack.size();
                String arg = offendingArgIndex >= 0 && offendingArgIndex < originalArgs.length ? originalArgs[offendingArgIndex] : "?";
                throw ParameterException.create(ex, arg, argumentStack.size(), originalArgs);
            }
            if (!isHelpRequested && !required.isEmpty()) {
                throw MissingParameterException.create(required);
            }
        }

        private void processArguments(Stack<String> args, Set<Field> required, final String[] originalArgs) throws Exception {
            // arg must be one of:
            // 1. the "--" double dash separating options from positional arguments
            // 1. a stand-alone flag, like "-v" or "--verbose": no value required, must map to boolean or Boolean field
            // 2. a short option followed by an argument, like "-f file" or "-ffile": may map to any type of field
            // 3. a long option followed by an argument, like "-file out.txt" or "-file=out.txt"
            // 3. one or more remaining arguments without any associated options. Must be the last in the list.
            // 4. a combination of stand-alone options, like "-vxr". Equivalent to "-v -x -r", "-v true -x true -r true"
            // 5. a combination of stand-alone options and one option with an argument, like "-vxrffile"

            while (!args.isEmpty()) {
                String arg = args.pop();

                // Double-dash separates options from positional arguments.
                // If found, then interpret the remaining args as positional parameters.
                if ("--".equals(arg)) {
                    processPositionalParameters(args);
                    return; // we are done
                }

                // if we find another command, we are done with the current command
                if (commands.containsKey(arg)) {
                    if (!isHelpRequested && !required.isEmpty()) { // ensure current command portion is valid
                        throw MissingParameterException.create(required);
                    }
                    commands.get(arg).parse(args, originalArgs);
                    return; // remainder done by the command
                }

                // First try to interpret the argument as a single option (as opposed to a compact group of options).
                // A single option may be without option parameters, like "-v" or "--verbose" (a boolean value),
                // or an option may have one or more option parameters.
                // A parameter may be attached to the option.
                boolean paramAttachedToOption = false;
                int separatorIndex = arg.indexOf(separator);
                if (separatorIndex > 0) {
                    String key = arg.substring(0, separatorIndex);
                    // be greedy. Consume the whole arg as an option if possible.
                    if (optionName2Field.containsKey(key) && !optionName2Field.containsKey(arg)) {
                        paramAttachedToOption = true;
                        String optionParam = arg.substring(separatorIndex + separator.length());
                        args.push(optionParam);
                        arg = key;
                    }
                }
                if (optionName2Field.containsKey(arg)) {
                    processStandaloneOption(required, arg, args, paramAttachedToOption);
                }
                // Compact (single-letter) options can be grouped with other options or with an argument.
                // only single-letter options can be combined with other options or with an argument
                else if (arg.length() > 2 && arg.startsWith("-")) {
                    processClusteredShortOptions(required, arg, args);
                }
                // The argument could not be interpreted as an option.
                // We take this to mean that the remainder are positional arguments
                else {
                    args.push(arg);
                    processPositionalParameters(args);
                    return;
                }
            }
        }

        private void processPositionalParameters(Stack<String> args) throws Exception {
//            if (positionalParametersFields.isEmpty() && !args.isEmpty()) {
//                throw new SuperfluousParameterException("Superfluous arguments: " + args); // FIXME
//            }
            processPositionalParameters0(false, args);
        }

        private void processPositionalParameters0(boolean validateOnly, Stack<String> args) throws Exception {
            for (Field positionalParam : positionalParametersFields) {
                Arity indexRange = Arity.valueOf(positionalParam.getAnnotation(Parameters.class).index());
                Stack<String> argsCopy = (Stack<String>) args.clone();
                Collections.reverse(argsCopy);
                if (!indexRange.isVariable) {
                    for (int i = argsCopy.size() - 1; i > indexRange.max; i--) {
                        argsCopy.removeElementAt(i);
                    }
                }
                Collections.reverse(argsCopy);
                for (int i = 0; i < indexRange.min; i++) { argsCopy.pop(); }
                Arity arity = Arity.forParameters(positionalParam);
                assertNoMissingParameters(positionalParam, arity.min, argsCopy);
                if (!validateOnly) {
                    applyOption(positionalParam, Parameters.class, arity, false, argsCopy);
                }
            }
            if (!validateOnly) {
                args.clear(); // clear the stack to prevent processing the elements twice
            }
        }

        private void processStandaloneOption(Set<Field> required,
                                             String arg,
                                             Stack<String> args,
                                             boolean paramAttachedToKey) throws Exception {
            Field field = optionName2Field.get(arg);
            required.remove(field);
            Arity arity = Arity.forOption(field);
            if (paramAttachedToKey) {
                arity.min = Math.max(1, arity.min); // if key=value, minimum arity is at least 1
            }
            applyOption(field, Option.class, arity, paramAttachedToKey, args);
        }

        private void processClusteredShortOptions(Set<Field> required, String arg, Stack<String> args)
                throws Exception {
            String cluster = arg.substring(1);
            do {
                if (cluster.length() > 0 && singleCharOption2Field.containsKey(cluster.charAt(0))) {
                    Field field = singleCharOption2Field.get(cluster.charAt(0));
                    required.remove(field);
                    cluster = cluster.length() > 0 ? cluster.substring(1) : "";
                    boolean paramAttachedToOption = cluster.length() > 0;
                    Arity arity = Arity.forOption(field);
                    if (cluster.startsWith(separator)) {// attached with separator, like -f=FILE or -v=true
                        cluster = cluster.substring(separator.length());
                        arity.min = Math.max(1, arity.min); // if key=value, minimum arity is at least 1
                    }
                    args.push(cluster); // interpret remainder as option parameter (CAUTION: may be empty string!)
                    // arity may be >= 1, or
                    // arity <= 0 && !cluster.startsWith(separator)
                    // e.g., boolean @Option("-v", arity=0, varargs=true); arg "-rvTRUE", remainder cluster="TRUE"
                    int consumed = applyOption(field, Option.class, arity, paramAttachedToOption, args);
                    // only return if cluster (and maybe more) was consumed, otherwise continue do-while loop
                    if (consumed > 0) {
                        return;
                    }
                    cluster = args.pop();
                } else { // cluster is empty || cluster.charAt(0) is not a short option key
                    if (cluster.length() == 0) { // we finished parsing a group of short options like -rxv
                        return; // return normally and parse the next arg
                    }
                    // We get here when the remainder of the cluster group is neither an option,
                    // nor a parameter that the last option could consume.
                    // Consume it and any other remaining parameters as positional parameters.
                    args.push(cluster);
                    processPositionalParameters(args);
                    return;
                }
            } while (true);
        }

        private int applyOption(Field field,
                                Class<?> annotation,
                                Arity arity,
                                boolean valueAttachedToOption,
                                Stack<String> args) throws Exception {
            updateHelpRequested(field);
            if (!args.isEmpty() && args.peek().length() == 0 && !valueAttachedToOption) {
                args.pop(); // throw out empty string we get at the end of a group of clustered short options
            }
            int length = args.size();
            assertNoMissingParameters(field, arity.min, args);

            Class<?> cls = field.getType();
            if (cls.isArray()) {
                return applyValuesToArrayField(field, annotation, arity, args, cls);
            }
            if (Collection.class.isAssignableFrom(cls)) {
                return applyValuesToCollectionField(field, annotation, arity, args, cls);
            }
            return applyValueToSingleValuedField(field, arity, args, cls);
        }

        private int applyValueToSingleValuedField(Field field, Arity arity, Stack<String> args, Class<?> cls) throws Exception {
            boolean noMoreValues = args.isEmpty();
            String value = args.isEmpty() ? null : trim(args.pop()); // unquote the value
            int result = arity.min; // the number or args we need to consume

            // special logic for booleans: BooleanConverter accepts only "true" or "false".
            if ((cls == Boolean.class || cls == Boolean.TYPE) && arity.min <= 0) {

                // boolean option with arity = 0..1 or 0..*: value MAY be a param
                if (arity.max > 0 && ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))) {
                    result = 1;            // if it is a varargs we only consume 1 argument if it is a boolean value
                } else {
                    if (value != null) {
                        args.push(value); // we don't consume the value
                    }
                    value = "true";      // just specifying the option name sets the boolean to true
                }
            }
            if (noMoreValues && value == null) {
                return 0;
            }
            ITypeConverter<?> converter = getTypeConverter(cls);
            Object objValue = tryConvert(field, -1, converter, value, cls);
            field.set(annotatedObject, objValue);
            return result;
        }

        private int applyValuesToArrayField(Field field,
                                            Class<?> annotation,
                                            Arity arity,
                                            Stack<String> args,
                                            Class<?> cls) throws Exception {
            Class<?> type = cls.getComponentType();
            ITypeConverter converter = getTypeConverter(type);
            List<Object> converted = consumeArguments(field, annotation, arity, args, converter, cls);
            Object existing = field.get(annotatedObject);
            int length = existing == null ? 0 : Array.getLength(existing);
            List<Object> newValues = new ArrayList<Object>();
            for (int i = 0; i < length; i++) {
                newValues.add(Array.get(existing, i));
            }
            for (Object obj : converted) {
                if (obj instanceof Collection<?>) {
                    newValues.addAll((Collection<?>) obj);
                } else {
                    newValues.add(obj);
                }
            }
            Object array = Array.newInstance(type, newValues.size());
            field.set(annotatedObject, array);
            for (int i = 0; i < newValues.size(); i++) {
                Array.set(array, i, newValues.get(i));
            }
            return converted.size(); // return how many args were consumed
        }

        @SuppressWarnings("unchecked")
        private int applyValuesToCollectionField(Field field,
                                                 Class<?> annotation,
                                                 Arity arity,
                                                 Stack<String> args,
                                                 Class<?> cls) throws Exception {
            Collection<Object> collection = (Collection<Object>) field.get(annotatedObject);
            Class<?> type = getTypeAttribute(field);
            ITypeConverter converter = getTypeConverter(type);
            List<Object> converted = consumeArguments(field, annotation, arity, args, converter, type);
            if (collection == null) {
                collection = createCollection(cls);
                field.set(annotatedObject, collection);
            }
            for (Object element : converted) {
                if (element instanceof Collection<?>) {
                    collection.addAll((Collection<?>) element);
                } else {
                    collection.add(element);
                }
            }
            return converted.size();
        }

        private List<Object> consumeArguments(Field field,
                                              Class<?> annotation,
                                              Arity arity,
                                              Stack<String> args,
                                              ITypeConverter converter,
                                              Class<?> type) throws Exception {
            List<Object> result = new ArrayList<Object>();
            int index = 0;

            // first do the arity.min mandatory parameters
            for (int i = 0; result.size() < arity.min; i++) {
                index = consumeOneArgument(field, arity, args, converter, type, result, index);
            }
            // now process the varargs if any
            while (result.size() < arity.max && !args.isEmpty()) {
                if (annotation != Parameters.class) {
                    if (commands.containsKey(args.peek()) || isOption(args.peek())) {
                        return result;
                    }
                }
                index = consumeOneArgument(field, arity, args, converter, type, result, index);
            }
            return result;
        }

        private int consumeOneArgument(Field field,
                                       Arity arity,
                                       Stack<String> args,
                                       ITypeConverter converter,
                                       Class<?> type,
                                       List<Object> result, int index) throws Exception {
            String[] values = split(trim(args.pop()), field);

            // ensure we don't process more than arity.max (as result of splitting args)
            int max = Math.min(arity.max - result.size(), values.length);
            for (int j = 0; j < max; j++) {
                result.add(tryConvert(field, index, converter, values[j], type));
            }
            // if this option cannot consume values because of its arity.max,
            // then push them back on the stack (they are likely processed as positional parameters)
            for (int j = values.length - 1; j >= max; j--) {
                args.push(values[j]);
            }
            index++;
            return index;
        }

        private String[] split(String value, Field field) {
            if (field.isAnnotationPresent(Option.class)) {
                String regex = field.getAnnotation(Option.class).split();
                return regex.length() == 0 ? new String[] {value} : value.split(regex);
            }
            if (field.isAnnotationPresent(Parameters.class)) {
                String regex = field.getAnnotation(Parameters.class).split();
                return regex.length() == 0 ? new String[] {value} : value.split(regex);
            }
            return new String[] {value};
        }

        /**
         * Called when parsing varargs parameters for a multi-value option.
         * When an option is encountered, the remainder should not be interpreted as vararg elements.
         * @param arg the string to determine whether it is an option or not
         * @return true if it is an option, false otherwise
         */
        private boolean isOption(String arg) {
            if ("--".equals(arg)) {
                return true;
            }
            // not just arg prefix: we may be in the middle of parsing -xrvfFILE
            if (optionName2Field.containsKey(arg)) { // -v or -f or --file (not attached to param or other option)
                return true;
            }
            int separatorIndex = arg.indexOf(separator);
            if (separatorIndex > 0) { // -f=FILE or --file==FILE (attached to param via separator)
                if (optionName2Field.containsKey(arg.substring(0, separatorIndex))) {
                    return true;
                }
            }
            return (arg.length() > 2 && arg.startsWith("-") && singleCharOption2Field.containsKey(arg.charAt(1)));
        }
        private Object tryConvert(Field field, int index, ITypeConverter<?> converter, String value, Class<?> type)
                throws Exception {
            try {
                return converter.convert(value);
            } catch (ParameterException ex) {
                throw new ParameterException(ex.getMessage() + optionDescription(" for ", field, index));
            } catch (Exception other) {
                String desc = optionDescription(" for ", field, index);
                throw new ParameterException("Could not convert '" + value + "' to " + type.getSimpleName() + desc, other);
            }
        }

        private String optionDescription(String prefix, Field field, int index) {
            String desc = "";
            if (field.isAnnotationPresent(Option.class)) {
                desc = prefix + "option '" + field.getAnnotation(Option.class).names()[0] + "'";
                if (index >= 0) {
                    desc += " parameter[" + index + "]";
                }
            } else if (field.isAnnotationPresent(Parameters.class)) {
                Arity indexRange = Arity.forParameters(field);
                desc = prefix + "positional parameters[" + indexRange + "]";
            }
            return desc;
        }

        private Class<?> getTypeAttribute(Field field) {
            if (field.isAnnotationPresent(Parameters.class)) {
                return field.getAnnotation(Parameters.class).type();
            } else if (field.isAnnotationPresent(Option.class)) {
                return field.getAnnotation(Option.class).type();
            }
            throw new IllegalStateException(field + " has neither @Parameters nor @Option annotation");
        }

        private void updateHelpRequested(Field field) {
            if (field.isAnnotationPresent(Option.class)) {
                isHelpRequested |= field.getAnnotation(Option.class).help();
            }
        }

        @SuppressWarnings("unchecked")
        private Collection<Object> createCollection(Class<?> collectionClass) throws Exception {
            if (collectionClass.isInterface()) {
                if (List.class.isAssignableFrom(collectionClass)) {
                    return new ArrayList<Object>();
                } else if (SortedSet.class.isAssignableFrom(collectionClass)) {
                    return new TreeSet<Object>();
                } else if (Set.class.isAssignableFrom(collectionClass)) {
                    return new HashSet<Object>();
                } else if (Queue.class.isAssignableFrom(collectionClass)) {
                    return new LinkedList<Object>(); // ArrayDeque is only available since 1.6
                }
                return new ArrayList<Object>();
            }
            // custom Collection implementation class must have default constructor
            return (Collection<Object>) collectionClass.newInstance();
        }

        private ITypeConverter<?> getTypeConverter(final Class<?> type) {
            ITypeConverter<?> result = converterRegistry.get(type);
            if (result != null) {
                return result;
            }
            if (type.isEnum()) {
                return new ITypeConverter<Object>() {
                    @SuppressWarnings("unchecked")
                    public Object convert(String value) throws Exception {
                        return Enum.valueOf((Class<Enum>) type, value);
                    }
                };
            }
            throw new MissingTypeConverterException("No TypeConverter registered for " + type.getName());
        }

        private void assertNoMissingParameters(Field field, int arity, Stack<String> args) {
            if (arity > args.size()) {
                int actualSize = 0;
                Stack<String> copy = (Stack<String>) args.clone();
                while (!copy.isEmpty()) {
                    actualSize += split(copy.pop(), field).length;
                    if (actualSize >= arity) { return; }
                }
                if (arity == 1) {
                    throw new MissingParameterException("Missing required parameter for " +
                            optionDescription("", field, 0));
                }
                throw new MissingParameterException(optionDescription("", field, 0) +
                        " requires at least " + arity + " parameters, but only " + args.size() + " were specified.");
            }
        }

        private String trim(String value) {
            return unquote(value);
        }

        private String unquote(String value) {
            return value == null
                    ? null
                    : (value.length() > 1 && value.startsWith("\"") && value.endsWith("\""))
                        ? value.substring(1, value.length() - 1)
                        : value;
        }
    }

    /**
     * Inner class to group the built-in {@link ITypeConverter} implementations.
     */
    private static class BuiltIn {
        static class StringConverter implements ITypeConverter<String> {
            public String convert(String value) { return value; }
        }
        static class StringBuilderConverter implements ITypeConverter<StringBuilder> {
            public StringBuilder convert(String value) { return new StringBuilder(value); }
        }
        static class CharSequenceConverter implements ITypeConverter<CharSequence> {
            public String convert(String value) { return value; }
        }
        /** Converts text to a {@code Byte} by delegating to {@link Byte#valueOf(String)}.*/
        static class ByteConverter implements ITypeConverter<Byte> {
            public Byte convert(String value) { return Byte.valueOf(value); }
        }
        /** Converts {@code "true"} or {@code "false"} to a {@code Boolean}. Other values result in a ParameterException.*/
        static class BooleanConverter implements ITypeConverter<Boolean> {
            public Boolean convert(String value) {
                if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                    return Boolean.parseBoolean(value);
                } else {
                    throw new ParameterException("'" + value + "' is not a boolean");
                }
            }
        }
        static class CharacterConverter implements ITypeConverter<Character> {
            public Character convert(String value) {
                if (value.length() > 1) {
                    throw new ParameterException("'" + value + "' is not a single character");
                }
                return value.charAt(0);
            }
        }
        /** Converts text to a {@code Short} by delegating to {@link Short#valueOf(String)}.*/
        static class ShortConverter implements ITypeConverter<Short> {
            public Short convert(String value) { return Short.valueOf(value); }
        }
        /** Converts text to an {@code Integer} by delegating to {@link Integer#valueOf(String)}.*/
        static class IntegerConverter implements ITypeConverter<Integer> {
            public Integer convert(String value) { return Integer.valueOf(value); }
        }
        /** Converts text to a {@code Long} by delegating to {@link Long#valueOf(String)}.*/
        static class LongConverter implements ITypeConverter<Long> {
            public Long convert(String value) { return Long.valueOf(value); }
        }
        static class FloatConverter implements ITypeConverter<Float> {
            public Float convert(String value) { return Float.valueOf(value); }
        }
        static class DoubleConverter implements ITypeConverter<Double> {
            public Double convert(String value) { return Double.valueOf(value); }
        }
        static class FileConverter implements ITypeConverter<File> {
            public File convert(String value) { return new File(value); }
        }
        static class URLConverter implements ITypeConverter<URL> {
            public URL convert(String value) throws MalformedURLException { return new URL(value); }
        }
        static class URIConverter implements ITypeConverter<URI> {
            public URI convert(String value) throws URISyntaxException { return new URI(value); }
        }
        /** Converts text in {@code yyyy-mm-dd} format to a {@code java.util.Date}. ParameterException on failure. */
        static class ISO8601DateConverter implements ITypeConverter<Date> {
            public Date convert(String value) {
                try {
                    return new SimpleDateFormat("yyyy-MM-dd").parse(value);
                } catch (ParseException e) {
                    throw new ParameterException("'" + value + "' is not a yyyy-MM-dd date");
                }
            }
        }
        /** Converts text in any of the following formats to a {@code java.sql.Time}: {@code HH:mm}, {@code HH:mm:ss},
         * {@code HH:mm:ss.SSS}, {@code HH:mm:ss,SSS}. Other formats result in a ParameterException. */
        static class ISO8601TimeConverter implements ITypeConverter<Time> {
            public Time convert(String value) {
                try {
                    if (value.length() <= 5) {
                        return new Time(new SimpleDateFormat("HH:mm").parse(value).getTime());
                    } else if (value.length() <= 8) {
                        return new Time(new SimpleDateFormat("HH:mm:ss").parse(value).getTime());
                    } else if (value.length() <= 12) {
                        try {
                            return new Time(new SimpleDateFormat("HH:mm:ss.SSS").parse(value).getTime());
                        } catch (ParseException e2) {
                            return new Time(new SimpleDateFormat("HH:mm:ss,SSS").parse(value).getTime());
                        }
                    }
                } catch (ParseException ignored) {
                    // ignored because we throw a ParameterException below
                }
                throw new ParameterException("'" + value + "' is not a HH:mm[:ss[.SSS]] time");
            }
        }
        static class BigDecimalConverter implements ITypeConverter<BigDecimal> {
            public BigDecimal convert(String value) { return new BigDecimal(value); }
        }
        static class BigIntegerConverter implements ITypeConverter<BigInteger> {
            public BigInteger convert(String value) { return new BigInteger(value); }
        }
        static class CharsetConverter implements ITypeConverter<Charset> {
            public Charset convert(String s) { return Charset.forName(s); }
        }
        /** Converts text to a {@code InetAddress} by delegating to {@link InetAddress#getByName(String)}. */
        static class InetAddressConverter implements ITypeConverter<InetAddress> {
            public InetAddress convert(String s) throws Exception { return InetAddress.getByName(s); }
        }
        static class PatternConverter implements ITypeConverter<Pattern> {
            public Pattern convert(String s) { return Pattern.compile(s); }
        }
        static class UUIDConverter implements ITypeConverter<UUID> {
            public UUID convert(String s) throws Exception { return UUID.fromString(s); }
        }
    }

    /**
     * A collection of methods and inner classes that provide fine-grained control over the contents and layout of
     * the usage help message to display to end users when help is requested or invalid input values were specified.
     */
    public static class Help {
        /** Constant String holding the default program name: {@value} */
        protected static final String DEFAULT_COMMAND_NAME = "<main class>";

        private final Object annotatedObject;
        private final Map<String, Help> commands = new LinkedHashMap<String, Help>();
        final ColorScheme colorScheme;

        /** LinkedHashMap mapping {@link Option} instances to the {@code Field} they annotate, in declaration order. */
        //public final Map<Option, Field> option2Field = new LinkedHashMap<Option, Field>();
        public List<Field> optionFields = new ArrayList<Field>();

        /** The {@code Fields} annotated with {@link Parameters}, or an empty list if no such field exists. */
        public List<Field> positionalParametersFields = new ArrayList<Field>();

        /** The String to use as the separator between options and option parameters. {@code "="} by default,
         * initialized from {@link Command#separator()} if defined. */
        public String separator;

        /** The String to use as the program name in the synopsis line of the help message.
         * {@link #DEFAULT_COMMAND_NAME} by default, initialized from {@link Command#name()} if defined. */
        public String commandName = DEFAULT_COMMAND_NAME;

        /** Optional text lines to use as the description of the help message, displayed between the synopsis and the
         * options list. Initialized from {@link Command#description()} if the {@code Command} annotation is present,
         * otherwise this is an empty array and the help message has no description.
         * Applications may programmatically set this field to create a custom help message. */
        public String[] description = {};

        /** Optional custom synopsis lines to use instead of the auto-generated synopsis.
         * Initialized from {@link Command#customSynopsis()} if the {@code Command} annotation is present,
         * otherwise this is an empty array and the synopsis is generated.
         * Applications may programmatically set this field to create a custom help message. */
        public String[] customSynopsis = {};

        /** Optional header lines displayed at the top of the help message. For sub-commands, the first header line is
         * displayed in the list of commands. Values are initialized from {@link Command#header()}
         * if the {@code Command} annotation is present, otherwise this is an empty array and the help message has no
         * header. Applications may programmatically set this field to create a custom help message. */
        public String[] header = {};

        /** Optional footer text lines displayed at the bottom of the help message. Initialized from
         * {@link Command#footer()} if the {@code Command} annotation is present, otherwise this is an empty array and
         * the help message has no footer.
         * Applications may programmatically set this field to create a custom help message. */
        public String[] footer = {};

        /** Option and positional parameter value label renderer used for the synopsis line(s) and the option list. */
        public IParamLabelRenderer parameterLabelRenderer;

        /** If {@code true}, the synopsis line(s) will show an abbreviated synopsis without detailed option names. */
        public Boolean abbreviateSynopsis;

        /** If {@code true}, the options list is sorted alphabetically. */
        public Boolean sortOptions;

        /** If {@code true}, the options list will show default values for all options except booleans. */
        public Boolean showDefaultValues;

        /** Character used to prefix required options in the options list. */
        public Character requiredOptionMarker;

        /** Optional heading preceding the header section. Initialized from {@link Command#headerHeading()}, or null. */
        public String headerHeading;

        /** Optional heading preceding the synopsis. Initialized from {@link Command#synopsisHeading()}, {@code "Usage: "} by default. */
        public String synopsisHeading;

        /** Optional heading preceding the description section. Initialized from {@link Command#descriptionHeading()}, or null. */
        public String descriptionHeading;

        /** Optional heading preceding the parameter list. Initialized from {@link Command#parameterListHeading()}, or null. */
        public String parameterListHeading;

        /** Optional heading preceding the options list. Initialized from {@link Command#optionListHeading()}, or null. */
        public String optionListHeading;

        /** Optional heading preceding the command list. Initialized from {@link Command#commandListHeading()}. {@code "Commands:%n"} by default. */
        public String commandListHeading;

        /** Optional heading preceding the footer section. Initialized from {@link Command#footerHeading()}, or null. */
        public String footerHeading;

        /** Constructs a new {@code Help} instance with a default color scheme, initialized from annotatations
         * on the specified class and superclasses. */
        public Help(Object annotatedObject) {
            this(annotatedObject, defaultColorScheme());
        }

        /** Constructs a new {@code Help} instance with the specified color scheme, initialized from annotatations
         * on the specified class and superclasses. */
        public Help(Object annotatedObject, ColorScheme colorScheme) {
            this.annotatedObject = Assert.notNull(annotatedObject, "annotatedObject");
            this.colorScheme = Assert.notNull(colorScheme, "colorScheme").applySystemProperties();
            Class<?> cls = annotatedObject.getClass();
            while (cls != null) {
                for (Field field : cls.getDeclaredFields()) {
                    field.setAccessible(true);
                    if (field.isAnnotationPresent(Option.class)) {
                        Option option = field.getAnnotation(Option.class);
                        if (!option.hidden()) { // hidden options should not appear in usage help
                            // TODO remember longest concatenated option string length (issue #45)
                            optionFields.add(field);
                        }
                    }
                    if (field.isAnnotationPresent(Parameters.class)) {
                        positionalParametersFields.add(field);
                    }
                }
                // superclass values should not overwrite values if both class and superclass have a @Command annotation
                if (cls.isAnnotationPresent(Command.class)) {
                    Command command = cls.getAnnotation(Command.class);
                    if (DEFAULT_COMMAND_NAME.equals(commandName)) {
                        commandName = command.name();
                    }
                    separator = (separator == null) ? command.separator() : separator;
                    abbreviateSynopsis = (abbreviateSynopsis == null) ? command.abbreviateSynopsis() : abbreviateSynopsis;
                    sortOptions = (sortOptions == null) ? command.sortOptions() : sortOptions;
                    requiredOptionMarker = (requiredOptionMarker == null) ? command.requiredOptionMarker() : requiredOptionMarker;
                    showDefaultValues = (showDefaultValues == null) ? command.showDefaultValues() : showDefaultValues;
                    customSynopsis = empty(customSynopsis) ? command.customSynopsis() : customSynopsis;
                    description = empty(description) ? command.description() : description;
                    header = empty(header) ? command.header() : header;
                    footer = empty(footer) ? command.footer() : footer;
                    headerHeading = headerHeading == null ? command.headerHeading() : headerHeading;
                    synopsisHeading = synopsisHeading == null ? command.synopsisHeading() : synopsisHeading;
                    descriptionHeading = descriptionHeading == null ? command.descriptionHeading() : descriptionHeading;
                    parameterListHeading = parameterListHeading == null ? command.parameterListHeading() : parameterListHeading;
                    optionListHeading = optionListHeading == null ? command.optionListHeading() : optionListHeading;
                    commandListHeading = commandListHeading == null ? command.commandListHeading() : commandListHeading;
                    footerHeading = footerHeading == null ? command.footerHeading() : footerHeading;
                }
                cls = cls.getSuperclass();
            }
            sortOptions =          (sortOptions == null)          ? true : sortOptions;
            abbreviateSynopsis =   (abbreviateSynopsis == null)   ? false : abbreviateSynopsis;
            requiredOptionMarker = (requiredOptionMarker == null) ? ' ' : requiredOptionMarker;
            showDefaultValues =    (showDefaultValues == null)    ? false : showDefaultValues;
            separator =            (separator == null)            ? "=" : separator;
            parameterLabelRenderer = new DefaultParamLabelRenderer(separator);
            synopsisHeading =      (synopsisHeading == null)      ? "Usage: " : synopsisHeading;
            commandListHeading =   (commandListHeading == null)   ? "Commands:%n" : commandListHeading;
        }

        public Help addAllCommands(Map<String, Object> commands) {
            if (commands != null) {
                for (Map.Entry<String, Object> entry : commands.entrySet()) {
                    addCommand(entry.getKey(), entry.getValue());
                }
            }
            return this;
        }

        public Help addCommand(String command, Object annotatedObject) {
            commands.put(command, new Help(annotatedObject));
            return this;
        }

        public String synopsis() {
            if (!empty(customSynopsis)) { return customSynopsis(); }
            return abbreviateSynopsis ? abbreviatedSynopsis()
                    : detailedSynopsis(createShortOptionArityAndNameComparator(), true);
        }

        /** Generates a generic synopsis like {@code <command name> [OPTIONS] [PARAM1 [PARAM2]...]}, omitting parts
         * that don't apply to the command (e.g., does not show [OPTIONS] if the command has no options). */
        public String abbreviatedSynopsis() {
            StringBuilder sb = new StringBuilder();
            if (!optionFields.isEmpty()) { // only show if annotated object actually has options
                sb.append(" [OPTIONS]");
            }
            // sb.append(" [--] "); // implied
            for (Field positionalParam : positionalParametersFields) {
                if (!positionalParam.getAnnotation(Parameters.class).hidden()) {
                    sb.append(' ').append(parameterLabelRenderer.renderParameterLabel(positionalParam, colorScheme.parameterStyles));
                }
            }
            return colorScheme.commandText(commandName).toString()
                    + (sb.toString()) + System.getProperty("line.separator");
        }

        /** Generates a detailed synopsis message showing all options and parameters. Follows the unix convention of
         * showing optional options and parameters in square brackets ({@code [ ]}). */
        public String detailedSynopsis(Comparator<Field> optionSort, boolean clusterBooleanOptions) {
            Text optionText = new Text(0);
            List<Field> fields = new ArrayList<Field>(optionFields); // iterate in declaration order
            if (optionSort != null) {
                Collections.sort(fields, optionSort);// iterate in specified sort order
            }
            if (clusterBooleanOptions) { // cluster all short boolean options into a single string
                List<Field> booleanOptions = new ArrayList<Field>();
                StringBuilder clusteredRequired = new StringBuilder("-");
                StringBuilder clusteredOptional = new StringBuilder("-");
                for (Field field : fields) {
                    if (field.getType() == boolean.class || field.getType() == Boolean.class) {
                        Option option = field.getAnnotation(Option.class);
                        String shortestName = ShortestFirst.sort(option.names())[0];
                        if (shortestName.length() == 2 && shortestName.startsWith("-")) {
                            booleanOptions.add(field);
                            if (option.required()) {
                                clusteredRequired.append(shortestName.substring(1));
                            } else {
                                clusteredOptional.append(shortestName.substring(1));
                            }
                        }
                    }
                }
                fields.removeAll(booleanOptions);
                if (clusteredRequired.length() > 1) { // initial length was 1
                    optionText = optionText.append(" ").append(colorScheme.optionText(clusteredRequired.toString()));
                }
                if (clusteredOptional.length() > 1) { // initial length was 1
                    optionText = optionText.append(" [").append(colorScheme.optionText(clusteredOptional.toString())).append("]");
                }
            }
            for (Field field : fields) {
                Option option = field.getAnnotation(Option.class);
                if (!option.hidden()) {
                    //sb.append(" ");
                    optionText = optionText.append(option.required() ? " " : " [");

                    String optionNames = ShortestFirst.sort(option.names())[0];
                    optionText = optionText.append(colorScheme.optionText(optionNames));

                    Text optionParamText = parameterLabelRenderer.renderParameterLabel(field, colorScheme.optionParamStyles);
                    optionText = optionText.append(optionParamText);
                    if (!option.required()) {
                        optionText = optionText.append("]");
                    }
                }
            }
            for (Field positionalParam : positionalParametersFields) {
                if (!positionalParam.getAnnotation(Parameters.class).hidden()) {
                    optionText = optionText.append(" ");
                    Text label = parameterLabelRenderer.renderParameterLabel(positionalParam, colorScheme.parameterStyles);
                    optionText = optionText.append(label);
                }
            }
            TextTable textTable = new TextTable(commandName.length(), 80 - commandName.length());
            textTable.indentWrappedLines = 1; // don't worry about first line: options (2nd column) always start with a space
            textTable.addRowValues(new Text[] {colorScheme.commandText(commandName), optionText});
            return textTable.toString();
        }
        /**
         * <p>Returns a description of the {@linkplain Option options} supported by the application.
         * This implementation {@linkplain #createShortOptionNameComparator() sorts options alphabetically}, and shows
         * only the {@linkplain Option#hidden() non-hidden} options in a {@linkplain TextTable tabular format}
         * using the {@linkplain #createDefaultOptionRenderer() default renderer} and {@linkplain Layout default layout}.</p>
         * @return the fully formatted option list
         * @see #optionList(Layout, Comparator, IParamLabelRenderer)
         */
        public String optionList() {
            Comparator<Field> sortOrder = sortOptions == null || sortOptions.booleanValue()
                    ? createShortOptionNameComparator()
                    : null;
            return optionList(createDefaultLayout(), sortOrder, createDefaultParamLabelRenderer());
        }

        /** Sorts all {@code Options} with the specified {@code comparator} (if the comparator is non-{@code null}),
         * then {@linkplain Layout#addOption(Field, CommandLine.Help.IParamLabelRenderer) adds} all non-hidden options to the
         * specified TextTable and returns the result of TextTable.toString().
         * @param layout responsible for rendering the option list
         * @param optionSort determines in what order {@code Options} should be listed. Declared order if {@code null}
         * @param valueLabelRenderer used for options with a parameter
         * @return the fully formatted option list
         */
        public String optionList(Layout layout, Comparator<Field> optionSort, IParamLabelRenderer valueLabelRenderer) {
            List<Field> fields = new ArrayList<Field>(optionFields); // options are stored in order of declaration
            if (optionSort != null) {
                Collections.sort(fields, optionSort); // default: sort options ABC
            }
            layout.addOptions(fields, valueLabelRenderer);
            return layout.toString();
        }
        public String parameterList() {
            return parameterList(createDefaultLayout(), createMinimalParamLabelRenderer());
        }
        public String parameterList(Layout layout, IParamLabelRenderer paramLabelRenderer) {
            layout.addPositionalParameters(positionalParametersFields, paramLabelRenderer);
            return layout.toString();
        }

        /** Appends each of the specified values plus the specified separator to the specified StringBuilder and returns it. */
        public static StringBuilder join(String[] values, StringBuilder sb, Object... params) {
            if (values != null) {
                TextTable table = new TextTable(80);
                table.indentWrappedLines = 0;
                for (String summaryLine : values) {
                    table.addRowValues(new Text(String.format(summaryLine, params)));
                }
                table.toString(sb);
            }
            return sb;
        }
        /** Returns command custom synopsis as a string. A custom synopsis can be zero or more lines, and can be
         * specified declaratively with the {@link Command#customSynopsis()} annotation attribute or programmatically
         * by setting the Help instance's {@link Help#customSynopsis} field.
         * @param params Arguments referenced by the format specifiers in the synopsis strings
         * @return the custom synopsis lines combined into a single String (which may be empty)
         */
        public String customSynopsis(Object... params) {
            return join(customSynopsis, new StringBuilder(), params).toString();
        }
        /** Returns command description text as a string. Description text can be zero or more lines, and can be specified
         * declaratively with the {@link Command#description()} annotation attribute or programmatically by
         * setting the Help instance's {@link Help#description} field.
         * @param params Arguments referenced by the format specifiers in the description strings
         * @return the description lines combined into a single String (which may be empty)
         */
        public String description(Object... params) {
            return join(description, new StringBuilder(), params).toString();
        }
        /** Returns the command header text as a string. Header text can be zero or more lines, and can be specified
         * declaratively with the {@link Command#header()} annotation attribute or programmatically by
         * setting the Help instance's {@link Help#header} field.
         * @param params Arguments referenced by the format specifiers in the header strings
         * @return the header lines combined into a single String (which may be empty)
         */
        public String header(Object... params) {
            return join(header, new StringBuilder(), params).toString();
        }
        /** Returns command footer text as a string. Footer text can be zero or more lines, and can be specified
         * declaratively with the {@link Command#footer()} annotation attribute or programmatically by
         * setting the Help instance's {@link Help#footer} field.
         * @param params Arguments referenced by the format specifiers in the footer strings
         * @return the footer lines combined into a single String (which may be empty)
         */
        public String footer(Object... params) {
            return join(footer, new StringBuilder(), params).toString();
        }

        /** Returns the text displayed before the header text; the result of {@code String.format(headerHeading, params)}. */
        public String headerHeading(Object... params) {
            return new Text(format(headerHeading, params)).toString();
        }
        /** Returns the text displayed before the synopsis text; the result of {@code String.format(synopsisHeading, params)}. */
        public String synopsisHeading(Object... params) {
            return new Text(format(synopsisHeading, params)).toString();
        }

        /** Returns the text displayed before the description text; an empty string if there is no description,
         * otherwise the result of {@code String.format(descriptionHeading, params)}. */
        public String descriptionHeading(Object... params) {
            return empty(descriptionHeading) ? "" : new Text(format(descriptionHeading, params)).toString();
        }

        /** Returns the text displayed before the positional parameter list; an empty string if there are no positional
         * parameters, otherwise the result of {@code String.format(parameterListHeading, params)}. */
        public String parameterListHeading(Object... params) {
            return positionalParametersFields.isEmpty() ? "" : new Text(format(parameterListHeading, params)).toString();
        }

        /** Returns the text displayed before the option list; an empty string if there are no options,
         * otherwise the result of {@code String.format(optionListHeading, params)}. */
        public String optionListHeading(Object... params) {
            return optionFields.isEmpty() ? "" : new Text(format(optionListHeading, params)).toString();
        }

        /** Returns the text displayed before the command list; an empty string if there are no commands,
         * otherwise the result of {@code String.format(commandListHeading, params)}. */
        public String commandListHeading(Object... params) {
            return commands.isEmpty() ? "" : new Text(format(commandListHeading, params)).toString();
        }

        /** Returns the text displayed before the footer text; the result of {@code String.format(footerHeading, params)}. */
        public String footerHeading(Object... params) {
            return new Text(format(footerHeading, params)).toString();
        }
        private String format(String formatString,  Object[] params) {
            return formatString == null ? "" : String.format(formatString, params);
        }
        /** Returns a 2-column list with command names and the first line of their header or (if absent) description. */
        public String commandList() {
            if (commands.isEmpty()) { return ""; }
            int commandLength = maxLength(commands.keySet());
            Help.TextTable textTable = new Help.TextTable(
                    new Help.Column(commandLength + 2, 2, Help.Column.Overflow.SPAN),
                    new Help.Column(80 - (commandLength + 2), 2, Help.Column.Overflow.WRAP));

            for (Map.Entry<String, Help> entry : commands.entrySet()) {
                Help command = entry.getValue();
                String header = command.header != null && command.header.length > 0 ? command.header[0]
                        : (command.description != null && command.description.length > 0 ? command.description[0] : "");
                textTable.addRowValues(colorScheme.commandText(entry.getKey()), new Text(header));
            }
            return textTable.toString();
        }
        private static int maxLength(Collection<String> any) {
            List<String> strings = new ArrayList<String>(any);
            Collections.sort(strings, Collections.reverseOrder(Help.shortestFirst()));
            return strings.get(0).length();
        }
        private static String join(String[] names, int offset, int length, String separator) {
            if (names == null) { return ""; }
            StringBuilder result = new StringBuilder();
            for (int i = offset; i < offset + length; i++) {
                result.append((i > offset) ? separator : "").append(names[i]);
            }
            return result.toString();
        }

        /** Returns a {@code Layout} instance configured with the user preferences captured in this Help instance. */
        public Layout createDefaultLayout() {
            return new Layout(colorScheme, new TextTable(), createDefaultOptionRenderer(), createDefaultParameterRenderer());
        }
        /** Returns a new default OptionRenderer which converts {@link Option Options} to five columns of text to match
         *  the default {@linkplain TextTable TextTable} column layout. The first row of values looks like this:
         * <ol>
         * <li>the required option marker</li>
         * <li>2-character short option name (or empty string if no short option exists)</li>
         * <li>comma separator (only if both short option and long option exist, empty string otherwise)</li>
         * <li>comma-separated string with long option name(s)</li>
         * <li>first element of the {@link Option#description()} array</li>
         * </ol>
         * <p>Following this, there will be one row for each of the remaining elements of the {@link
         *   Option#description()} array, and these rows look like {@code {"", "", "", "", option.description()[i]}}.</p>
         * <p>If configured, this option renderer adds an additional row to display the default field value.</p>
         */
        public IOptionRenderer createDefaultOptionRenderer() {
            DefaultOptionRenderer result = new DefaultOptionRenderer();
            result.requiredMarker = String.valueOf(requiredOptionMarker);
            if (showDefaultValues != null && showDefaultValues.booleanValue()) {
                result.annotatedObject = this.annotatedObject;
            }
            return result;
        }
        /** Returns a new minimal OptionRenderer which converts {@link Option Options} to a single row with two columns
         * of text: an option name and a description. If multiple names or descriptions exist, the first value is used. */
        public static IOptionRenderer createMinimalOptionRenderer() {
            return new MinimalOptionRenderer();
        }
        /** Returns a new default ParameterRenderer which converts {@link Parameters Parameters} to four columns of
         * text to match the default {@linkplain TextTable TextTable} column layout. The first row of values looks like this:
         * <ol>
         * <li>empty string </li>
         * <li>empty string </li>
         * <li>parameter(s) label as rendered by the {@link IParamLabelRenderer}</li>
         * <li>first element of the {@link Parameters#description()} array</li>
         * </ol>
         * <p>Following this, there will be one row for each of the remaining elements of the {@link
         *   Parameters#description()} array, and these rows look like {@code {"", "", "", param.description()[i]}}.</p>
         * <p>If configured, this parameter renderer adds an additional row to display the default field value.</p>
         */
        public IParameterRenderer createDefaultParameterRenderer() {
            DefaultParameterRenderer result = new DefaultParameterRenderer();
            result.requiredMarker = String.valueOf(requiredOptionMarker);
            return result;
        }
        /** Returns a new minimal ParameterRenderer which converts {@link Parameters Parameters} to a single row with
         * two columns of text: an option name and a description. If multiple descriptions exist, the first value is used. */
        public static IParameterRenderer createMinimalParameterRenderer() {
            return new MinimalParameterRenderer();
        }
        /** Returns a value renderer that returns the {@code paramLabel} if defined or the field name otherwise. */
        public static IParamLabelRenderer createMinimalParamLabelRenderer() {
            return new IParamLabelRenderer() {
                public Text renderParameterLabel(Field field, List<IStyle> styles) {
                    String paramLabel = null;
                    Parameters parameters = field.getAnnotation(Parameters.class);
                    if (parameters != null) {
                        paramLabel = parameters.paramLabel();
                    } else {
                        paramLabel = field.isAnnotationPresent(Option.class) ? field.getAnnotation(Option.class).paramLabel() : null;
                    }
                    String text = paramLabel == null || paramLabel.length() == 0 ? field.getName() : paramLabel;
                    return Text.apply(text, styles);
                }
                public String separator() { return ""; }
            };
        }
        /** Returns a new default value renderer that separates option parameters from their {@linkplain Option
         * options} with the specified separator string, surrounds optional parameters with {@code '['} and {@code ']'}
         * characters and uses ellipses ("...") to indicate that any number of a parameter are allowed.
         */
        public IParamLabelRenderer createDefaultParamLabelRenderer() {
            return new DefaultParamLabelRenderer(separator);
        }
        /** Sorts Fields annotated with {@code Option} by their option name in case-insensitive alphabetic order. If an
         * Option has multiple names, the shortest name is used for the sorting. Help options follow non-help options. */
        public static Comparator<Field> createShortOptionNameComparator() {
            return new SortByShortestOptionNameAlphabetically();
        }
        /** Sorts Fields annotated with {@code Option} by their option {@linkplain Arity#max max arity} first, by
         * {@linkplain Arity#min min arity} next, and by {@linkplain #createShortOptionNameComparator() option name} last. */
        public static Comparator<Field> createShortOptionArityAndNameComparator() {
            return new SortByOptionArityAndNameAlphabetically();
        }
        /** Sorts short strings before longer strings. */
        public static Comparator<String> shortestFirst() {
            return new ShortestFirst();
        }

        /** When customizing online help for {@link Option Option} details, a custom {@code IOptionRenderer} can be
         * used to create textual representation of an Option in a tabular format: one or more rows, each containing
         * one or more columns. The {@link Layout Layout} is responsible for placing these text values in the
         * {@link TextTable TextTable}. */
        public interface IOptionRenderer {
            /**
             * Returns a text representation of the specified Option and the Field that captures the option value.
             * @param option the command line option to show online usage help for
             * @param field the field that will hold the value for the command line option
             * @param parameterLabelRenderer responsible for rendering option parameters to text
             * @param scheme color scheme for applying ansi color styles to options and option parameters
             * @return a 2-dimensional array of text values: one or more rows, each containing one or more columns
             */
            Text[][] render(Option option, Field field, IParamLabelRenderer parameterLabelRenderer, ColorScheme scheme);
        }
        /** The DefaultOptionRenderer converts {@link Option Options} to five columns of text to match the default
         * {@linkplain TextTable TextTable} column layout. The first row of values looks like this:
         * <ol>
         * <li>the required option marker (if the option is required)</li>
         * <li>2-character short option name (or empty string if no short option exists)</li>
         * <li>comma separator (only if both short option and long option exist, empty string otherwise)</li>
         * <li>comma-separated string with long option name(s)</li>
         * <li>first element of the {@link Option#description()} array</li>
         * </ol>
         * <p>Following this, there will be one row for each of the remaining elements of the {@link
         *   Option#description()} array, and these rows look like {@code {"", "", "", option.description()[i]}}.</p>
         */
        static class DefaultOptionRenderer implements IOptionRenderer {
            public String requiredMarker = " ";
            public Object annotatedObject;
            public Text[][] render(Option option, Field field, IParamLabelRenderer paramLabelRenderer, ColorScheme scheme) {
                String[] names = new ShortestFirst().sort(option.names());
                int shortOptionCount = names[0].length() == 2 ? 1 : 0;
                String shortOption = shortOptionCount > 0 ? names[0] : "";
                Text paramLabelText = paramLabelRenderer.renderParameterLabel(field, scheme.optionParamStyles);
                String longOption = join(names, shortOptionCount, names.length - shortOptionCount, ", ");
                String sep = shortOptionCount > 0 && names.length > 1 ? "," : "";

                // if no long option, fill in the space between the short option name and the param label value
                if (paramLabelText.length > 0 && longOption.length() == 0) {
                    sep = paramLabelRenderer.separator();
                    paramLabelText = paramLabelText.substring(sep.length());
                }
                Text longOptionText = scheme.optionText(longOption);
                longOptionText = longOptionText.append(paramLabelText);
                String requiredOption = option.required() ? requiredMarker : "";

                boolean showDefault = annotatedObject != null && !option.help() && !isBoolean(field.getType());
                Object defaultValue = null;
                try {
                    defaultValue = field.get(annotatedObject);
                    if (defaultValue != null && field.getType().isArray()) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < Array.getLength(defaultValue); i++) {
                            sb.append(i > 0 ? ", " : "").append(Array.get(defaultValue, i));
                        }
                        defaultValue = sb.insert(0, "[").append("]").toString();
                    }
                } catch (Exception ex) {
                    showDefault = false;
                }
                final int descriptionCount = Math.max(1, option.description().length);
                final int ROW_COUNT = showDefault ? descriptionCount + 1 : descriptionCount;
                final int COLUMN_COUNT = 5;
                Text[][] result = new Text[ROW_COUNT][COLUMN_COUNT];
                result[0] = new Text[] { scheme.optionText(requiredOption), scheme.optionText(shortOption),
                        new Text(sep), longOptionText, new Text(str(option.description(), 0)) };
                for (int i = 1; i < option.description().length; i++) {
                    result[i] = new Text[] { Text.EMPTY, Text.EMPTY, Text.EMPTY, Text.EMPTY, new Text(option.description()[i]) };
                }
                if (showDefault) {
                    Arrays.fill(result[result.length - 1], Text.EMPTY);
                    int row = empty(result[ROW_COUNT - 2][COLUMN_COUNT - 1]) ? ROW_COUNT - 2 : ROW_COUNT - 1;
                    result[row][COLUMN_COUNT - 1] = new Text("Default: " + defaultValue);
                }
                return result;
            }
        }
        /** The MinimalOptionRenderer converts {@link Option Options} to a single row with two columns of text: an
         * option name and a description. If multiple names or description lines exist, the first value is used. */
        static class MinimalOptionRenderer implements IOptionRenderer {
            public Text[][] render(Option option, Field field, IParamLabelRenderer parameterLabelRenderer, ColorScheme scheme) {
                Text optionText = scheme.optionText(option.names()[0]);
                Text paramLabelText = parameterLabelRenderer.renderParameterLabel(field, scheme.optionParamStyles);
                optionText = optionText.append(paramLabelText);
                return new Text[][] {{ optionText,
                                        new Text(option.description().length == 0 ? "" : option.description()[0]) }};
            }
        }
        /** The MinimalParameterRenderer converts {@link Parameters Parameters} to a single row with two columns of
         * text: the parameters label and a description. If multiple description lines exist, the first value is used. */
        static class MinimalParameterRenderer implements IParameterRenderer {
            public Text[][] render(Parameters param, Field field, IParamLabelRenderer parameterLabelRenderer, ColorScheme scheme) {
                return new Text[][] {{ parameterLabelRenderer.renderParameterLabel(field, scheme.parameterStyles),
                        new Text(param.description().length == 0 ? "" : param.description()[0]) }};
            }
        }
        /** When customizing online help for {@link Parameters Parameters} details, a custom {@code IParameterRenderer}
         * can be used to create textual representation of a Parameters field in a tabular format: one or more rows,
         * each containing one or more columns. The {@link Layout Layout} is responsible for placing these text
         * values in the {@link TextTable TextTable}. */
        public interface IParameterRenderer {
            /**
             * Returns a text representation of the specified Parameters and the Field that captures the parameter values.
             * @param parameters the command line parameters to show online usage help for
             * @param field the field that will hold the value for the command line parameters
             * @param parameterLabelRenderer responsible for rendering parameter labels to text
             * @param scheme color scheme for applying ansi color styles to positional parameters
             * @return a 2-dimensional array of text values: one or more rows, each containing one or more columns
             */
            Text[][] render(Parameters parameters, Field field, IParamLabelRenderer parameterLabelRenderer, ColorScheme scheme);
        }
        /** The DefaultParameterRenderer converts {@link Parameters Parameters} to five columns of text to match the
         * default {@linkplain TextTable TextTable} column layout. The first row of values looks like this:
         * <ol>
         * <li>the required option marker (if the parameter's arity is to have at least one value)</li>
         * <li>empty string </li>
         * <li>empty string </li>
         * <li>parameter(s) label as rendered by the {@link IParamLabelRenderer}</li>
         * <li>first element of the {@link Parameters#description()} array</li>
         * </ol>
         * <p>Following this, there will be one row for each of the remaining elements of the {@link
         *   Parameters#description()} array, and these rows look like {@code {"", "", "", param.description()[i]}}.</p>
         */
        static class DefaultParameterRenderer implements IParameterRenderer {
            public String requiredMarker = " ";
            public Text[][] render(Parameters params, Field field, IParamLabelRenderer paramLabelRenderer, ColorScheme scheme) {
                Text label = paramLabelRenderer.renderParameterLabel(field, scheme.parameterStyles);
                Text requiredParameter = scheme.parameterText(Arity.forParameters(field).min > 0 ? requiredMarker : "");

                final int COLUMN_COUNT = 5;
                Text[][] result = new Text[Math.max(1, params.description().length)][COLUMN_COUNT];
                result[0] = new Text[] { requiredParameter, Text.EMPTY, Text.EMPTY, label, new Text(str(params.description(), 0)) };
                for (int i = 1; i < params.description().length; i++) {
                    result[i] = new Text[] { Text.EMPTY, Text.EMPTY, Text.EMPTY, Text.EMPTY, new Text(params.description()[i]) };
                }
                return result;
            }
        }
        /** When customizing online usage help for an option parameter or a positional parameter, a custom
         * {@code IParamLabelRenderer} can be used to render the parameter name or label to a String. */
        public interface IParamLabelRenderer {

            /** Returns a text rendering of the Option parameter or positional parameter; returns an empty string
             * {@code ""} if the option is a boolean and does not take a parameter. */
            Text renderParameterLabel(Field field, List<IStyle> styles);

            /** Returns the separator between option name and param label */
            String separator();
        }
        /**
         * DefaultParamLabelRenderer separates option parameters from their {@linkplain Option options} with a
         * {@linkplain DefaultParamLabelRenderer#separator separator} string, surrounds optional values
         * with {@code '['} and {@code ']'} characters and uses ellipses ("...") to indicate that any number of
         * values is allowed for options or parameters with variable arity.
         */
        static class DefaultParamLabelRenderer implements IParamLabelRenderer {
            /** The string to use to separate option parameters from their options. */
            public final String separator;
            /** Constructs a new DefaultParamLabelRenderer with the specified separator string. */
            public DefaultParamLabelRenderer(String separator) {
                this.separator = Assert.notNull(separator, "separator");
            }
            public String separator() { return separator; }
            public Text renderParameterLabel(Field field, List<IStyle> styles) {
                boolean isOptionParameter = field.isAnnotationPresent(Option.class);
                Arity arity = isOptionParameter ? Arity.forOption(field) : Arity.forParameters(field);
                Text result = new Text("");
                String sep = isOptionParameter ? separator : "";
                if (arity.min > 0) {
                    for (int i = 0; i < arity.min; i++) {
                        result = result.append(sep).append(Text.apply(renderParameterName(field), styles));
                        sep = " ";
                    }
                }
                if (arity.max > arity.min) {
                    sep = result.length == 0 ? (isOptionParameter ? separator : "") : " ";
                    int max = arity.isVariable ? 1 : arity.max - arity.min;
                    for (int i = 0; i < max; i++) {
                        if (sep.trim().length() == 0) {
                            result = result.append(sep + "[").append(Text.apply(renderParameterName(field), styles));
                        } else {
                            result = result.append("[" + sep).append(Text.apply(renderParameterName(field), styles));
                        }
                        sep  = " ";
                    }
                    if (arity.isVariable) {
                        result = result.append("...");
                    }
                    for (int i = 0; i < max; i++) { result = result.append("]"); }
                }
                return result;
            }
            private String renderParameterName(Field field) {
                String result = null;
                if (field.isAnnotationPresent(Option.class)) {
                    result = field.getAnnotation(Option.class).paramLabel();
                } else if (field.isAnnotationPresent(Parameters.class)) {
                    result = field.getAnnotation(Parameters.class).paramLabel();
                }
                if (result != null && result.trim().length() > 0) {
                    return result.trim();
                }
                return "<" + field.getName() + ">";
            }
        }
        /** Layout is responsible for creating a fully formatted list of options/parameters.
         * By default this Layout displays each array of text values representing on a separate row in the table.
         * Customize by overriding the {@link #layout(Field, Text[][])} method.
         * @see IOptionRenderer rendering options to text
         * @see IParameterRenderer rendering parameters to text
         * @see TextTable showing values in a tabular format
         */
        public static class Layout {
            protected final ColorScheme colorScheme;
            protected final TextTable table;
            protected IOptionRenderer optionRenderer;
            protected IParameterRenderer parameterRenderer;

            /** @see Help#defaultColorScheme() */
            public Layout(ColorScheme colorScheme) { this(colorScheme, new TextTable()); }
            public Layout(ColorScheme colorScheme, TextTable textTable) {
                this(colorScheme, textTable, new DefaultOptionRenderer(), new DefaultParameterRenderer());
            }
            public Layout(ColorScheme colorScheme, TextTable textTable, IOptionRenderer optionRenderer, IParameterRenderer parameterRenderer) {
                this.colorScheme       = Assert.notNull(colorScheme, "colorScheme");
                this.table             = Assert.notNull(textTable, "textTable");
                this.optionRenderer    = Assert.notNull(optionRenderer, "optionRenderer");
                this.parameterRenderer = Assert.notNull(parameterRenderer, "parameterRenderer");
            }
            /**
             * Copies the specified text values into the correct cells in the {@link TextTable}. This implementation
             * delegates to {@link TextTable#addRowValues(Text...)} for each row of values.
             * <p>Subclasses may override.</p>
             * @param field the field annotated with the specified Option or Parameters
             * @param cellValues the text values representing the Option/Parameters, to be displayed in tabular form
             */
            public void layout(Field field, Text[][] cellValues) {
                for (Text[] oneRow : cellValues) {
                    table.addRowValues(oneRow);
                }
            }
            /** Calls {@link #addOption(Field, CommandLine.Help.IParamLabelRenderer)} for all non-hidden Options in the list. */
            public void addOptions(List<Field> fields, IParamLabelRenderer paramLabelRenderer) {
                for (Field field : fields) {
                    Option option = field.getAnnotation(Option.class);
                    if (!option.hidden()) {
                        addOption(field, paramLabelRenderer);
                    }
                }
            }
            /**
             * Convenience method that delegates to the configured {@link #optionRenderer option renderer} to obtain
             * text values for the specified {@link Option}, and then delegates to the {@link #layout(Field, Text[][]) layout}
             * method to write these text values into the correct cells in this TextTable.
             * @param field the field annotated with the specified Option
             * @param paramLabelRenderer knows how to render option parameters
             */
            public void addOption(Field field, IParamLabelRenderer paramLabelRenderer) {
                Option option = field.getAnnotation(Option.class);
                Text[][] values = optionRenderer.render(option, field, paramLabelRenderer, colorScheme);
                layout(field, values);
            }
            /** Calls {@link #addPositionalParameter(Field, CommandLine.Help.IParamLabelRenderer)} for all non-hidden Parameters in the list. */
            public void addPositionalParameters(List<Field> fields, IParamLabelRenderer paramLabelRenderer) {
                for (Field field : fields) {
                    Parameters parameters = field.getAnnotation(Parameters.class);
                    if (!parameters.hidden()) {
                        addPositionalParameter(field, paramLabelRenderer);
                    }
                }
            }
            /**
             * Convenience method that delegates to the configured {@link #parameterRenderer parameter renderer}
             * to obtain text values for the specified {@link Parameters}, and then delegates to
             * {@link #layout(Field, Text[][]) layout} to write these text values into the correct cells in this TextTable.
             * @param field the field annotated with the specified Parameters
             * @param paramLabelRenderer knows how to render option parameters
             */
            public void addPositionalParameter(Field field, IParamLabelRenderer paramLabelRenderer) {
                Parameters option = field.getAnnotation(Parameters.class);
                Text[][] values = parameterRenderer.render(option, field, paramLabelRenderer, colorScheme);
                layout(field, values);
            }
            @Override public String toString() {
                return table.toString();
            }
        }
        /** Sorts short strings before longer strings. */
        static class ShortestFirst implements Comparator<String> {
            public int compare(String o1, String o2) {
                return o1.length() - o2.length();
            }
            /** Sorts the specified array of Strings shortest-first and returns it. */
            public static String[] sort(String[] names) {
                Arrays.sort(names, new ShortestFirst());
                return names;
            }
        }
        /** Sorts {@code Option} instances by their name in case-insensitive alphabetic order. If an Option has
         * multiple names, the shortest name is used for the sorting. Help options follow non-help options. */
        static class SortByShortestOptionNameAlphabetically implements Comparator<Field> {
            ShortestFirst shortestFirst = new ShortestFirst();
            public int compare(Field f1, Field f2) {
                Option o1 = f1.getAnnotation(Option.class);
                Option o2 = f2.getAnnotation(Option.class);
                if (o1 == null) { return 1; } else if (o2 == null) { return -1; } // options before params
                String[] names1 = shortestFirst.sort(o1.names());
                String[] names2 = shortestFirst.sort(o2.names());
                int result = names1[0].toUpperCase().compareTo(names2[0].toUpperCase()); // case insensitive sort
                result = result == 0 ? -names1[0].compareTo(names2[0]) : result; // lower case before upper case
                return o1.help() == o2.help() ? result : o2.help() ? -1 : 1; // help options come last
            }
        }
        /** Sorts {@code Option} instances by their max arity first, then their min arity, then delegates to super class. */
        static class SortByOptionArityAndNameAlphabetically extends SortByShortestOptionNameAlphabetically {
            public int compare(Field f1, Field f2) {
                Option o1 = f1.getAnnotation(Option.class);
                Option o2 = f2.getAnnotation(Option.class);
                Arity arity1 = Arity.forOption(f1);
                Arity arity2 = Arity.forOption(f2);
                int result = arity1.max - arity2.max;
                if (result == 0) {
                    result = arity1.min - arity2.min;
                }
                return result == 0 ? super.compare(f1, f2) : result;
            }
        }
        /**
         * <p>Provides a table layout for text values, applicable for arranging option names and their description on
         * the console. A table has a fixed number of {@link Column Columns}, where each column has a fixed width,
         * indent and {@link Column.Overflow} policy. The Overflow policy determines what happens when a value is
         * longer than the column width.</p>
         */
        public static class TextTable {
            /** The column definitions of this table. */
            public final Column[] columns;
            /** The {@code char[]} slots of the {@code TextTable} to copy text values into. */
            protected final List<Text> columnValues = new ArrayList<Text>();
            /** By default, indent wrapped lines by 2 spaces. */
            public int indentWrappedLines = 2;
            /** Constructs a TextTable with five columns as follows:
             * <ol>
             * <li>required option/parameter marker (width: 2, indent: 0, TRUNCATE on overflow)</li>
             * <li>short option name (width: 2, indent: 0, TRUNCATE on overflow)</li>
             * <li>comma separator (width: 1, indent: 0, TRUNCATE on overflow)</li>
             * <li>long option name(s) (width: 24, indent: 1, SPAN multiple columns on overflow)</li>
             * <li>description line(s) (width: 51, indent: 1, WRAP to next row on overflow)</li>
             * </ol>
             */
            public TextTable() {
                // "* -c, --create                Creates a ...."
                this(new Column[] {
                            new Column(2,  0, TRUNCATE),   // "*"
                            new Column(2,  0, TRUNCATE),   // "-c"
                            new Column(1,  0, TRUNCATE),   // ","
                            new Column(24, 1, SPAN),  // " --create"
                            new Column(51, 1, WRAP) // " Creates a ..."
                    });
            }

            /** Constructs a new TextTable with columns with the specified width, all SPANning  multiple columns on
             * overflow except the last column which WRAPS to the next row.
             * @param columnWidths the width of the table columns (all columns have zero indent)
             */
            public TextTable(int... columnWidths) {
                columns = new Column[columnWidths.length];
                for (int i = 0; i < columnWidths.length; i++) {
                    columns[i] = new Column(columnWidths[i], 0, i == columnWidths.length - 1 ? SPAN: WRAP);
                }
            }
            /** Constructs a {@code TextTable} with the specified columns. */
            public TextTable(Column... columns) {
                this.columns = Assert.notNull(columns, "columns");
                if (columns.length == 0) { throw new IllegalArgumentException("At least one column is required"); }
            }
            /** Returns the {@code char[]} slot at the specified row and column to write a text value into. */
            public Text cellAt(int row, int col) { return columnValues.get(col + (row * columns.length)); }

            /** Returns the current number of rows of this {@code TextTable}. */
            public int rowCount() { return columnValues.size() / columns.length; }

            /** Adds the required {@code char[]} slots for a new row to the {@link #columnValues} field. */
            public void addEmptyRow() {
                for (int i = 0; i < columns.length; i++) {
                    columnValues.add(new Text(columns[i].width));
                }
            }

            /** Delegates to {@link #addRowValues(Text...)}. */
            public void addRowValues(String... values) {
                Text[] array = new Text[values.length];
                for (int i = 0; i < array.length; i++) {
                    array[i] = values[i] == null ? Text.EMPTY : new Text(values[i]);
                }
                addRowValues(array);
            }
            /**
             * Adds a new {@linkplain TextTable#addEmptyRow() empty row}, then calls {@link
             * TextTable#putValue(int, int, Text) putValue} for each of the specified values, adding more empty rows
             * if the return value indicates that the value spanned multiple columns or was wrapped to multiple rows.
             * @param values the values to write into a new row in this TextTable
             * @throws IllegalArgumentException if the number of values exceeds the number of Columns in this table
             */
            public void addRowValues(Text... values) {
                if (values.length > columns.length) {
                    throw new IllegalArgumentException(values.length + " values don't fit in " +
                            columns.length + " columns");
                }
                addEmptyRow();
                for (int col = 0; col < values.length; col++) {
                    int row = rowCount() - 1;// write to last row: previous value may have wrapped to next row
                    Point cell = putValue(row, col, values[col]);

                    // add row if a value spanned/wrapped and there are still remaining values
                    if ((cell.y != row || cell.x != col) && col != values.length - 1) {
                        addEmptyRow();
                    }
                }
            }
            /**
             * Writes the specified value into the cell at the specified row and column and returns the last row and
             * column written to. Depending on the Column's {@link Column#overflow Overflow} policy, the value may span
             * multiple columns or wrap to multiple rows when larger than the column width.
             * @param row the target row in the table
             * @param col the target column in the table to write to
             * @param value the value to write
             * @return a Point whose {@code x} value is the last column written to and whose {@code y} value is the
             *          last row written to
             * @throws IllegalArgumentException if the specified row exceeds the table's {@linkplain
             *          TextTable#rowCount() row count}
             */
            public Point putValue(int row, int col, Text value) {
                if (row > rowCount() - 1) {
                    throw new IllegalArgumentException("Cannot write to row " + row + ": rowCount=" + rowCount());
                }
                if (value == null || value.plain.length() == 0) { return new Point(col, row); }
                Column column = columns[col];
                int indent = column.indent;
                switch (column.overflow) {
                    case TRUNCATE:
                        copy(value, cellAt(row, col), indent);
                        return new Point(col, row);
                    case SPAN:
                        int startColumn = col;
                        do {
                            boolean lastColumn = col == columns.length - 1;
                            int charsWritten = lastColumn
                                    ? copy(BreakIterator.getLineInstance(), value, cellAt(row, col), indent)
                                    : copy(value, cellAt(row, col), indent);
                            value = value.substring(charsWritten);
                            indent = 0;
                            if (value.length > 0) { // value did not fit in column
                                ++col;                // write remainder of value in next column
                            }
                            if (value.length > 0 && col >= columns.length) { // we filled up all columns on this row
                                addEmptyRow();
                                row++;
                                col = startColumn;
                                indent = column.indent + indentWrappedLines;
                            }
                        } while (value.length > 0);
                        return new Point(col, row);
                    case WRAP:
                        BreakIterator lineBreakIterator = BreakIterator.getLineInstance();
                        do {
                            int charsWritten = copy(lineBreakIterator, value, cellAt(row, col), indent);
                            value = value.substring(charsWritten);
                            indent = column.indent + indentWrappedLines;
                            if (value.length > 0) {  // value did not fit in column
                                ++row;                 // write remainder of value in next row
                                addEmptyRow();
                            }
                        } while (value.length > 0);
                        return new Point(col, row);
                }
                throw new IllegalStateException(column.overflow.toString());
            }
            private static int length(Text str) {
                return str.length; // TODO count some characters as double length
            }
            private char[] spaces(int length) { char[] result = new char[length]; Arrays.fill(result, ' '); return result; }

            private int copy(BreakIterator line, Text text, Text columnValue, int offset) {
                // Deceive the BreakIterator to ensure no line breaks after '-' character
                line.setText(text.plainString().replace("-", "\u00ff"));
                int done = 0;
                for (int start = line.first(), end = line.next(); end != BreakIterator.DONE; start = end, end = line.next()) {
                    Text word = text.substring(start, end); //.replace("\u00ff", "-"); // not needed
                    if (columnValue.maxLength >= offset + done + length(word)) {
                        done += copy(word, columnValue, offset + done); // TODO localized length
                    } else {
                        break;
                    }
                }
                return done;
            }
            private static int copy(Text value, Text destination, int offset) {
                int length = Math.min(value.length, destination.maxLength - offset);
                value.getStyledChars(value.from, length, destination, offset);
                return length;
            }

            /** Copies the text representation that we built up from the options into the specified StringBuilder. */
            public StringBuilder toString(StringBuilder text) {
                int columnCount = this.columns.length;
                StringBuilder row = new StringBuilder(80);
                for (int i = 0; i < columnValues.size(); i++) {
                    Text column = columnValues.get(i);
                    row.append(column.toString());
                    row.append(new String(spaces(columns[i % columnCount].width - column.length)));
                    if (i % columnCount == columnCount - 1) {
                        int lastChar = row.length() - 1;
                        while (lastChar >= 0 && row.charAt(lastChar) == ' ') {lastChar--;} // rtrim
                        row.setLength(lastChar + 1);
                        text.append(row.toString()).append(System.getProperty("line.separator"));
                        row.setLength(0);
                    }
                }
                //if (Ansi.enabled()) { text.append(Style.reset.off()); }
                return text;
            }
            public String toString() { return toString(new StringBuilder()).toString(); }
        }
        /** Columns define the width, indent (leading number of spaces in a column before the value) and
         * {@linkplain Overflow Overflow} policy of a column in a {@linkplain TextTable TextTable}. */
        public static class Column {

            /** Policy for handling text that is longer than the column width:
             *  span multiple columns, wrap to the next row, or simply truncate the portion that doesn't fit. */
            public enum Overflow { TRUNCATE, SPAN, WRAP }

            /** Column width in characters */
            public final int width;

            /** Indent (number of empty spaces at the start of the column preceding the text value) */
            public final int indent;

            /** Policy that determines how to handle values larger than the column width. */
            public final Overflow overflow;
            public Column(int width, int indent, Overflow overflow) {
                this.width = width;
                this.indent = indent;
                this.overflow = Assert.notNull(overflow, "overflow");
            }
        }

        /** All usage help message are generated with a color scheme that assigns certain styles and colors to common
         * parts of a usage message: the command name, options, positional parameters and option parameters.
         * Users may customize these styles by creating Help with a custom color scheme.
         * <p>Note that these options and styles may not be rendered if ASCII escape codes are not
         * {@linkplain CommandLine.Help.Ansi#enabled() enabled}.</p>
         * @see Help#defaultColorScheme()
         */
        public static class ColorScheme {
            public final List<IStyle> commandStyles = new ArrayList<IStyle>();
            public final List<IStyle> optionStyles = new ArrayList<IStyle>();
            public final List<IStyle> parameterStyles = new ArrayList<IStyle>();
            public final List<IStyle> optionParamStyles = new ArrayList<IStyle>();
            /** Adds the specified styles to the styles for commands in this color scheme and returns this color scheme. */
            public ColorScheme commands(IStyle... styles)     { return addAll(commandStyles, styles); }
            /** Adds the specified styles to the styles for options in this color scheme and returns this color scheme. */
            public ColorScheme options(IStyle... styles)      { return addAll(optionStyles, styles);}
            /** Adds the specified styles to the styles for positional parameters in this color scheme and returns this color scheme. */
            public ColorScheme parameters(IStyle... styles)   { return addAll(parameterStyles, styles);}
            /** Adds the specified styles to the styles for option parameters in this color scheme and returns this color scheme. */
            public ColorScheme optionParams(IStyle... styles) { return addAll(optionParamStyles, styles);}
            /** Returns a Text with all command styles applied to the specified command string. */
            public Ansi.Text commandText(String command)         { return Text.apply(command,     commandStyles); }
            /** Returns a Text with all option styles applied to the specified option string. */
            public Ansi.Text optionText(String option)           { return Text.apply(option,      optionStyles); }
            /** Returns a Text with all parameter styles applied to the specified parameter string. */
            public Ansi.Text parameterText(String parameter)     { return Text.apply(parameter,   parameterStyles); }
            /** Returns a Text with all optionParam styles applied to the specified optionParam string. */
            public Ansi.Text optionParamText(String optionParam) { return Text.apply(optionParam, optionParamStyles); }

            /** Replaces colors and styles in this scheme with ones specified in system properties, and returns this scheme.
             * Supported property names:<ul>
             *     <li>{@code picocli.color.commands}</li>
             *     <li>{@code picocli.color.options}</li>
             *     <li>{@code picocli.color.parameters}</li>
             *     <li>{@code picocli.color.optionParams}</li>
             * </ul><p>Property values can be anything that {@link Help.Ansi.Style#parse(String)} can handle.</p>
             * @return this ColorScheme
             */
            public ColorScheme applySystemProperties() {
                replace(commandStyles,     System.getProperty("picocli.color.commands"));
                replace(optionStyles,      System.getProperty("picocli.color.options"));
                replace(parameterStyles,   System.getProperty("picocli.color.parameters"));
                replace(optionParamStyles, System.getProperty("picocli.color.optionParams"));
                return this;
            }
            private void replace(List<IStyle> styles, String property) {
                if (property != null) {
                    styles.clear();
                    addAll(styles, Style.parse(property));
                }
            }
            private ColorScheme addAll(List<IStyle> styles, IStyle... add) {
                styles.addAll(Arrays.asList(add));
                return this;
            }
        }

        /** Creates and returns a new {@link ColorScheme} initialized with picocli default values: commands are bold,
         *  options and parameters use a yellow foreground, and option parameters use italic.
         * @return a new default color scheme
         */
        public static ColorScheme defaultColorScheme() {
            return new ColorScheme()
                    .commands(Style.bold)
                    .options(Style.fg_yellow)
                    .parameters(Style.fg_yellow)
                    .optionParams(Style.italic);
        }

        /** Provides methods and inner classes to support using ANSI escape codes in usage help messages. */
        public static class Ansi {
            static final boolean isWindows  = System.getProperty("os.name").startsWith("Windows");
            static final boolean isXterm    = System.getenv("TERM") != null && System.getenv("TERM").startsWith("xterm");
            static final boolean ISATTY = calcTTY();
            // http://stackoverflow.com/questions/1403772/how-can-i-check-if-a-java-programs-input-output-streams-are-connected-to-a-term
            static final boolean calcTTY() {
                try { return System.class.getDeclaredMethod("console").invoke(null) != null; }
                catch (Throwable reflectionFailed) { return true; }
            }
            private static boolean ansiPossible() { return ISATTY && (!isWindows || isXterm); }
            private static boolean forceAnsiOn()  { return ansi != null && ansi; }
            private static boolean forceAnsiOff() { return ansi != null && !ansi; }
            /** Enabled if it is forced ON or if the platform supports ANSI escape codes and it is not forced OFF. */
            public  static boolean enabled() { return forceAnsiOn() || (ansiPossible() && !forceAnsiOff()); }

            /** Defines the interface for an ANSI escape sequence. */
            public interface IStyle {
                /** The Control Sequence Introducer (CSI) escape sequence {@code "ESC["}. */
                String CSI = "\u001B[";
				/** Returns the ANSI escape code for turning this style on. */
                String on();
				/** Returns the ANSI escape code for turning this style off. */
                String off();
            }

            /**
             * A set of pre-defined ANSI escape code styles and colors, and a set of convenience methods for parsing
             * text with embedded style descriptors, as well as convenience methods for converting
             * styles to strings with embedded escape codes.
             */
            enum Style implements IStyle {
                reset(0, 0), bold(1, 21), faint(2, 22), italic(3, 23), underline(4, 24), blink(5, 25), reverse(7, 27),
                fg_black(30, 39), fg_red(31, 39), fg_green(32, 39), fg_yellow(33, 39), fg_blue(34, 39), fg_magenta(35, 39), fg_cyan(36, 39), fg_white(37, 39),
                bg_black(40, 49), bg_red(41, 49), bg_green(42, 49), bg_yellow(43, 49), bg_blue(44, 49), bg_magenta(45, 49), bg_cyan(46, 49), bg_white(47, 49),
                ;
                private final int startCode;
                private final int endCode;

                Style(int startCode, int endCode) {this.startCode = startCode; this.endCode = endCode; }
                public String on() { return CSI + startCode + "m"; }
                public String off() { return CSI + endCode + "m"; }
				/** Returns the concatenated ANSI escape codes for turning all specified styles on. */
                public static String on(IStyle... styles) {
                    StringBuilder result = new StringBuilder();
                    for (IStyle style : styles) {
                        result.append(style.on());
                    }
                    return result.toString();
                }
				/** Returns the concatenated ANSI escape codes for turning all specified styles off. */
                public static String off(IStyle... styles) {
                    StringBuilder result = new StringBuilder();
                    for (IStyle style : styles) {
                        result.append(style.off());
                    }
                    return result.toString();
                }
				/** Parses the specified style descriptor and returns the associated style.
				 *  The descriptor may be one of the Style enum value names, or it may be one of the Style enum value
				 *  names when {@code "fg_"} is prepended, or it may be an {@link java.awt.Color#decode(String) RGB Color int}.
				 * @param str the case-insensitive style descriptor to convert, e.g. {@code "blue"} or {@code "fg_blue"}
				 * @return the IStyle for the specified converter
				 */
                public static IStyle fg(String str) {
                    try { return Style.valueOf(str.toLowerCase(ENGLISH)); } catch (Exception ignored) {}
                    try { return Style.valueOf("fg_" + str.toLowerCase(ENGLISH)); } catch (Exception ignored) {}
                    return new ISO86133RGBColor(true, Color.decode(str));
                }
				/** Parses the specified style descriptor and returns the associated style.
				 *  The descriptor may be one of the Style enum value names, or it may be one of the Style enum value
				 *  names when {@code "bg_"} is prepended, or it may be an {@link java.awt.Color#decode(String) RGB Color int}.
				 * @param str the case-insensitive style descriptor to convert, e.g. {@code "blue"} or {@code "bg_blue"}
				 * @return the IStyle for the specified converter
				 */
                public static IStyle bg(String str) {
                    try { return Style.valueOf(str.toLowerCase(ENGLISH)); } catch (Exception ignored) {}
                    try { return Style.valueOf("bg_" + str.toLowerCase(ENGLISH)); } catch (Exception ignored) {}
                    return new ISO86133RGBColor(false, Color.decode(str));
                }
                /** Parses the specified comma-separated sequence of style descriptors and returns the associated
                 *  styles. For each descriptor, strings starting with {@code "bg("} are delegated to
                 *  {@link #bg(String)}, others are delegated to {@link #bg(String)}.
                 * @param commaSeparatedCodes one or more descriptors, e.g. {@code "bg(blue),underline,red"}
                 * @return an array with all styles for the specified descriptors
                 */
                public static IStyle[] parse(String commaSeparatedCodes) {
                    String[] codes = commaSeparatedCodes.split(",");
                    IStyle[] styles = new IStyle[codes.length];
                    for(int i = 0; i < codes.length; ++i) {
                        if (codes[i].toLowerCase(ENGLISH).startsWith("fg(")) {
                            int end = codes[i].indexOf(')');
                            styles[i] = Style.fg(codes[i].substring(3, end < 0 ? codes[i].length() : end));
                        } else if (codes[i].toLowerCase(ENGLISH).startsWith("bg(")) {
                            int end = codes[i].indexOf(')');
                            styles[i] = Style.bg(codes[i].substring(3, end < 0 ? codes[i].length() : end));
                        } else {
                            styles[i] = Style.fg(codes[i]);
                        }
                    }
                    return styles;
                }
            }

            /** ISO-8613-3 24-bit RGB color ansi escape codes. */
            static class ISO86133RGBColor implements IStyle {
                private final int fgbg;
                private final Color color;

                ISO86133RGBColor(boolean foreground, Color color) {
                    this.fgbg = foreground ? 38 : 48;
                    this.color = color;
                }
                public String on() { return String.format(CSI + "%d;2;%d;%d;%dm", fgbg, color.getRed(), color.getGreen(), color.getBlue()); }
                public String off() { return CSI + (fgbg + 1) + "m"; }
            }
            public static class Text implements Cloneable {
                public static Text EMPTY = new Text(0);
                private final int maxLength;
                private int from;
                private int length;
                private StringBuilder plain = new StringBuilder();
                private SortedMap<Integer, String> indexToStyle = new TreeMap<Integer, String>();

                public Text(int maxLength) { this.maxLength = maxLength; }
                public Text(String input) {
                    maxLength = -1;
                    plain.setLength(0);
                    int i = 0;

                    while (true) {
                        int j = input.indexOf("@|", i);
                        if (j == -1) {
                            if (i == 0) {
                                plain.append(input);
                                length = plain.length();
                                return;
                            }
                            plain.append(input.substring(i, input.length()));
                            length = plain.length();
                            return;
                        }
                        plain.append(input.substring(i, j));
                        int k = input.indexOf("|@", j);
                        if (k == -1) {
                            plain.append(input);
                            length = plain.length();
                            return;
                        }

                        j += 2;
                        String spec = input.substring(j, k);
                        String[] items = spec.split(" ", 2);
                        if (items.length == 1) {
                            plain.append(input);
                            length = plain.length();
                            return;
                        }

                        IStyle[] styles = Style.parse(items[0]);
                        putStyle(plain.length(), Style.on(styles));
                        plain.append(items[1]);
                        reverse(styles);
                        putStyle(plain.length(), Style.off(styles));
                        putStyle(plain.length(), Style.reset.off());
                        i = k + 2;
                    }
                }
                private void putStyle(int index, String style) {
                    String existing = indexToStyle.put(index, style);
                    if (existing != null) { indexToStyle.put(index, existing + style); }
                }
                public Object clone() {
                    try { return super.clone(); } catch (CloneNotSupportedException e) { throw new IllegalStateException(e); }
                }
                public Text substring(int start) {
                    return substring(start, length);
                }
                public Text substring(int start, int end) {
                    Text result = (Text) clone();
                    result.from = from + start;
                    result.length = end - start;
                    return result;
                }
                /** Returns a new {@code Text} instance with the specified text appended. Does not modify this instance! */
                public Text append(String string) {
                    return append(new Text(string));
                }
                /** Returns a new {@code Text} instance with the specified text appended. Does not modify this instance! */
                public Text append(Text other) {
                    Text result = (Text) clone();
                    result.plain = new StringBuilder(plain.toString().substring(from, from + length));
                    result.from = 0;
                    result.indexToStyle = new TreeMap<Integer, String>();
                    for (Integer index : indexToStyle.keySet()) {
                        result.indexToStyle.put(index - from, indexToStyle.get(index));
                    }
                    result.plain.append(other.plain.toString().substring(other.from, other.from + other.length));
                    for (Integer otherIndex : other.indexToStyle.keySet()) {
                        int index = result.length + otherIndex - other.from;
                        String replaced = result.indexToStyle.put(index, other.indexToStyle.get(otherIndex));
                        if (replaced != null) {
                            result.indexToStyle.put(index, replaced + other.indexToStyle.get(otherIndex));
                        }
                    }
                    result.length = result.plain.length();
                    return result;
                }

                public void getStyledChars(int from, int length, Text destination, int offset) {
                    if (destination.length < offset) {
                        for (int i = destination.length; i < offset; i++) {
                            destination.plain.append(' ');
                        }
                        destination.length = offset;
                    }
                    for (Integer index : indexToStyle.keySet()) {
                        destination.indexToStyle.put(index - from + destination.length, indexToStyle.get(index));
                    }
                    destination.plain.append(plain.toString().substring(from, from + length));
                    destination.length = destination.plain.length();
                }
                public String plainString() {  return plain.toString().substring(from, from + length); }
                public boolean equals(Object obj) { return toString().equals(String.valueOf(obj)); }
                public int hashCode() { return toString().hashCode(); }
                public String toString() {
                    if (!Ansi.enabled()) {
                        return plain.toString().substring(from, from + length);
                    }
                    if (length == 0) { return ""; }
                    StringBuilder sb = new StringBuilder(plain.length() + 20 * indexToStyle.size());
                    Integer startStyle = null;
                    Integer endStyle = -1;
                    for (Integer index : indexToStyle.keySet()) {
                        if (index <= from) {
                            startStyle = startStyle == null ? index : null;
                            endStyle   = endStyle   == null ? index : null;
                        }
                        if (index >= from) {break;}
                    }
                    if (startStyle != null) {
                        sb.append(indexToStyle.get(startStyle));
                        endStyle = startStyle;
                    }
                    int end = Math.min(from + length, plain.length());
                    for (int i = from; i < end; i++) {
                        String style = indexToStyle.get(i);
                        if (style != null) {
                            if (endStyle != null && endStyle != i) {
                                sb.append(style);
                                startStyle = startStyle == null ? i : null;
                            }
                            endStyle = i;
                        }
                        sb.append(plain.charAt(i));
                    }
                    if (startStyle != null) { // find closing style
                        SortedMap<Integer, String> tailMap = indexToStyle.tailMap(startStyle + 1);
                        if (!tailMap.isEmpty()) {
                            sb.append(indexToStyle.get(tailMap.firstKey()));
                        } else {
                            sb.append(Style.reset.off());
                        }
                    }
                    return sb.toString();
                }

                public static Text apply(String plainText, List<IStyle> styles) {
                    if (plainText.length() == 0) { return new Text(0); }
                    Text result = new Text(plainText.length());
                    IStyle[] all = styles.toArray(new IStyle[styles.size()]);
                    result.indexToStyle.put(result.plain.length(), Style.on(all));
                    result.plain.append(plainText);
                    result.length = result.plain.length();
                    reverse(all);
                    result.indexToStyle.put(result.plain.length(), Style.off(all) + Style.reset.off());
                    return result;
                }

                static void reverse(Object[] all) {
                    for (int i = 0; i < all.length / 2; i++) {
                        Object temp = all[i];
                        all[i] = all[all.length - i - 1];
                        all[all.length - i - 1] = temp;
                    }
                }
            }
        }
    }

    /**
     * Utility class providing some defensive coding convenience methods.
     */
    private static final class Assert {
        /**
         * Throws a NullPointerException if the specified object is null.
         * @param object the object to verify
         * @param description error message
         * @param <T> type of the object to check
         * @return the verified object
         */
        static <T> T notNull(T object, String description) {
            if (object == null) {
                throw new NullPointerException(description);
            }
            return object;
        }
    }

    /**
     * Exception indicating something went wrong while parsing command line options.
     */
    public static class ParameterException extends RuntimeException {
        public ParameterException(String msg) {
            super(msg);
        }

        public ParameterException(String msg, Exception ex) {
            super(msg, ex);
        }

        private static ParameterException create(Exception ex, String arg, int i, String[] args) {
            String next = args.length < i + 1 ? "" : " " + args[i + 1];
            String msg = ex.getClass().getSimpleName() + ": " + ex.getLocalizedMessage()
                    + " while processing option[" + i + "] '" + arg + next + "'.";
            return new ParameterException(msg, ex);
        }
    }
    /**
     * Exception indicating that a required parameter was not specified.
     */
    public static class MissingParameterException extends ParameterException {
        public MissingParameterException(String msg) {
            super(msg);
        }

        private static MissingParameterException create(Set<Field> missing) {
            if (missing.size() == 1) {
                return new MissingParameterException("Missing required option '"
                        + missing.iterator().next().getName() + "'");
            }
            List<String> names = new ArrayList<String>(missing.size());
            for (Field field : missing) {
                names.add(field.getName());
            }
            return new MissingParameterException("Missing required options " + names.toString());
        }
    }

    /**
     * Exception indicating that multiple fields have been annotated with the same Option name.
     */
    public static class DuplicateOptionAnnotationsException extends ParameterException {
        public DuplicateOptionAnnotationsException(String msg) {
            super(msg);
        }

        private static DuplicateOptionAnnotationsException create(String name, Field field1, Field field2) {
            return new DuplicateOptionAnnotationsException("Option name '" + name + "' is used in both "
                    + field1.getName() + " and " + field2.getName());
        }
    }

    /**
     * Exception indicating that an annotated field had a type for which no {@link ITypeConverter} was
     * {@linkplain #registerConverter(Class, ITypeConverter) registered}.
     */
    public static class MissingTypeConverterException extends ParameterException {
        public MissingTypeConverterException(String msg) {
            super(msg);
        }
    }
}
