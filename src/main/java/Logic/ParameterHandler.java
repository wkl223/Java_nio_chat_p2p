package Logic;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.BooleanOptionHandler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.kohsuke.args4j.ExampleMode.ALL;

/*
Your peer should be executableexactlyas follows where the port option -p gives the port that the peer
listens on for incomming connections and the portoption -i gives the port that the peer uses to make
connections to other peerswhen the user issues theConnectcommand:
$> java -jar chatpeer.jar [-p port] [-i port]
* */
public class ParameterHandler {

    @Option(name="-p",usage="incomingPort")
    private int incomingPort = -1;

    @Option(name="-i",usage="outgoingPort")
    private int outgoingPort = -1;

    public void doMain(String[] args) throws IOException {
        CmdLineParser parser = new CmdLineParser(this);

        try {
            // parse the arguments.
            parser.parseArgument(args);

        } catch(CmdLineException e ) {
            // if there's a problem in the command line,
            // you'll get this exception. this will report
            // an error message.
            System.err.println(e.getMessage());
            System.err.println("java -jar chatpeer.jar [-p port] [-i port]");
            // print the list of available options
            parser.printUsage(System.err);
            System.err.println();

            // print option sample. This is useful some time
            System.err.println("  Example: java -jar chatpeer.jar "+ parser.printExample(ALL));

            return;
        }
    }
    public int getP(){
        return incomingPort;
    }
    public int getI(){
        return outgoingPort;
    }
}
