package sampleagent;

import java.util.Collection;
import java.util.EnumSet;
import org.apache.log4j.Logger;
//import org.dom4j.Entity;

//import firesimulator.world.PoliceForce;
import rescuecore.commands.AKSay;
import rescuecore2.messages.Command;
import rescuecore2.standard.components.StandardAgent;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.ChangeSet;
//import rescuecore2.worldmodel.EntityID;

/**
 * A sample centre agent.
 */
public class SampleCentre extends StandardAgent<Building> {

  private static final Logger LOG = Logger.getLogger(SampleCentre.class);

  @Override
  public String toString() {
    return "Sample centre";
  }


  @Override
  protected void think(int time, ChangeSet changed, Collection<Command> heard) {
    if (time == config
        .getIntValue(kernel.KernelConstants.IGNORE_AGENT_COMMANDS_KEY)) {
      // Subscribe to channels 1 and 2
      sendSubscribe(time, 1, 2);
    }
    for (Command next : heard) {
      LOG.debug("Heard " + next);
      if (next instanceof AKSay ksay) {
          String message = new String(ksay.getMessage());
        //check if the message is from the drone agent 
        if(message.startsWith("Civilians detected at")) {
          // extract the coordinates from the message
          String[] parts = message.split(" ");
          int x = Integer.parseInt(parts[4]);
          int y = Integer.parseInt(parts[8]);
          LOG.info("Received coordinates of civilians: (" + x + "," + y + ")");
          //send coordinates to the rescue robot 
          sendCoordinatesToRescueRobot(time, x, y);
        }
      }
    }
    sendRest(time);
  }


  @Override
  protected EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
    return EnumSet.of(StandardEntityURN.FIRE_STATION,
        StandardEntityURN.AMBULANCE_CENTRE, StandardEntityURN.POLICE_OFFICE);
  }

  private void sendCoordinatesToRescueRobot(int time, int x, int y) {
    int rescueRobotID = getRescueRobotID();
    // send command to rescue robot to go towards the coordinates
    sendSpeak(time, rescueRobotID, ("Go towards the civilians at " + x + ", " + y).getBytes());
  }

  private int getRescueRobotID() {
    Collection<StandardEntity> entities = model.getEntitiesOfType(StandardEntityURN.POLICE_FORCE);
    for (StandardEntity entity : entities) {
      int rescueRobotID = entity.getID().getValue();
      return rescueRobotID;
    }
    // return null if the rescue robot is not found
    return 0;
  }

}