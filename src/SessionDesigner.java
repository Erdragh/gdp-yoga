import yoga.data.Database;
import yoga.data.Pose;
import yoga.data.SessionElement;
import yoga.data.Transition;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The class containing the main method,
 * but not much else
 * @author Jan Bayer
 */
public class SessionDesigner {
  /**
   * How to use the program.
   * Stored in a String, so I don't have to copy-paste
   * it wherever needed.
   */
  private static final String USAGE = "Usage: java SessionDesigner <seconds> /path/to/yoga_poses.json /path/to/yoga_transitions.json (-accelerated)";
  /**
   * The <code>SessionCompute</code> instance responsible
   * for the current run.
   */
  private static SessionCompute sessionCompute;
  public static void main(String[] args) {
    // local variable declaration, so it can
    // be used later on outside the if clause where it is set.
    int desiredDuration = 0;
    // cancel the program if there aren't enough arguments
    if (args.length < 3) {
      System.err.println(USAGE);
      return;
    }
    // if the specified duration can be parsed, store
    // it. Otherwise, cancel the program.
    if (args[0].matches("[0-9]+")) {
      desiredDuration = Integer.parseInt(args[0]);
    } else {
      System.err.println(USAGE);
      return;
    }

    // local variable declaration declared here,
    // so it can be used outside the if clause where it's set
    boolean accelerated = false;

    // if we have more than 3 arguments check if one of them is the
    // accelerated flag. This allows for more options in the future.
    if (args.length > 3) {
      for (int i = 3; i < args.length; i++) {
        if (args[i].equals("-accelerated")) accelerated = true;
      }
    }

    // create the session compute instance which will do the actual calculations.
    sessionCompute = new SessionCompute(desiredDuration, accelerated, args[1], args[2]);
    long then = System.nanoTime();
    // do the computation
    sessionCompute.computePoses();
    long now = System.nanoTime();
    // output how long the process took.
    System.out.println("Duration: " + ((now - then) / 1_000_000_000d) + "s");
  }
}

/**
 * the class responsible for the actual computation
 * of the yoga session.
 * @author Jan Bayer
 */
class SessionCompute {

  /**
   * the desired Duration for the yoga session that will
   * be computed.
   */
  private int desiredDuration = 0;
  /**
   * whether memoization should be used during the computation.
   */
  private boolean accelerated = false;
  /**
   * a list storing all records used for memoization.
   * These store from which pose they start, the time
   * they take and the actual elements.
   */
  private List<MemoizationRecord> memoizationRecords = new ArrayList<>();
  /**
   * an array storing all available poses.
   */
  private Pose[] poses;
  /**
   * an array storing all possible transitions.
   */
  private Transition[] transitions;

  /**
   * Your average constructor. Opens files.
   * @param desiredDuration how long the desired yoga session should be
   * @param accelerated whether memoization should be used
   * @param posesPath the path to the json file containing information about the yoga sessions.
   * @param transitionsPath the path to the json file containing information about the yoga transitions.
   */
  public SessionCompute(int desiredDuration, boolean accelerated, String posesPath, String transitionsPath) {
    this.desiredDuration = desiredDuration;
    this.accelerated = accelerated;

    // Open the JSON files and parse them.
    try {
      poses = Database.importYogaPoses(Path.of(posesPath));
    } catch (IOException e) {
      System.err.println("Something went wrong when trying to access " + posesPath);
    }
    try {
      transitions = Database.importYogaTransitions(Path.of(transitionsPath));
    } catch (IOException e) {
      System.err.println("Something went wrong when trying to access " + transitionsPath);
    }
  }

  /**
   * this is the starting point of the computation process.
   */
  public void computePoses() {
    // if we don't have all the info we need, cancel the process.
    if (poses == null || transitions == null) return;

    // local variables storing the best session we found so far.
    SessionElement[] bestFoundSessionSoFar = new SessionElement[]{};
    int bestFoundSessionLengthSoFar = 0;

    // try every pose. This won't actually try everything, unless
    // there isn't a perfect match.
    for (var pose : poses) {
      // this is where the fun begins... - Anakin Skywalker
      // here we call the method which contains the recursive logic.
      // We will get the best available session for this pose and an
      // empty session so far (new ArrayList<>())
      var session = getBestSessionForPose(pose, new ArrayList<>());
      // check the length of the session.
      int sessionLength = getLengthOfSession(session);
      if (sessionLength == desiredDuration) {
        // if it is a perfect match, store it and break out of the loop,
        // because we already found the perfect session.
        bestFoundSessionLengthSoFar = sessionLength;
        bestFoundSessionSoFar = session;
        break;
      } else if (Math.abs(desiredDuration - sessionLength) < Math.abs(desiredDuration - bestFoundSessionLengthSoFar)) {
        // if it isn't a perfect match though, we need to check whether we're closer than before
        // and if so store the new session as the best one so far.
        bestFoundSessionLengthSoFar = sessionLength;
        bestFoundSessionSoFar = session;
      }
    }
    // after the recursive process is done, we just print the session.
    printSession(bestFoundSessionSoFar);
  }

