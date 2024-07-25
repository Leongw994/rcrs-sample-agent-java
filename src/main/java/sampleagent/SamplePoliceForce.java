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
import rescuecore2.standard.entities.*;
import rescuecore2.standard.messages.AKSpeak;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.EntityID;
import sample.AbstractSampleAgent;

/**
 * A sample police force agent.
 */
public class SamplePoliceForce extends AbstractSampleAgent<PoliceForce> {

  private static final Logger LOG = Logger.getLogger(SamplePoliceForce.class);
  private static final String DISTANCE_KEY = "clear.repair.distance";

  private int distance;
  private Point2D targetCivilians;

  @Override
  public String toString() {
    return "Sample police force";
  }


  @Override
  protected void postConnect() {
    super.postConnect();
    model.indexClass(StandardEntityURN.ROAD);
    distance = (int) Math.round(config.getIntValue(DISTANCE_KEY) * 0.95);
  }


  @Override
  protected void think(int time, ChangeSet changed, Collection<Command> heard) {
    if (time == config
        .getIntValue(kernel.KernelConstants.IGNORE_AGENT_COMMANDS_KEY)) {
      // Subscribe to channel 1
      sendSubscribe(time, 1);
    }
    for (Command next : heard) {
      LOG.debug("Heard " + next);
      if (next instanceof AKSpeak) {
        AKSpeak speak = (AKSpeak) next;
        String message = new String(speak.getContent());
        if (message.startsWith("Help")) {
          String[] parts = message.split(" ");
          try {
            if (parts.length == 3) {
              int x = Integer.parseInt(parts[1]);
              int y = Integer.parseInt(parts[2]);
              targetCivilians = new Point2D(x, y);
              LOG.info("Going to help civilians at " + x + " " + y);
            }
          } catch (NumberFormatException ex) {
            LOG.error("Failed to parse coordinates from message: " + message + " ERROR: " + ex);
          }
        }
      }
    }
    if (targetCivilians != null) {
      moveToCivilians(time);
    } else {
      sendMove(time, randomWalk());
//      sendRest(time);
    }

    if (location() instanceof Refuge) {
      LOG.info("Escorted the civilians to safety!");
    }
    // Am I near a blockade?
//    Blockade target = getTargetBlockade();
//    if (target != null) {
//      LOG.info("Clearing " + target);
//      sendSpeak(time, 1, ("Clearing " + target).getBytes());
//      // sendClear(time, target.getX(), target.getY());
//      List<Line2D> lines = GeometryTools2D.pointsToLines(
//          GeometryTools2D.vertexArrayToPoints(target.getApexes()), true);
//      double best = Double.MAX_VALUE;
//      Point2D bestPoint = null;
//      Point2D origin = new Point2D(me().getX(), me().getY());
//      for (Line2D next : lines) {
//        Point2D closest = GeometryTools2D.getClosestPointOnSegment(next,
//            origin);
//        double d = GeometryTools2D.getDistance(origin, closest);
//        if (d < best) {
//          best = d;
//          bestPoint = closest;
//        }
//      }
//      @SuppressWarnings("null")
//      Vector2D v = bestPoint.minus(new Point2D(me().getX(), me().getY()));
//      v = v.normalised().scale(1000000);
//      sendClear(time, (int) (me().getX() + v.getX()),
//          (int) (me().getY() + v.getY()));
//      return;
//    }
//    // Plan a path to a blocked area
//    List<EntityID> path = search.breadthFirstSearch(me().getPosition(),
//        getBlockedRoads());
//    if (path != null) {
//      LOG.info("Moving to target");
//      Road r = (Road) model.getEntity(path.get(path.size() - 1));
//      Blockade b = getTargetBlockade(r, -1);
//      sendMove(time, path, b.getX(), b.getY());
//      LOG.debug("Path: " + path);
//      LOG.debug("Target coordinates: " + b.getX() + ", " + b.getY());
//      return;
//    }
//    LOG.debug("Couldn't plan a path to a blocked road");
//    LOG.info("Moving randomly");
    sendMove(time, randomWalk());
  }


  @Override
  protected EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
    return EnumSet.of(StandardEntityURN.POLICE_FORCE);
  }

  private void moveToCivilians(int time) {
    List<EntityID> path = search.breadthFirstSearch(me().getPosition(), getRoadID(targetCivilians));
    if (path != null) {
      int x = (int) targetCivilians.getX();
      int y = (int) targetCivilians.getY();
      sendMove(time, path, x, y);
    } else {
      LOG.error("No path to target found, moving randomly");
      sendMove(time, randomWalk());
    }
  }

  private EntityID getRoadID(Point2D p) {
    for (StandardEntity entity : model.getEntitiesOfType(StandardEntityURN.ROAD)) {
      Road road = (Road) entity;
      if (road.getX() == p.getX() && road.getY() == p.getY()) {
        return road.getID();
      }
    }
    //if no exact match is found, return the closest road
    Road closestRoad = null;
    double closestDistance = Double.MAX_VALUE;
    for (StandardEntity entity : model.getEntitiesOfType(StandardEntityURN.ROAD)) {
      Road road = (Road) entity;
      double distance = Math.sqrt(Math.pow(road.getX() - p.getX(), 2) + Math.pow(road.getY() - p.getY(), 2));
      if (distance < closestDistance) {
        closestDistance = distance;
        closestRoad = road;
      }
    }
    return closestRoad != null ? closestRoad.getID() : null;
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


  private Blockade getTargetBlockade() {
    LOG.debug("Looking for target blockade");
    Area location = (Area) location();
    LOG.debug("Looking in current location");
    Blockade result = getTargetBlockade(location, distance);
    if (result != null) {
      return result;
    }
    LOG.debug("Looking in neighboring locations");
    for (EntityID next : location.getNeighbours()) {
      location = (Area) model.getEntity(next);
      result = getTargetBlockade(location, distance);
      if (result != null) {
        return result;
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
    // Logger.debug("Finding distance to " + b + " from " + x + ", " + y);
    List<Line2D> lines = GeometryTools2D.pointsToLines(
        GeometryTools2D.vertexArrayToPoints(b.getApexes()), true);
    double best = Double.MAX_VALUE;
    Point2D origin = new Point2D(x, y);
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