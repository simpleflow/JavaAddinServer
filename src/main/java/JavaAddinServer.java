/* 
 This is an example of a Notes Server Addin written in Java.
 by Julian Robichaux -- http://www.nsftools.com
 
 To use it, copy the compiled class file to your Domino program
 directory and type "load runjava JavaAddinServer" at the server
 console. R5+ only! To see the usage instructions, type
 "tell JavaAddinServer help" at the console, once the addin is
 running. You can stop the addin at any time by typing
 "tell JavaAddinServer quit".
 
 Please note that as of R5, this is completely
 unsupported functionality, so if it doesn't work right you may
 not be able to get help from Lotus/IBM.
 
 Original idea from http://searchdomino.techtarget.com/tip/1,289483,sid4_gci541938,00.html?FromTaxonomy=%2Fpr%2F283839
 
 This is version 1.1a of the code. The only big change is in the
 displayDbSize method, where I added a check for db.isOpen().
 This was at the recommendation of Peter Gloor ( http://www.notesdev.ch ),
 who told me that on certain versions of Domino, attempting to
 access the properties of a database that does not exist will
 crash the Notes server instead of throwing an error. If you
 perform a db.isOpen check before trying to access the properties,
 you can avoid this problem. Peter indicated that this can
 happen with agents, too.
 
 From version 1.1 to version 1.1a, I added a line when the
 help/usage message is displayed, which indicates that you can
 terminate the addin by typing "tell JavaAddinServer quit".
 
 Also, I recently read an IBM technote about how you should 
 always make sure to recycle() your Notes objects after you're
 done using them, or otherwise you can end up with objects that
 never quite get released from memory. I've tried to do that 
 with all the methods in this example, so try to make sure you
 do it too.
 */

// make sure Notes.jar is in your ClassPath
import lotus.domino.*;
import lotus.notes.addins.JavaServerAddin;
import lotus.notes.internal.MessageQueue;
import java.util.*;

public class JavaAddinServer extends JavaServerAddin {
    /* global variables */
    // the name of this program
    String progName = new String("JavaAddinServer");

    // the "friendly" name for this Addin
    String addinName = new String("Java Addin Server");

    // Message Queue name for this Addin (normally uppercase);
    // MSG_Q_PREFIX is defined in JavaServerAddin.class
    String qName = new String(MSG_Q_PREFIX + "JAVAADDINSERVER");

    // MessageQueue Constants
    public static final int MQ_MAX_MSGSIZE = 256;

    // this is already defined (should be = 1):
    public static final int MQ_WAIT_FOR_MSG = MessageQueue.MQ_WAIT_FOR_MSG;

    // MessageQueue errors:
    public static final int PKG_MISC = 0x0400;

    public static final int ERR_MQ_POOLFULL = PKG_MISC + 94;

    public static final int ERR_MQ_TIMEOUT = PKG_MISC + 95;

    public static final int ERR_MQSCAN_ABORT = PKG_MISC + 96;

    public static final int ERR_DUPLICATE_MQ = PKG_MISC + 97;

    public static final int ERR_NO_SUCH_MQ = PKG_MISC + 98;

    public static final int ERR_MQ_EXCEEDED_QUOTA = PKG_MISC + 99;

    public static final int ERR_MQ_EMPTY = PKG_MISC + 100;

    public static final int ERR_MQ_BFR_TOO_SMALL = PKG_MISC + 101;

    public static final int ERR_MQ_QUITTING = PKG_MISC + 102;

    /* the main method, which just kicks off the runNotes method */
    public static void main(String[] args) {
        // kick off the Addin from main -- you can also do something
        // here with optional program args, if you want to...
        JavaAddinServer addinserver = new JavaAddinServer();

        addinserver.start();
    }

    /* some class constructors */
    public void JavaAddinServer() {
        setName(progName);
    }

    public void JavaAddinServer(String[] args) {
        setName(progName);
        // do something with the args that were passed...
        if (args.length > 0) {
            // whatever...
        }
    }

