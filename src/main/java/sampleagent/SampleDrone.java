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

import org.jfree.chart.block.Block;
import rescuecore2.messages.Command;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
// import rescuecore2.misc.geometry.Vector2D;
// import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.*;
// import rescuecore2.standard.entities.StandardPropertyURN;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.Entity;
import rescuecore2.worldmodel.EntityID;
import sample.AbstractSampleAgent;
import sample.DistanceSorter;

public class SampleDrone extends AbstractSampleAgent<Drone> {

    private static final Logger LOG = Logger.getLogger(SampleDrone.class);
    private Collection<EntityID> unexploredBuildings;



    @Override
    public String toString() {
        return "Sample drone";
    }

    @Override
    protected void postConnect() {
        super.postConnect();
        model.indexClass(StandardEntityURN.CIVILIAN, StandardEntityURN.BUILDING,
                StandardEntityURN.RESCUE_ROBOT, StandardEntityURN.DRONE,
                StandardEntityURN.REFUGE, StandardEntityURN.POLICE_OFFICE);
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
        updateUnexploredBuildings(changed);
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

        // Keep exploring
        List<EntityID> path = search.breadthFirstSearch(me().getPosition(), unexploredBuildings);
//        List<EntityID> path = new ArrayList<EntityID>();
//        path.add(neighbours.get());
        if(path != null) {
            LOG.info("Searching map");
            sendFly(time, path);
            return;
        }
        LOG.info("Flying in random direction");
        sendFly(time, randomFly());
    }

    @Override
    protected EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
        return EnumSet.of(StandardEntityURN.DRONE);
    }

    private void sendCoordinatesToPolice(int time, int x, int y) {
        Collection<StandardEntity> entities = model.getEntitiesOfType(StandardEntityURN.RESCUE_ROBOT);
        for (StandardEntity entity : entities) {
            int policeOfficeId = entity.getID().getValue();
            sendSpeak(time, policeOfficeId, ("Civilians detected at " + x + ", " + y).getBytes());
            LOG.info("Send help!");
        }        
    }

    private List<Human> getTargets() {
        List<Human> targets = new ArrayList<Human>();
        for (StandardEntity next: model.getEntitiesOfType(
            StandardEntityURN.CIVILIAN)) {
                Human human = (Human) next;
//                if(human == me()) {
//                    continue;
//                }
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
  
 
}
