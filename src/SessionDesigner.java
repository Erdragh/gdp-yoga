import yoga.data.Database;
import yoga.data.Pose;
import yoga.data.SessionElement;
import yoga.data.Transition;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;

public class SessionDesigner {

  private static int desiredDuration = 0;

  private static Pose[] poses;
  private static Transition[] transitions;
  public static void main(String[] args) {
    if (args.length != 3) {
      System.err.println("Usage: java SessionDesigner <seconds> /path/to/yoga_poses.json /path/to/yoga_transitions.json");
      return;
    }
    if (args[0].matches("[0-9]+")) {
      desiredDuration = Integer.parseInt(args[0]);
    } else {
      System.err.println("Usage: java SessionDesigner <seconds> /path/to/yoga_poses.json /path/to/yoga_transitions.json");
      return;
    }

    try {
      poses = Database.importYogaPoses(Path.of(args[1]));
    } catch (IOException e) {
      System.err.println("Something went wrong when trying to access " + args[1]);
    }
    try {
      transitions = Database.importYogaTransitions(Path.of(args[2]));
    } catch (IOException e) {
      System.err.println("Something went wrong when trying to access " + args[2]);
    }

    computePoses();
  }

  private static void computePoses() {
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

  private static SessionElement[] getBestSessionForPose(Pose pose, ArrayList<SessionElement> elementsSoFar) {
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
    return bestFoundSessionSoFar;
  }

  private static SessionElement[] getBestSessionForTransition(Transition transition, ArrayList<SessionElement> elementsSoFar) {
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
  
  private static int getLengthOfSession(SessionElement[] session) {
    int length = 0;
    for (SessionElement e : session) length += e.getDurationInSeconds();
    return length;
  }

  private static void printSession(SessionElement[] session) {
    for (int i = 0; i < session.length; i++) {
      System.out.print((i + 1) + ". ");
      System.out.println(session[i]);
    }
    System.out.println("Total duration: " + getLengthOfSession(session) + "/" + desiredDuration);
  }
}