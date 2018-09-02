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

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ProvideSystemProperty;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

import static org.junit.Assert.*;
import static picocli.HelpTestUtil.usageString;

/**
 * Tests valid values-related functionality.
 */
public class I18nTest {

    @Rule
    public final ProvideSystemProperty ansiOFF = new ProvideSystemProperty("picocli.ansi", "false");

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Test
    public void testLocalizedCommandWithResourceBundle() {
        String expected = String.format("" +
                "This is my app. There are other apps like it but this one is mine.%n" +
                "header first line from bundle%n" +
                "header second line from bundle%n" +
                "BundleUsage: ln [OPTION]... [-T] TARGET LINK_NAME   (1st form)%n" +
                "  or:  ln [OPTION]... TARGET                  (2nd form)%n" +
                "  or:  ln [OPTION]... TARGET... DIRECTORY     (3rd form)%n" +
                "Description from bundle:%n" +
                "bundle line 0%n" +
                "bundle line 1%n" +
                "bundle line 2%n" +
                "%n" +
                "Positional parameters from bundle:%n" +
                "      <param0>    parameter 0 description[0] from bundle%n" +
                "                  parameter 0 description[1] from bundle%n" +
                "      <param1>    orig param1 description%n" +
                "%n" +
                "Options from bundle:%n" +
                "  -h, --help      Help option description from bundle.%n" +
                "  -V, --version   Version option description from bundle.%n" +
                "  -x, --xxx=<x>   X option description[0] from bundle.%n" +
                "                  X option description[1] from bundle.%n" +
                "  -y, --yyy=<y>   orig yyy description 1%n" +
                "                  orig yyy description 2%n" +
                "  -z, --zzz=<z>   orig zzz description%n" +
                "%n" +
                "Commands from bundle:%n" +
                "  help  Displays help information about the specified command%n" +
                "Powered by picocli from bundle%n" +
                "footer from bundle%n");
        assertEquals(expected, new CommandLine(new I18nBean()).getUsageMessage());
    }
}
