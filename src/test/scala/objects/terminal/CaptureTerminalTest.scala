//package objects.terminal
//
//import akka.actor.{ActorRef, ActorSystem, Props}
//import base.ActorTest
//import net.psforever.objects.serverobject.CommonMessages
//import net.psforever.objects.serverobject.structures.{Building, StructureType}
//import net.psforever.objects.serverobject.terminals.{CaptureTerminal, CaptureTerminalControl, CaptureTerminalDefinition}
//import net.psforever.objects.zones.Zone
//import net.psforever.objects.{Avatar, GlobalDefinitions, Player}
//import net.psforever.packet.game.PlanetSideGUID
//import net.psforever.types.{CharacterGender, CharacterVoice, PlanetSideEmpire}
//
//class CaptureTerminalTest extends ActorTest {
//  "CaptureTerminal" should {
//    "construct" in {
//      val terminal = CaptureTerminal(GlobalDefinitions.capture_terminal)
//      terminal.Actor = system.actorOf(Props(classOf[CaptureTerminalControl], terminal), "capture-terminal-control")
//      assert(terminal.Actor != ActorRef.noSender)
//    }
//
//    "fail on invalid object type" in {
//      assertThrows[IllegalArgumentException] {
//        CaptureTerminal(new CaptureTerminalDefinition(0))
//      }
//    }
//
//    "can be hacked" in {
//      val (player, terminal) = CaptureTerminalTest.SetUpAgents(PlanetSideEmpire.TR, PlanetSideEmpire.VS, 1)
//      assert(terminal.HackedBy.isEmpty)
//
//      terminal.Actor ! CommonMessages.Hack(player)
//      expectMsg(true)
//      assert(terminal.HackedBy.nonEmpty)
//      assert(terminal.HackedBy.get._1.Faction == PlanetSideEmpire.VS)
//    }
//
//    "can be resecured" in {
//      val (player, player2, terminal) = CaptureTerminalTest.SetUpAgents(PlanetSideEmpire.TR, PlanetSideEmpire.VS, PlanetSideEmpire.TR, 2)
//      assert(terminal.HackedBy.isEmpty)
//
//      terminal.Actor ! CommonMessages.Hack(player)
//      expectMsg(true)
//      assert(terminal.HackedBy.nonEmpty)
//      assert(terminal.HackedBy.get._1.Faction == PlanetSideEmpire.VS)
//
//      terminal.Actor ! CommonMessages.ClearHack()
//      Thread.sleep(500L) // blocking
//      assert(terminal.HackedBy.isEmpty)
//    }
//
//    "can overwrite hack" in {
//      val (player, player2, terminal) = CaptureTerminalTest.SetUpAgents(PlanetSideEmpire.TR, PlanetSideEmpire.VS, PlanetSideEmpire.NC, 3)
//      assert(terminal.HackedBy.isEmpty)
//
//      terminal.Actor ! CommonMessages.Hack(player)
//      expectMsg(true)
//      assert(terminal.HackedBy.nonEmpty)
//      assert(terminal.HackedBy.get._1.Faction == PlanetSideEmpire.VS)
//
//      terminal.Actor ! CommonMessages.Hack(player2)
//      expectMsg(true)
//      assert(terminal.HackedBy.nonEmpty)
//      assert(terminal.HackedBy.get._1.Faction == PlanetSideEmpire.NC)
//    }
//  }
//}
//
//object CaptureTerminalTest {
//  def SetUpAgents(ccFaction : PlanetSideEmpire.Value, playerFaction : PlanetSideEmpire.Value, player2Faction: PlanetSideEmpire.Value, actorId : Int)(implicit system : ActorSystem) : (Player, Player, CaptureTerminal) = {
//    val (player, terminal) = SetUpAgents(ccFaction, playerFaction, actorId)
//
//    val player2 = new Player(Avatar("test", player2Faction, CharacterGender.Male, 0, CharacterVoice.Mute))
//    player2.GUID = PlanetSideGUID(1)
//
//    (player, player2, terminal)
//  }
//
//  def SetUpAgents(ccFaction : PlanetSideEmpire.Value, playerFaction : PlanetSideEmpire.Value, actorId: Int)(implicit system : ActorSystem) : (Player, CaptureTerminal) = {
//    val terminal = CaptureTerminal(GlobalDefinitions.capture_terminal)
//    terminal.Actor = system.actorOf(Props(classOf[CaptureTerminalControl], terminal), s"capture-terminal-control-${actorId}")
//    terminal.Owner = new Building(guid = 0, map_id = 0, Zone.Nowhere, StructureType.Building)
//    terminal.Owner.Faction = ccFaction
//
//    val player = new Player(Avatar("test", playerFaction, CharacterGender.Male, 0, CharacterVoice.Mute))
//    player.GUID = PlanetSideGUID(1)
//
//    (player, terminal)
//  }
//}