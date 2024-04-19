package sampleagent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
// import java.util.Set;

import org.apache.log4j.Logger;
// import org.dom4j.Entity;

// import com.vividsolutions.jts.geom.impl.PackedCoordinateSequence.Double;

import rescuecore2.messages.Command;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
// import rescuecore2.misc.geometry.Vector2D;
// import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Civilian;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.standard.entities.Drone;
// import rescuecore2.standard.entities.StandardPropertyURN;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.EntityID;
import sample.AbstractSampleAgent;
import sample.DistanceSorter;

public class SampleDrone extends AbstractSampleAgent<Drone> {

    private static final Logger LOG = Logger.getLogger(SampleDrone.class);
    private static final int VISION_RANGE = 500;
    private Collection<EntityID> unexploredBuildings;
    private static final String DISTANCE_KEY = "clear.repair.distance";

    private int distance;


    @Override
    public String toString() {
        return "Sample drone";
    }

    @Override
    protected void postConnect() {
        super.postConnect();
        model.indexClass(StandardEntityURN.CIVILIAN, StandardEntityURN.BUILDING);
        unexploredBuildings = new HashSet<EntityID>(buildingIDs);
    }

    @Override
    protected void think(int time, ChangeSet changed, Collection<Command> heard) {
        if(time == config.getIntValue(kernel.KernelConstants.IGNORE_AGENT_COMMANDS_KEY)) {
            //subscribe to channel 1
            sendSubscribe(time, 1);
        }
        for (Command next : heard){
            LOG.debug("Heard " + next);
        }

        //if near a blockade, go through 
        Blockade target = getTargetBlockade();
        if (target != null) {
            LOG.info("Going through the blockade at " + target.getID());
            goThroughBlockade(time, target);
            return;
        }

        //go through targets and see if there are any civilians
        for (Human next : getTargets()) {
            if(next.getPosition().equals(location().getID())) {
                //Target civilians that might need rescuing 
                if((next instanceof Civilian) && next.getBuriedness() == 0
                    && !(location() instanceof Refuge)) {
                        int x = me().getX();
                        int y = me().getY();
                        LOG.info("Civilians detected at: " + x + ", " + y);
                        //Send coordinates to police office 
                        sendCoordinatesToPolice(1, x, y);
                        return;
                    }
            }
        }

        // Keep exploring
        List<EntityID> path = search.breadthFirstSearch(me().getPosition(), unexploredBuildings);
        if(path != null) {
            LOG.info("Searching map");
            sendMove(time, path);
            return;
        }
        LOG.info("Moving in random direction");
        //sendMove(time, randomWalk());
        
    }


    @Override
    protected EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
        return EnumSet.of(StandardEntityURN.AMBULANCE_TEAM);
    }

    private void goThroughBlockade(int time, Blockade blockade) {
        sendClear(time, blockade.getID());
        sendMove(time, randomWalk());
    }

    private void sendCoordinatesToPolice(int time, int x, int y) {
        Collection<StandardEntity> entities = model.getEntitiesOfType(StandardEntityURN.POLICE_OFFICE);
        for (StandardEntity entity : entities) {
            int policeOfficeId = entity.getID().getValue();
            sendSpeak(time, policeOfficeId, ("Civilians detected at " + x + ", " + y).getBytes());
        }        
    }

    private List<Human> getTargets() {
        List<Human> targets = new ArrayList<Human>();
        for (StandardEntity next: model.getEntitiesOfType(
            StandardEntityURN.CIVILIAN)) {
                Human human = (Human) next;
                if(human == me()) {
                    continue;
                }
                if (human.isHPDefined() && human.isBuriednessDefined() && human.isDamageDefined()
                    && human.isPositionDefined() && human.getHP() > 0 && (human.getBuriedness() > 0 || human.getDamage() > 0)) {
                        targets.add(human);
                }
        }
        Collections.sort(targets, new DistanceSorter(location(), model));
        return targets;
    }

    private void updateUnexploredBuildings(ChangeSet changed) {
        for(EntityID next : changed.getChangedEntities()) {
            unexploredBuildings.remove(next);
        }
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
        if(area == null || !area.isBlockadesDefined()) {
            return null;
        }
        List<EntityID> ids = area.getBlockades();
        //find nearest blockade that is in range 
        int x = me().getX();
        int y = me().getY();
        for (EntityID next : ids) {
            Blockade b = (Blockade) model.getEntity(next);
            double distance = findDistanceTo(b, x, y);
            if(maxDistance < 0 || distance < maxDistance) {
                return b;
            }
        }
        LOG.debug("No blockades found");
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
