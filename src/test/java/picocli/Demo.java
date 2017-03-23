/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package picocli;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Demonstrates some of picoCLI's capabilities.
 */
public class Demo {

    @CommandLine.Command(name = "git", sortOptions = false, showDefaultValues = false,
            description = "Git is a fast, scalable, distributed revision control " +
            "system with an unusually rich command set that provides both high-level operations " +
            "and full access to internals.")
    class Git {
        @CommandLine.Option(names = {"-V", "--version"}, help = true, description = "Prints version information and exits")
        boolean isVersionRequested;

        @CommandLine.Option(names = {"-h", "--help"}, help = true, description = "Prints this help message and exits")
        boolean isHelpRequested;

        @CommandLine.Option(names = "--git-dir", description = "Set the path to the repository")
        File gitDir;
    }

    // the "status" subcommand's has an option "mode" with a fixed number of values, modeled by this enum
    enum GitStatusMode {all, no, normal};

    @CommandLine.Command(name = "git-status",
            header = "Show the working tree status",
            customSynopsis = "status [<options>...] [--] [<pathspec>...]",
            description = "Displays paths that have differences between the index file and the current HEAD commit," +
                    "paths that have differences between the working tree and the index file, and paths in the" +
                    "working tree that are not tracked by Git (and are not ignored by gitignore(5)). The first" +
                    "are what you would commit by running git commit; the second and third are what you could" +
                    "commit by running git add before running git commit.")
    class GitStatus {
        @CommandLine.Option(names = {"-s", "--short"}, description = "Give the output in the short-format")
        boolean shortFormat;

        @CommandLine.Option(names = {"-b", "--branch"}, description = "Show the branch and tracking info even in short-format")
        boolean branchInfo;

        @CommandLine.Option(names = "--ignored", description = "Show ignored files as well") boolean showIgnored;

        @CommandLine.Option(names = {"-u", "--untracked"}, valueLabel = "<mode>", description = {
                "Show untracked files.",
                "The mode parameter is optional (defaults to `all`), and is used to specify the handling of untracked files.",
                "The possible options are:",
                " · no - Show no untracked files.",
                " · normal - Shows untracked files and directories.",
                " · all - Also shows individual files in untracked directories."
        })
        GitStatusMode mode = GitStatusMode.all;
    }
    @CommandLine.Command(name = "git-commit", sortOptions = false, header = "Record changes to the repository",
            description = "Stores the current contents of the index in a new commit along with a " +
                    "log message from the user describing the changes.")
    class GitCommit {
        @CommandLine.Option(names = {"-a", "--all"},
                description = "Tell the command to automatically stage files that have been modified " +
                        "and deleted, but new files you have not told Git about are not affected.")
        boolean all;

        @CommandLine.Option(names = {"-p", "--patch"}, description = "Use the interactive patch selection interface to chose which changes to commit")
        boolean patch;

        @CommandLine.Option(names = {"-C", "--reuse-message"}, valueLabel = "<commit>",
                description = "Take an existing commit object, and reuse the log message and the " +
                        "authorship information (including the timestamp) when creating the commit.")
        String reuseMessageCommit;

        @CommandLine.Option(names = {"-c", "--reedit-message"}, valueLabel = "<commit>",
                description = "Like -C, but with -c the editor is invoked, so that the user can" +
                        "further edit the commit message.\n")
        String reEditMessageCommit;

        @CommandLine.Option(names = "--fixup", valueLabel = "<commit>",
                description = "Construct a commit message for use with rebase --autosquash.")
        String fixupCommit;

        @CommandLine.Option(names = "--squash", valueLabel = "<commit>",
                description = " Construct a commit message for use with rebase --autosquash. The commit" +
                        "message subject line is taken from the specified commit with a prefix of " +
                        "\"squash! \". Can be used with additional commit message options (-m/-c/-C/-F).")
        String squashCommit;

        @CommandLine.Option(names = {"-F", "--file"}, valueLabel = "<file>",
                description = "Take the commit message from the given file. Use - to read the message from the standard input.")
        File file;

        @CommandLine.Option(names = {"-m", "--message"}, valueLabel = "<msg>",
                description = " Use the given <msg> as the commit message. If multiple -m options" +
                        " are given, their values are concatenated as separate paragraphs.")
        List<String> message = new ArrayList<String>();

        @CommandLine.Parameters(valueLabel = "<files>", description = "the files to commit")
        List<File> files = new ArrayList<File>();
    }