  /**
   * A part of the recursive computation process which returns the best
   * remaining session for a given pose and what the session has been
   * so far.
   * @param pose the pose that is being checked.
   * @param elementsSoFar the session so far.
   * @return the best available session from this point on.
   */
  private SessionElement[] getBestSessionForPose(Pose pose, ArrayList<SessionElement> elementsSoFar) {
    // add the new pose to the session.
    elementsSoFar.add(pose);
    // the duration of this new session including the latest pose.
    int duration = getLengthOfSession(elementsSoFar.toArray(new SessionElement[0]));

    if (duration == desiredDuration) {
      // if we are now a perfect match, we can immediately return
      // the session as is.
      return elementsSoFar.toArray(new SessionElement[0]);
    } else if (duration > desiredDuration) {
      // if we've overshot the desired duration we need to check
      // whether the current one or the one without the last two
      // elements is the best/closest one.

      // the difference for the current session.
      int differenceHere = duration - desiredDuration;
      // the difference for the last version of the session (meaning without the current pose and the last transition)
      int differenceLast = Math.abs((duration - pose.getDurationInSeconds() - elementsSoFar.get(elementsSoFar.size() - 2).getDurationInSeconds()) - desiredDuration);
      // if the previous state of the session was better, we need to return that
      if (differenceHere > differenceLast) {
        // create a temporary list in which we will store
        // the previous state of the session.
        var tempSessionList = new ArrayList<SessionElement>();
        // add every element except for the last two from the current
        // session into the temp array. This effectively removes the last transition and pose.
        // I'm very certain it's possible to do this more efficiently, but it's fast enough, so
        // I won't bother.
        for (int i = 0; i < elementsSoFar.size()-2; i++) {
          tempSessionList.add(elementsSoFar.get(i));
        }
        return tempSessionList.toArray(new SessionElement[0]);
      }
      // return the current state of the session.
      return elementsSoFar.toArray(new SessionElement[0]);
    }

    // ------------ Memoization Part ------------

    // how much time we still have left to fill.
    int remainingTime = desiredDuration - duration;
    if (accelerated) {
      // if we are allowed to use memoization we can check the records
      // we have
      for (var record : memoizationRecords) {
        // if the remaining time matches the one of the record and
        // the record is actually for coming from this pose, we can return
        // it and not have to do the whole recursive part below again,
        // since we already calculated the best possible remaining
        // session for the situation we're in.

        // It's quite possible that the storing and accessing of these
        // records could be made more performant than just looping through them,
        // but it's quick enough, so I didn't bother.
        if (record.from().equals(pose) && record.remainingTime() == remainingTime) {
          return record.bestSession();
        }
      }
    }

    // -------- End Memoization Part ------------

    // local variables storing the best session we found so far.
    SessionElement[] bestFoundSessionSoFar = new SessionElement[]{};
    int bestFoundSessionLengthSoFar = 0;

    // try out every available transition
    for (var transition : transitions) {
      // if a transition doesn't come from the current pose, we can just
      // skip this transition.
      if (!transition.getFrom().equals(pose)) continue;

      // this is what calculates the best session for the transition.
      // I use a clone of the ArrayList here, because I want to use the state it's in before calling this method
      // for the next time we're at this point in the loop. Yes this will create a lot of clones,
      // with a million more on the way, but it works and is quick enough, so I didn't bother.
      var clonedList = (ArrayList<SessionElement>) elementsSoFar.clone();
      clonedList.add(transition);
      var session = getBestSessionForPose(transition.getTo(), clonedList);

      // check the length of the session we got.
      int sessionLength = getLengthOfSession(session);
      if (sessionLength == desiredDuration) {
        // if it's the perfect length immediately return it.
        return session;
      } else if(Math.abs(desiredDuration - sessionLength) < Math.abs(desiredDuration - bestFoundSessionLengthSoFar)) {
        // otherwise, only store it if it's got a smaller difference from the desired duration than we previously had.
        bestFoundSessionLengthSoFar = sessionLength;
        bestFoundSessionSoFar = session;
      }
    }
    // if we're allowed to use memoization we can store the best session for this pose and remaining time
    // in the memoization list for future use.
    if (accelerated) memoizationRecords.add(new MemoizationRecord(pose, remainingTime, bestFoundSessionSoFar));
    return bestFoundSessionSoFar;
  }

  /**
   * calculates the length of a given session
   * @param session the session
   * @return the length of the session
   */
  private int getLengthOfSession(SessionElement[] session) {
    int length = 0;
    // simply loop over every element of the session and add its duration to the total
    for (SessionElement e : session) length += e.getDurationInSeconds();
    return length;
  }

  /**
   * prints a session into the standard output
   * @param session the session that will be printed
   */
  private void printSession(SessionElement[] session) {
    for (int i = 0; i < session.length; i++) {
      // print the number of the element
      System.out.print((i + 1) + ". ");
      // print the element
      System.out.println(session[i]);
    }
    // print the duration of the session in comparison to the desired duration
    System.out.println("Total duration: " + getLengthOfSession(session) + "/" + desiredDuration);
  }
}

/**
 * a record storing information for use in the Memoization process.
 * @param from the pose from which on this record stores the best session
 * @param remainingTime the duration of the session this record stores
 * @param bestSession the session itself.
 */
record MemoizationRecord(Pose from, int remainingTime, SessionElement[] bestSession) {}