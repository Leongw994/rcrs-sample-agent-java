package sampleagent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.apache.log4j.Logger;
import rescuecore2.messages.Command;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.EntityID;
import sample.AbstractSampleAgent;

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
        }
      
    }

     //go to the nearest blockad
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
