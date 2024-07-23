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
    private Set<EntityID> visitedLocations;

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
        visitedLocations = new HashSet<>();
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
        updateUnexploredBuildings(changed);
        //go through targets and see if there are any civilians
        for (Human next : getTargets()) {
            if(next.getPosition().equals(location().getID())) {
                //Find civilians that might need rescuing
                if((next instanceof Civilian) && next.getBuriedness() > 0
                    && !(location() instanceof Refuge)) {
                        int x = me().getX();
                        int y = me().getY();
                        LOG.info("Civilians detected at: X: " + x + ", Y: " + y);
                        //Send coordinates to police office
                        String message = String.format("Coordinates: %d %d", x, y);
                        sendSpeak(time, 1, message.getBytes());
                        return;
                }
            } else {
                //try to move to target
                List<EntityID> path = search.breadthFirstSearch(me().getPosition(), next.getPosition());
                if(path != null){
                    LOG.info("Moving to target");
                    // fly command
                    sendFly(time, path);
                    return;
                }
            }
        }
//
//        // explore unvisited buildings
//        List<EntityID> path = search.breadthFirstSearch(me().getPosition(), unexploredBuildings);
//        if(path != null) {
//            LOG.info("Searching map");
//            sendFly(time, path);
//            return;
//        }

        LOG.info("Flying in random direction");
        sendFly(time, randomWalk());
    }

    @Override
    protected EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
        return EnumSet.of(StandardEntityURN.DRONE);
    }

    @Override
    protected List<EntityID> randomWalk() {
        List<EntityID> result = new ArrayList<EntityID>( RANDOM_FLY_LENGTH );
        Set<EntityID> seen = new HashSet<EntityID>( visitedLocations );
        EntityID current = ( (Robot) me() ).getPosition();

        for (int i = 0; i < RANDOM_FLY_LENGTH; ++i) {
            result.add( current );
            seen.add( current );
            List<EntityID> possible = new ArrayList<EntityID>( neighbours.get( current ) );
            Collections.shuffle(possible, random);
            boolean found = false;
            for ( EntityID next : possible ) {
                if (!seen.contains(next)) {
                    current = next;
                    found = true;
                    break;
                }
            }
            if (!found) {
                if (!possible.isEmpty()) {
                    current = possible.get(random.nextInt(possible.size()));
                    found = true;
                }
            }
//            if (!found) {
//                break;
//            }
        }

        return result;
    }



    private List<Human> getTargets() {
        List<Human> targets = new ArrayList<Human>();
        for (StandardEntity next: model.getEntitiesOfType(
                StandardEntityURN.CIVILIAN, StandardEntityURN.AMBULANCE_TEAM,
                StandardEntityURN.FIRE_BRIGADE, StandardEntityURN.RESCUE_ROBOT)) {
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
}
