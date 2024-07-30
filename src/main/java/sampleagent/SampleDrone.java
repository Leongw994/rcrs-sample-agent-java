package sampleagent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import org.jfree.chart.block.Block;
import rescuecore2.messages.Command;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.Entity;
import rescuecore2.worldmodel.EntityID;
import sample.AbstractSampleAgent;
import sample.DistanceSorter;

public class SampleDrone extends AbstractSampleAgent<Drone> {

    private static final Logger LOG = Logger.getLogger(SampleDrone.class);

    private static final int    RANDOM_FLY_LENGTH = 2;
    private Collection<EntityID> unexploredBuildings;

    private EntityID targetDestination;

    @Override
    public String toString() {
        return "Sample drone";
    }

    @Override
    protected void postConnect() {
        super.postConnect();
        model.indexClass(StandardEntityURN.FIRE_BRIGADE,
                StandardEntityURN.POLICE_FORCE,
                StandardEntityURN.AMBULANCE_TEAM,
                StandardEntityURN.BUILDING);
        LOG.info("Sample drone connected");
        unexploredBuildings = new HashSet<EntityID>(buildingIDs);
        targetDestination = null;
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
        }
        updateUnexploredBuildings(changed);

//        // Nothing to do
//        List<EntityID> path = null;
//        if ((targetDestination != null) && (!targetDestination.equals(me().getPosition()))) {
//            path = search.breadthFirstSearch(me().getPosition(), targetDestination);
//            if (path != null) {
//                LOG.info("Flying to target destination");
//                sendFly(time, path);
//                return;
//            }
//        }
//
//        if (!unexploredBuildings.isEmpty()) {
//            path = search.breadthFirstSearch(me().getPosition(), unexploredBuildings);
//            targetDestination = path.get(path.size() - 1);
//        } else {
//            //if no unexplored buildings, go to random location
//            List<StandardEntity> allAreasEntities = new ArrayList<>(model.getEntitiesOfType(
//                    StandardEntityURN.ROAD, StandardEntityURN.BUILDING, StandardEntityURN.REFUGE));
//            List<EntityID> allAreas = new ArrayList<>();
//            for (StandardEntity entity : allAreasEntities) {
//                allAreas.add(entity.getID());
//            }
//            Collections.shuffle(allAreas);
//            targetDestination = allAreas.get(0);
//            path = search.breadthFirstSearch(me().getPosition(), targetDestination);
//        }
//
//        // Move to the target destination
//        if (path != null) {
//            LOG.info("Flying to target destination");
//            sendFly(time, path);
//        }


//        List<EntityID> path = search.breadthFirstSearch(me().getPosition(), unexploredBuildings);
//        if (path != null) {
//            LOG.info("Searching buildings");
//            sendFly(time, path);
////            detectCivilians(time);
//            int x = me().getX();
//            int y = me().getY();
//            LOG.info("Detected civilians at: " + x + ", " + y);
//            String message = String.format("People %d %d", x, y);
//            sendSpeak(time, 1, message.getBytes());
//            return;
//        }
        int x = me().getX();
        int y = me().getY();
        LOG.info("Detected civilians at: " + x + ", " + y);
        String message = String.format("People %d %d", x, y);
        sendSpeak(time, 1, message.getBytes());
        LOG.info("Moving randomly");
        sendFly(time, randomWalk());

    }

    @Override
    protected EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
        return EnumSet.of(StandardEntityURN.DRONE);
    }

    @Override
    protected List<EntityID> randomWalk() {
        List<EntityID> result = new ArrayList<EntityID>( RANDOM_FLY_LENGTH );
        Set<EntityID> seen = new HashSet<EntityID>();
        EntityID current = ( (Robot) me() ).getPosition();
        for ( int i = 0; i < RANDOM_FLY_LENGTH; ++i ) {
            result.add( current );
            seen.add( current );
            List<EntityID> possible = new ArrayList<EntityID>(
                    neighbours.get( current ) );
            Collections.shuffle( possible, random );
            boolean found = false;
            for ( EntityID next : possible ) {
//                if ( seen.contains( next ) ) {
//                    continue;
//                }
                current = next;
                found = true;
                break;
            }
            if ( !found ) {
                // We reached a dead-end.
                break;
            }
        }
        return result;
    }



    private List<Human> getTargets() {
        List<Human> targets = new ArrayList<Human>();
        for (StandardEntity next: model.getEntitiesOfType(
                StandardEntityURN.CIVILIAN)) {
            Human human = (Human) next;
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

    private Collection<EntityID> getAllRoads() {
        Collection<StandardEntity> roads = model.getEntitiesOfType(StandardEntityURN.ROAD);
        Collection<EntityID> roadIDs = new HashSet<>();
        for (StandardEntity road : roads) {
            roadIDs.add(road.getID());
        }
        return roadIDs;
    }

    private void detectCivilians(int time) {
        for (StandardEntity entity : model.getEntitiesOfType(StandardEntityURN.CIVILIAN)) {
            Civilian civilian = (Civilian) entity;
            if (civilian.isHPDefined() && civilian.isBuriednessDefined() && civilian.isDamageDefined()
                && civilian.isPositionDefined()) {
                EntityID location = civilian.getPosition();
                int x = ((Road) model.getEntity(location)).getX();
                int y = ((Road) model.getEntity(location)).getY();
                String message = String.format("Civilians %d %d", x, y);
                LOG.info(message);
                sendSpeak(time, 1, message.getBytes());
            }
        }
    }

}
