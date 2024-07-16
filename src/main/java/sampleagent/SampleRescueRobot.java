package sampleagent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.apache.log4j.Logger;

import rescuecore.commands.AKSay;
import rescuecore.commands.AKTell;
import rescuecore2.messages.Command;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.RescueRobot;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.standard.messages.AKSpeak;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.EntityID;
import sample.AbstractSampleAgent;

public class SampleRescueRobot extends AbstractSampleAgent<RescueRobot> {
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
        model.indexClass(StandardEntityURN.ROAD, StandardEntityURN.DRONE, StandardEntityURN.RESCUE_ROBOT);
        distance = (int) Math.round(config.getIntValue(DISTANCE_KEY) * 0.97);
    }

    @Override
    protected void think(int time, ChangeSet changed, Collection<Command> heard) {
        if(time == config.getIntValue(kernel.KernelConstants.IGNORE_AGENT_COMMANDS_KEY)) {
          //Subscribe to channel 1
          sendSubscribe(time, 1);
        }
//        handleHearCommands(time, heard);
        for (Command next : heard) {
            LOG.info("Heard" + next);
            if (next instanceof AKSpeak) {
                AKSpeak tell = (AKSpeak) next;
                String message = new String(tell.getContent());
                if (message.startsWith("Coordinates")) {
                    try {
//                        handleGoCommand(time, message);
                        LOG.info("Going to coordinates");
                    } catch (NumberFormatException e) {
                        LOG.error("Failed to parse coordinates: ", e);
                    }
                }
            }
        }

        // Am I near a blockade?
        Blockade target = getTargetBlockade();
        if (target != null) {
            LOG.info("Clearing blockade " + target);
//            sendMessageToPolice(1);
            sendSpeak(time, 1, ("Clearing " + target).getBytes());
            // sendClear(time, target.getX(), target.getY());
            List<Line2D> lines = GeometryTools2D.pointsToLines(
                    GeometryTools2D.vertexArrayToPoints(target.getApexes()), true);
            double best = Double.MAX_VALUE;
            Point2D bestPoint = null;
            Point2D origin = new Point2D(me().getX(), me().getY());
            for (Line2D next : lines) {
                Point2D closest = GeometryTools2D.getClosestPointOnSegment(next,
                        origin);
                double d = GeometryTools2D.getDistance(origin, closest);
                if (d < best) {
                    best = d;
                    bestPoint = closest;
                }
            }
            @SuppressWarnings("null")
            Vector2D v = bestPoint.minus(new Point2D(me().getX(), me().getY()));
            v = v.normalised().scale(1000000);
            sendClear(time, (int) (me().getX() + v.getX()),
                    (int) (me().getY() + v.getY()));
            return;
        }
        //Am I near a civilian?


        // Plan a path to a blocked area

        List<EntityID> path = search.breadthFirstSearch(me().getPosition(),
                getBlockedRoads());
        if (path != null) {
            LOG.info("Moving to target");
            Road r = (Road) model.getEntity(path.get(path.size() - 1));
            Blockade b = getTargetBlockade(r, -1);
            sendMove(time, path, b.getX(), b.getY());
            LOG.debug("Path: " + path);
            LOG.debug("Target coordinates: " + b.getX() + ", " + b.getY());
            return;
        }
        LOG.debug("Couldn't plan a path to a blocked road");
        LOG.info("Moving randomly");
        sendMove(time, randomWalk());
    }

//    private void handleHearCommands(int time, Collection<Command> heard) {
//        for (Command next : heard) {
//            LOG.debug("Heard " + next);
//            if (next instanceof AKTell say) {
//                String message = new String(say.getMessage());
//                if (message.startsWith("Go")) {
//                    try {
//                        handleGoCommand(time, message);
//                    } catch (NumberFormatException e) {
//                        LOG.error("Failed to parse coordinates from message: " + message, e);
//                    }
//                }
//            }
//        }
//    }

    private void handleGoCommand(int time, String message) {
        String[] parts = message.split(" ");
        LOG.info("message length: " + parts.length + " " + message);
        if(parts.length == 3) {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            LOG.info("Received coordinates of civilians at: " + x + ", " + y);
            List<EntityID> path = search.breadthFirstSearch(me().getPosition(), getBlockedRoads());
            if (path != null) {
                LOG.info("Moving to target");
                Road road = (Road) model.getEntity(path.get(path.size() - 1));
                Blockade blockade = getTargetBlockade(road, -1);
                if (road != null) {
                    int targetX = blockade.getX();
                    int targetY = blockade.getY();
                    sendMove(time, path, targetX, targetY);
                    LOG.debug("Path to coordinates: " + path);
                } else {
                    LOG.error("Target road is null, moving randomly");
                    sendMove(time, randomWalk());
                }
            }
        }
    }


    private List<EntityID> getTargetRoads() {
        Collection<StandardEntity> e = model.getEntitiesOfType(StandardEntityURN.ROAD);
        List<EntityID> result = new ArrayList<EntityID>();
        for (StandardEntity next : e) {
            Road r = (Road) next;
            result.add(r.getID());
        }
        return result;
    }

    @Override
    protected EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
        return EnumSet.of(StandardEntityURN.RESCUE_ROBOT);
    }

    private List<EntityID> getBlockedRoads() {
        Collection<
                StandardEntity> e = model.getEntitiesOfType(StandardEntityURN.ROAD);
        List<EntityID> result = new ArrayList<EntityID>();
        for (StandardEntity next : e) {
            Road r = (Road) next;
            if (r.isBlockadesDefined() && !r.getBlockades().isEmpty()) {
                result.add(r.getID());
            }
        }
        return result;
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