    // defines some commands to show in the list (option/parameters fields omitted for this demo)
    @CommandLine.Command(name = "git-add", header = "Add file contents to the index") class GitAdd {}
    @CommandLine.Command(name = "git-branch", header = "List, create, or delete branches") class GitBranch {}
    @CommandLine.Command(name = "git-checkout", header = "Checkout a branch or paths to the working tree") class GitCheckout{}
    @CommandLine.Command(name = "git-clone", header = "Clone a repository into a new directory") class GitClone{}
    @CommandLine.Command(name = "git-diff", header = "Show changes between commits, commit and working tree, etc") class GitDiff{}
    @CommandLine.Command(name = "git-merge", header = "Join two or more development histories together") class GitMerge{}
    @CommandLine.Command(name = "git-push", header = "Update remote refs along with associated objects") class GitPush{}
    @CommandLine.Command(name = "git-rebase", header = "Forward-port local commits to the updated upstream head") class GitRebase{}
    @CommandLine.Command(name = "git-tag", header = "Create, list, delete or verify a tag object signed with GPG") class GitTag{}

    @Test
    public void testParseSubCommands() {
        CommandLine commandLine = new CommandLine(new Git());
        commandLine.addCommand("status", new GitStatus());
        commandLine.addCommand("commit", new GitCommit());
        commandLine.addCommand("add", new GitAdd());
        commandLine.addCommand("branch", new GitBranch());
        commandLine.addCommand("checkout", new GitCheckout());
        commandLine.addCommand("clone", new GitClone());
        commandLine.addCommand("diff", new GitDiff());
        commandLine.addCommand("merge", new GitMerge());
        commandLine.addCommand("push", new GitPush());
        commandLine.addCommand("rebase", new GitRebase());
        commandLine.addCommand("tag", new GitTag());

        List<Object> parsed = commandLine.parse("--git-dir=/home/rpopma/picocli status -sbuno".split(" "));
        assertEquals("command count", 2, parsed.size());

        assertEquals(Git.class, parsed.get(0).getClass());
        assertEquals(GitStatus.class, parsed.get(1).getClass());
        Git git = (Git) parsed.get(0);
        GitStatus status = (GitStatus) parsed.get(1);

        assertEquals(new File("/home/rpopma/picocli"), git.gitDir);
        assertTrue("status -s", status.shortFormat);
        assertTrue("status -b", status.branchInfo);
        assertFalse("NOT status --showIgnored", status.showIgnored);
        assertEquals("status -u=no", GitStatusMode.no, status.mode);
    }

    @Test
    public void testUsageSubCommands() throws Exception {
        CommandLine commandLine = new CommandLine(new Git());
        commandLine.addCommand("status", new GitStatus());
        commandLine.addCommand("commit", new GitCommit());
        commandLine.addCommand("add", new GitAdd());
        commandLine.addCommand("branch", new GitBranch());
        commandLine.addCommand("checkout", new GitCheckout());
        commandLine.addCommand("clone", new GitClone());
        commandLine.addCommand("diff", new GitDiff());
        commandLine.addCommand("merge", new GitMerge());
        commandLine.addCommand("push", new GitPush());
        commandLine.addCommand("rebase", new GitRebase());
        commandLine.addCommand("tag", new GitTag());

        String expected = "Usage: git [-hV] [--git-dir=<gitDir>]%n" +
                "Git is a fast, scalable, distributed revision control system with an unusually%n" +
                "rich command set that provides both high-level operations and full access to%n" +
                "internals.%n" +
                "  -V, --version               Prints version information and exits%n" +
                "  -h, --help                  Prints this help message and exits%n" +
                "      --git-dir=<gitDir>      Set the path to the repository%n" +
                "%n" +
                "Commands:%n" +
                "%n" +
                "The most commonly used git commands are:%n" +
                "  status    Show the working tree status%n" +
                "  commit    Record changes to the repository%n" +
                "  add       Add file contents to the index%n" +
                "  branch    List, create, or delete branches%n" +
                "  checkout  Checkout a branch or paths to the working tree%n" +
                "  clone     Clone a repository into a new directory%n" +
                "  diff      Show changes between commits, commit and working tree, etc%n" +
                "  merge     Join two or more development histories together%n" +
                "  push      Update remote refs along with associated objects%n" +
                "  rebase    Forward-port local commits to the updated upstream head%n" +
                "  tag       Create, list, delete or verify a tag object signed with GPG%n";

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        commandLine.usage(new PrintStream(baos, true, "UTF8"));
        String result = baos.toString("UTF8");
        assertEquals(String.format(expected), result);
    }
}
