package sampleagent;

import java.util.Collection;
import java.util.EnumSet;
import org.apache.log4j.Logger;
//import org.dom4j.Entity;

//import firesimulator.world.PoliceForce;
import rescuecore.commands.AKSay;
import rescuecore.commands.AKTell;
import rescuecore2.messages.Command;
import rescuecore2.standard.components.StandardAgent;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.standard.messages.AKSpeak;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.misc.geometry.Point2D;
//import rescuecore2.worldmodel.EntityID;

/**
 * A sample centre agent.
 */
public class SampleCentre extends StandardAgent<Building> {

  private static final Logger LOG = Logger.getLogger(SampleCentre.class);

  private Point2D targetCoordinates;

  @Override
  public String toString() {
    return "Sample centre";
  }


  @Override
  protected void think(int time, ChangeSet changed, Collection<Command> heard) {
    if (time == config
        .getIntValue(kernel.KernelConstants.IGNORE_AGENT_COMMANDS_KEY)) {
      // Subscribe to channels 1 and 2
      sendSubscribe(time, 1);
    }
    for (Command next : heard) {
      LOG.debug("Heard " + next);
      if (next instanceof AKSpeak) {
        AKSpeak speak = (AKSpeak) next;
        String message = new String(speak.getContent());
        if (message.startsWith("People")) {
          String[] parts = message.split(" ");
          try {
            if (parts.length == 3) {
              int x = Integer.parseInt(parts[1]);
              int y = Integer.parseInt(parts[2]);
              targetCoordinates = new Point2D(x, y);
              LOG.info("Received coordinates of trapped civilians at: " + x + ", " + y);
            }
          } catch (NumberFormatException ex) {
            LOG.error("Failed to parse coordinates from message: " + message + " ERROR: " + ex);
          }
        }
      }
    }

//    int x = me().getX();
//    int y = me().getY();
    if (targetCoordinates != null) {
      int x = (int) targetCoordinates.getX();
      int y = (int) targetCoordinates.getY();
      String message = String.format("coordinates %d %d", x, y);
      LOG.info("Instructed the robot to go there: " + "X: " + x + " Y: " + y);
      LOG.info("Instructed the police brigade to go there: " + "X: " + x + " Y: " + y);
      sendSpeak(time, 1, message.getBytes());
    } else {
      LOG.info("No target coordinates received");
    }



    sendRest(time);
  }


  @Override
  protected EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
    return EnumSet.of(StandardEntityURN.FIRE_STATION,
        StandardEntityURN.AMBULANCE_CENTRE, StandardEntityURN.POLICE_OFFICE);
  }



}