    /* the runNotes method, which is the main loop of the Addin */
    public void runNotes() {
        int taskID;
        MessageQueue mq;
        StringBuffer qBuffer = new StringBuffer();
        int mqError;

        try {

            // set the text to be displayed if a user issues a SHOW STAT
            // or SHOW TASKS command (normally 20 characters or less for
            // the CreateStatusLine descriptor, 80 characters or less for
            // SetStatusLine, although larger strings can be used). You
            // can have multiple StatusLines that all display separately
            // by making multiple calls to AddInCreateStatusLine, and
            // keeping track of the different task IDs. Make sure you
            // deallocate the memory for the StatusLines at the exit point
            // of your program by calling AddInDeleteStatusLine for each
            // of the StatusLines that you use.
            taskID = AddInCreateStatusLine(addinName);
            AddInSetStatusLine(taskID, "Initialization in progress...");

            // set up the message queue (make sure the queue gets closed
            // when the program exits, with a call to mq.close). Note that
            // you are not required to use a MessageQueue if you don't need
            // one, because if someone tells your Addin to "Quit", then that
            // condition should be handled automatically by the JavaServerAddin
            // class. However, if you want your Addin to respond to custom
            // commands (like "Tell MyAddin Explode", or whatever), you have
            // to maintain a MessageQueue.
            mq = new MessageQueue();
            mqError = mq.create(qName, 0, 0); // use like MQCreate in API

            if (mqError != NOERROR) {
                // if there was an error creating the MessageQueue, just exit
                // (this could just mean that there's already an instance of
                // this Addin loaded)
                consolePrint("Error creating the Message Queue. Exiting...");
                doCleanUp(taskID, mq);
                return;
            }

            // if we got here, we must be running
            AddInSetStatusLine(taskID, "Idle");

            // open the MessageQueue, and wait for instructions
            mqError = mq.open(qName, 0); // use like MQOpen in API
            if (mqError != NOERROR) {
                // if we can't open the MessageQueue, we should exit
                consolePrint("Error opening the Message Queue. Exiting...");
                doCleanUp(taskID, mq);
                return;
            }

            while ((addInRunning()) && (mqError != ERR_MQ_QUITTING)) {
                // in case this is a non-preemptive operating system...
                OSPreemptOccasionally();

                // wait half a second (500 milliseconds) for a message,
                // then check for other conditions -- use 0 as the last
                // parameter to wait forever. You can use a longer interval
                // if you're not checking for any of the AddInElapsed
                // conditions -- otherwise you should keep the timeout to
                // a second or less (see comments below)
                mqError = mq.get(qBuffer, MQ_MAX_MSGSIZE, MQ_WAIT_FOR_MSG, 500);

                if (mqError == NOERROR) {
                    // if we got a message in the queue, process it
                    AddInSetStatusLine(taskID, "Processing Command");
                    processMsg(qBuffer);
                    AddInSetStatusLine(taskID, "Idle");
                }

                // just for fun, display messages every once in a while.
                // NOTE: the AddInMinutesHaveElapsed and AddInSecondsHaveElapsed
                // functions seem to depend on a rigid calculation to return
                // a "true" value, so if you are waiting for a message with
                // a MessageQueue.get call, keep the timeout value short
                // (preferably a second or less). Otherwise, you can run
                // into a situation where you want to do something every
                // 30 seconds, but the timeout in the mq.get call only lets
                // you check the AddInSecondsHaveElapsed return value at 29
                // seconds or 31 seconds or something, and then the call will
                // return "false" even if you haven't done anything in over
                // 30 seconds. It will only return "true" at EXACTLY 30 seconds.
                // Likewise, the AddInMinutesHaveElapsed is just a macro that
                // calls AddInSecondsHaveElapsed(60), so it has the same
                // behavior (it only returns "true" in EXACT 60 second intervals).
                if (AddInDayHasElapsed()) {
                    AddInSetStatusLine(taskID, "Doing Daily Stuff");
                    consolePrint(progName + ": Another day has passed...");
                    AddInSetStatusLine(taskID, "Idle");
                } else if (AddInHasMinutesElapsed(3)) {
                    //AddInSetStatusLine(taskID, "Doing 10-Minute Stuff");
                    consolePrint(progName + ": 3 more minutes have gone by...");
                    //AddInSetStatusLine(taskID, "Idle");
                } else if (AddInHasSecondsElapsed(30)) {
                    //AddInSetStatusLine(taskID, "Doing 30-Second Stuff");
                    consolePrint(progName + ": 30 seconds, and all is well...");
                    //AddInSetStatusLine(taskID, "Idle");
                }
            }
            // once we've exited the loop, the task is supposed to terminate,
            // so we should clean up
            doCleanUp(taskID, mq);
        } catch (Exception ne) {
            ne.printStackTrace();
        }

    }

