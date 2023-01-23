public class SessionDesigner {

  public static int desiredDuration = 0;
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
    System.out.println(desiredDuration);
  }
}