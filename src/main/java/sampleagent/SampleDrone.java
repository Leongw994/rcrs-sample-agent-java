package sampleagent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import com.vividsolutions.jts.geom.impl.PackedCoordinateSequence.Double;

import rescuecore2.messages.Command;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Civilian;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.standard.entities.StandardPropertyURN;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.EntityID;
import sample.AbstractSampleAgent;
import sample.DistanceSorter;

public class SampleDrone extends AbstractSampleAgent<Human> {

    private static final Logger LOG = Logger.getLogger(SampleAmbulanceTeam.class);
    private static final int VISION_RANGE = 500;
    private Collection<EntityID> unexploredBuildings;



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

        //
        List<EntityID> path = search.breadthFirstSearch(me().getPosition(), unexploredBuildings);
        if(path != null) {
            LOG.info("Searching map");
            sendMove(time, path);
            return;
        }
        LOG.info("Moving in random direction");
        sendMove(time, randomWalk());
    }


    @Override
    protected EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
        return EnumSet.of(StandardEntityURN.AMBULANCE_TEAM);
    }

    private List<Human> getTargets() {
        List<Human> targets = new ArrayList<Human>();
        for (StandardEntity next: model.getEntitiesOfType(
            StandardEntityURN.CIVILIAN, StandardEntityURN.FIRE_BRIGADE,
            StandardEntityURN.POLICE_FORCE, StandardEntityURN.AMBULANCE_TEAM
        )) {
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

    
    private void detectCivilians() {
        
    }
  
    private void sendCoordinatesToPolice() {
  
    }
  
 
}