    /* the consolePrint method, which is a tiny wrapper around the
     AddInLogMessageText method (because AddInLogMessageText requires
     a second parameter of 0, and I always forget to type it) */
    private void consolePrint(String msg) {
        AddInLogMessageText(msg, 0);
    }

    /* the doCleanUp method, which performs all the tasks we should do when
     the Addin terminates */
    private void doCleanUp(int taskID, MessageQueue mq) {
        try {
            AddInSetStatusLine(taskID, "Terminating...");
            consolePrint("Stopping " + addinName + "...");
            AddInDeleteStatusLine(taskID);
            mq.close(0);
            consolePrint(addinName + " has terminated.");
        } catch (Exception e) {
        }

    }

    /* the processMsg method, which translates and reacts to user commands,
     like "TELL JavaAddinTest THIS THAT" (where "THIS" and "THAT" are the
     messages we'll see in the queue) */
    private int processMsg(StringBuffer qBuffer) {
        StringTokenizer st;
        String token;
        int tokenCount;

        st = new StringTokenizer(qBuffer.toString());
        tokenCount = st.countTokens();

        // do a quick error check
        if (tokenCount == 0) {
            displayHelp();
            return -1;
        }

        // get the first token, and check it against our known list of arguments
        token = st.nextToken();

        // ? or HELP should display a help screen
        if ((token.equalsIgnoreCase("?")) || (token.equalsIgnoreCase("HELP"))) {
            displayHelp();
            return 0;
        }

        // VER should display the version of Notes we're running
        if (token.equalsIgnoreCase("VER")) {
            return displayNotesVersion();
        }

        // DBSIZE <dbname> should display the size of a particular database
        if (token.equalsIgnoreCase("DBSIZE")) {
            token = st.nextToken();
            return displayDbSize(token);
        }

        // QUIT and EXIT will stop the Addin
        if ((token.equalsIgnoreCase("QUIT")) || (token.equalsIgnoreCase("EXIT"))) {
            // automatically handled by the system
            return 0;
        }

        // if we got here, the user gave us an unknown argument, so we should
        // just display the help screen
        consolePrint("Unknown argument for " + addinName + ": " + token);
        displayHelp();
        return -1;

    }

    /* the displayHelp method simply shows a little Help screen on the console */
    private void displayHelp() {
        consolePrint(addinName + " Usage:");
        consolePrint("Tell " + progName + " HELP  -- displays this help screen");
        consolePrint("Tell " + progName + " VER  -- displays the Notes version of this server");
        consolePrint("Tell " + progName + " DBSIZE <dbname>  -- displays the size of a given database");
        consolePrint("Tell " + progName + " QUIT  -- terminates this addin");
    }

    /* the displayNotesVersion method, which just prints the Notes version
     that we're running */
    private int displayNotesVersion() {
        int retVal = 0;
        Session session = null;

        try {
            session = NotesFactory.createSession();
            String ver = session.getNotesVersion();
            consolePrint(progName + " - Domino version: " + ver);
        } catch (NotesException e) {
            consolePrint(progName + " - Notes Error getting Notes version: " + e.id + " " + e.text);
            retVal = -1;
        } catch (Exception e) {
            consolePrint(progName + " - Java Error getting Notes version: " + e.getMessage());
            retVal = -1;
        }

        // keep the memory clean
        try {
            session.recycle();
        } catch (Exception e) {
        }

        return retVal;
    }

    /* the displayDbSize method, which simply opens a database and displays
     its size */
    private int displayDbSize(String dbName) {
        int retVal = 0;
        Session session = null;
        Database db = null;

        try {
            session = NotesFactory.createSession();
            db = session.getDatabase(null, dbName);
            if (!db.isOpen()) {
                consolePrint(progName + " - Database " + dbName + " not found or is not accessible");
                retVal = -1;
            } else {
                double dbSize = db.getSize();
                consolePrint(progName + " - Database " + dbName + " is " + (int) dbSize + " bytes");
            }
        } catch (NotesException e) {
            consolePrint(progName + " - Notes Error getting database size: " + e.id + " " + e.text);
            retVal = -1;
        } catch (Exception e) {
            consolePrint(progName + " - Java Error getting database size: " + e.getMessage());
            retVal = -1;
        }

        // keep the memory clean
        try {
            if (db != null)
                db.recycle();
            session.recycle();
        } catch (Exception e) {
        }

        return retVal;
    }

}