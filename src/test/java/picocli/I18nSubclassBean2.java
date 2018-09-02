package picocli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "i18l-sub2",
        description = {"orig sub2 desc 1", "orig sub2 desc 2"},
        descriptionHeading = "orig sub2 desc heading%n",
        headerHeading = "orig sub2 header heading%n")
public class I18nSubclassBean2 extends I18nBean {
    @Option(names = {"-a", "--aaa"})
    String a;

    @Option(names = {"-b", "--bbb"}, description = {"orig sub2 bbb description 1", "orig sub2 bbb description 2"})
    String b;

    @Option(names = {"-c", "--ccc"}, description = "orig sub2 ccc description")
    String c;

    @Parameters(index = "2", description = "sub2")
    String param2;

    @Parameters(index = "3", description = "orig sub2 param1 description")
    String param3;
}
