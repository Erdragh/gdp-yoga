import yoga.data.Database;
import yoga.data.Pose;
import yoga.data.SessionElement;
import yoga.data.Transition;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SessionDesigner {
  private static final String USAGE = "Usage: java SessionDesigner <seconds> /path/to/yoga_poses.json /path/to/yoga_transitions.json (-accelerated)";

  private static SessionCompute sessionCompute;
  public static void main(String[] args) {
    int desiredDuration = 0;
    if (args.length < 3) {
      System.err.println(USAGE);
      return;
    }
    if (args[0].matches("[0-9]+")) {
      desiredDuration = Integer.parseInt(args[0]);
    } else {
      System.err.println(USAGE);
      return;
    }

    boolean accelerated = false;

    if (args.length > 3) {
      for (int i = 3; i < args.length; i++) {
        if (args[i].equals("-accelerated")) accelerated = true;
      }
    }

    sessionCompute = new SessionCompute(desiredDuration, accelerated, args[1], args[2]);
    sessionCompute.computePoses();
  }
}

class SessionCompute {

  private int desiredDuration = 0;
  private boolean accelerated = false;
  private List<MemoizationRecord> memoizationRecords = new ArrayList<>();
  private Pose[] poses;
  private Transition[] transitions;

  public SessionCompute(int desiredDuration, boolean accelerated, String posesPath, String transitionsPath) {
    this.desiredDuration = desiredDuration;
    this.accelerated = accelerated;

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

  public void computePoses() {
    if (poses == null || transitions == null) return;
    SessionElement[] bestFoundSessionSoFar = new SessionElement[]{};
    int bestFoundSessionLengthSoFar = 0;
    for (var pose : poses) {
      var session = getBestSessionForPose(pose, new ArrayList<>());
      int sessionLength = getLengthOfSession(session);
      if (sessionLength == desiredDuration) {
        bestFoundSessionLengthSoFar = sessionLength;
        bestFoundSessionSoFar = session;
        break;
      } else if (Math.abs(desiredDuration - sessionLength) < Math.abs(desiredDuration - bestFoundSessionLengthSoFar)) {
        bestFoundSessionLengthSoFar = sessionLength;
        bestFoundSessionSoFar = session;
      }
    }
    printSession(bestFoundSessionSoFar);
  }

  private SessionElement[] getBestSessionForPose(Pose pose, ArrayList<SessionElement> elementsSoFar) {
    elementsSoFar.add(pose);
    int duration = getLengthOfSession(elementsSoFar.toArray(new SessionElement[0]));
    if (duration == desiredDuration) {
      return elementsSoFar.toArray(new SessionElement[0]);
    } else if (duration > desiredDuration) {
      int differenceHere = duration - desiredDuration;
      int differenceLast = Math.abs((duration - pose.getDurationInSeconds() - elementsSoFar.get(elementsSoFar.size() - 2).getDurationInSeconds()) - desiredDuration);
      if (differenceHere > differenceLast) {
        var tempSessionList = new ArrayList<SessionElement>();
        for (int i = 0; i < elementsSoFar.size()-2; i++) {
          tempSessionList.add(elementsSoFar.get(i));
        }
        return tempSessionList.toArray(new SessionElement[0]);
      }
      return elementsSoFar.toArray(new SessionElement[0]);
    }

    // ------------

    int remainingTime = desiredDuration - duration;
    for (var record : memoizationRecords) {
      if (record.from().equals(pose) && record.remainingTime() == remainingTime) {
        return record.bestSession();
      }
    }

    // ------------

    SessionElement[] bestFoundSessionSoFar = new SessionElement[]{};
    int bestFoundSessionLengthSoFar = 0;
    for (var transition : transitions) {
      if (!transition.getFrom().equals(pose)) continue;
      var session = getBestSessionForTransition(transition, (ArrayList<SessionElement>) elementsSoFar.clone());
      int sessionLength = getLengthOfSession(session);
      if (sessionLength == desiredDuration) {
        return session;
      } else if(Math.abs(desiredDuration - sessionLength) < Math.abs(desiredDuration - bestFoundSessionLengthSoFar)) {
        bestFoundSessionLengthSoFar = sessionLength;
        bestFoundSessionSoFar = session;
      }
    }
    memoizationRecords.add(new MemoizationRecord(pose, remainingTime, bestFoundSessionSoFar));
    return bestFoundSessionSoFar;
  }

  private SessionElement[] getBestSessionForTransition(Transition transition, ArrayList<SessionElement> elementsSoFar) {
    elementsSoFar.add(transition);
    SessionElement[] bestFoundSessionSoFar = new SessionElement[]{};
    int bestFoundSessionLengthSoFar = 0;
    for (var pose : poses) {
      if (!transition.getTo().equals(pose)) continue;
      var session = getBestSessionForPose(pose, (ArrayList<SessionElement>) elementsSoFar.clone());
      int sessionLength = getLengthOfSession(session);
      if (sessionLength == desiredDuration) {
        return session;
      } else if (Math.abs(desiredDuration - sessionLength) < Math.abs(desiredDuration - bestFoundSessionLengthSoFar)) {
        bestFoundSessionLengthSoFar = sessionLength;
        bestFoundSessionSoFar = session;
      }
    }
    return bestFoundSessionSoFar;
  }

  private int getLengthOfSession(SessionElement[] session) {
    int length = 0;
    for (SessionElement e : session) length += e.getDurationInSeconds();
    return length;
  }

  private void printSession(SessionElement[] session) {
    for (int i = 0; i < session.length; i++) {
      System.out.print((i + 1) + ". ");
      System.out.println(session[i]);
    }
    System.out.println("Total duration: " + getLengthOfSession(session) + "/" + desiredDuration);
  }
}

record MemoizationRecord(Pose from, int remainingTime, SessionElement[] bestSession) {}