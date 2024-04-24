package sampleagent;

//import java.util.ArrayList;

import org.apache.log4j.Logger;
import rescuecore.commands.AKSay;
import rescuecore2.messages.Command;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
//import rescuecore2.standard.entities.RescueRobot;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.EntityID;
import sample.AbstractSampleAgent;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

public class SampleRescueRobot extends AbstractSampleAgent<PoliceForce> {
    private static final Logger LOG = Logger.getLogger(SampleRescueRobot.class);
    private static final String DISTANCE_KEY = "clear.repair.distance";

    private int distance;

    @Override
    public String toString() {
        return "Sample Rescue Robot";
    }

    @Override
    protected void postConnect() {
        super.postConnect();
        model.indexClass(StandardEntityURN.ROAD);
        distance = (int) Math.round(config.getIntValue(DISTANCE_KEY) * 0.96);
    }


    @Override
    protected EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
        return EnumSet.of(StandardEntityURN.POLICE_FORCE);
    }

    @Override
    protected void think(int time, ChangeSet changed, Collection<Command> heard) {
        if(time == config.getIntValue(kernel.KernelConstants.IGNORE_AGENT_COMMANDS_KEY)) {
          //Subscribe to channel 1
          sendSubscribe(time, 1);
        }
        for (Command next: heard) {
          LOG.debug("Heard " + next);
          if (next instanceof AKSay say) {
              String message = new String(say.getMessage());
              // check if the message is from the centre
            if(message.startsWith("Go towards the civilians at ")) {
              //extract the coordinates from the message
              String[] parts = message.split(" ");
              int x = Integer.parseInt(parts[4]);
              int y = Integer.parseInt(parts[8]);
              //Send the message
              LOG.info("Received the coordinates of civilians at (" + x + ", " + y + ")");
              //Go towards the civilians
              clearBlockadeForRescue(time, x, y);
            }
          }
        }
        //is robot near blockade
        Blockade target = getTargetBlockade();
        if (target != null) {
            LOG.info("Clearing blockade " + target);
            sendSpeak(time, 1, ("Clearing blockade at " + target).getBytes());
            //sendClear(time, target.getX(), target.getY());
            List<Line2D> lines = GeometryTools2D.pointsToLines(
                    GeometryTools2D.vertexArrayToPoints(target.getApexes()), true);
            double best = Double.MAX_VALUE;
            Point2D best_point = null;
            Point2D origin = new Point2D(me().getX(), me().getY());
            for (Line2D next : lines) {
                Point2D closest = GeometryTools2D.getClosestPointOnSegment(next, origin);
                double distance = GeometryTools2D.getDistance(origin, closest);
                if (distance < best) {
                    best = distance;
                    best_point = closest;
                }
            }
            Vector2D vec = best_point.minus(new Point2D(me().getX(), me().getY()));
            vec = vec.normalised().scale(1000000);
            //clear the blockade
            sendClear(time, (int) (me().getX() + vec.getX()), (int) (vec.getY() + me().getY()));
            LOG.info("HEllo world");
        }
    }

    private void clearBlockadeForRescue(int time, int targetX, int targetY) {
      LOG.info("Clearing blockades ");
      //Find nearest blockade
      Blockade target = getTargetBlockade();
      if (target != null) {
        //move towards the target
        List<EntityID> path = search.breadthFirstSearch(me().getPosition(), target.getID());
        if(target != null) {
            LOG.info("Moving to target");
            sendMove(time, path);
            return;
        }
      }
      //if no blockades are found or cannot reach, move towards the coordinates
      LOG.debug("Did not find any blockades");
      LOG.info("Moving towards the coordinates");
      sendMove(time, buildingIDs, targetX, targetY);

      if (noMoreBlockades()) {
          sendSpeak(time, 1, "Civilians have been evacuated. No more assistance required".getBytes());
      }

    }

    private boolean noMoreBlockades() {
        for (EntityID next : buildingIDs) {
            Area area = (Area) model.getEntity(next);
            if(area != null && area.isBlockadesDefined() && !area.getBlockades().isEmpty()) {
                return false;
            }
        }

        return true;
    }

     //go to the nearest blockade
     private Blockade getTargetBlockade() {
        LOG.info("Looking for target civilian");
        Area location = (Area) location();
        LOG.debug("Looking in current location");
        Blockade res = getTargetBlockade(location, distance);
        if (res != null) {
            return res;
        }
        LOG.debug("Looking in neighboring locations");
        for (EntityID next : location.getNeighbours()) {
            location = (Area) model.getEntity(next);
            res = getTargetBlockade(location, distance);
            if (res != null) {
                return res;
            }
        }
        return null;

    }

    private Blockade getTargetBlockade(Area area, int maxDistance) {
        // Logger.debug("Looking for nearest blockade in " + area);
        if (area == null || !area.isBlockadesDefined()) {
          // Logger.debug("Blockades undefined");
          return null;
        }
        List<EntityID> ids = area.getBlockades();
        // Find the first blockade that is in range.
        int x = me().getX();
        int y = me().getY();
        for (EntityID next : ids) {
          Blockade b = (Blockade) model.getEntity(next);
          double d = findDistanceTo(b, x, y);
          // Logger.debug("Distance to " + b + " = " + d);
          if (maxDistance < 0 || d < maxDistance) {
            // Logger.debug("In range");
            return b;
          }
        }
        // Logger.debug("No blockades in range");
        return null;
      }

    private int findDistanceTo(Blockade b, int x, int y) {
        List<Line2D> lines = GeometryTools2D.pointsToLines(
            GeometryTools2D.vertexArrayToPoints(b.getApexes()), true);
        double best = Double.MAX_VALUE;
        Point2D origin = new Point2D(x,y);
        for (Line2D next : lines) {
            Point2D closest = GeometryTools2D.getClosestPointOnSegment(next, origin);
            double d = GeometryTools2D.getDistance(origin, closest);
            // Logger.debug("Next line: " + next + ", closest point: " + closest + ",
            // distance: " + d);
            if (d < best) {
              best = d;
              // Logger.debug("New best distance");
            }
      
          }
          return (int) best;
    }

    
}
