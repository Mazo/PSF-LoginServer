package actor.objects

import actor.base.FreedContextActorTest
import actor.objects.PainboxControlTest.{CreatePlayer, RemovePlayer}
import akka.actor.{ActorContext, ActorSystem, Props}
import net.psforever.objects.Player.Respawn
import net.psforever.objects.avatar.Avatar
import net.psforever.objects.serverobject.doors.{Door, DoorControl}
import net.psforever.objects.serverobject.painbox._
import net.psforever.objects.serverobject.structures.{Building, StructureType}
import net.psforever.objects.zones.{Zone, ZoneMap}
import net.psforever.objects.{GlobalDefinitions, Player}
import net.psforever.packet.game.UseItemMessage
import net.psforever.types._
import org.scalatest.BeforeAndAfterEach
import org.specs2.mutable.Specification

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration._

class PainboxTest extends Specification {
  "Painbox" should {
    "Construct with all known painbox definitions" in {
      Painbox(GlobalDefinitions.painbox)
      Painbox(GlobalDefinitions.painbox_door_radius)
      Painbox(GlobalDefinitions.painbox_door_radius_continuous)
      Painbox(GlobalDefinitions.painbox_continuous)
      Painbox(GlobalDefinitions.painbox_radius)
      Painbox(GlobalDefinitions.painbox_radius_continuous)

      ok
    }

    "Throw error with invalid object id" in {
      val objectId = 1
      new PainboxDefinition(objectId) must throwA[IllegalArgumentException](message = s"${objectId} is not a valid painbox object id")
    }
  }
}

class PainboxControlTest extends FreedContextActorTest with BeforeAndAfterEach {
  val buildingMap = new TrieMap[Int, Building]()
  val building = new Building("Building", building_guid = 0, map_id = 0, zone, StructureType.Building, GlobalDefinitions.amp_station)

  building.Position = Vector3(1f, 1f, 1f)
  building.Faction = PlanetSideEmpire.VS
  buildingMap += building.GUID.guid -> building

  // Initialize the zone so the SphereOfInfluence actor starts running
  val zone = new Zone("test", new ZoneMap("map6"), 1) {
    override def Buildings: Map[Int, Building] = { buildingMap.toMap }
  }
  zone.init(context)

  val playerVS = CreatePlayer(zone, PlanetSideEmpire.VS, avatarId = 0)
  val playerTR = CreatePlayer(zone, PlanetSideEmpire.TR, avatarId = 1)
  val playerNC = CreatePlayer(zone, PlanetSideEmpire.NC, avatarId = 2)

  override protected def beforeEach() : Unit = {
    // Spawn players
    playerVS.Spawn
    playerVS.Position = Vector3(4f, 4f, 4f)

    playerTR.Spawn
    playerTR.Position = Vector3(4f, 4f, 4f)

    playerNC.Spawn
    playerNC.Position = Vector3(4f, 4f, 4f)
  }

  override protected def afterEach() : Unit = {
    // Kill players
    playerVS.Die
    playerTR.Die
    playerNC.Die
  }

  "PainboxControl" should {
    "Damage hostile player if dependent door is open" in {
      val door = Door(GlobalDefinitions.door)
      door.Actor = system.actorOf(Props(classOf[DoorControl], door), "door")
      door.Owner = building
      door.Position = Vector3(3f, 3f, 3f)
      building.Amenities = door

      val painbox = Painbox(GlobalDefinitions.painbox_door_radius)
      painbox.Actor = system.actorOf(Props(classOf[PainboxControl], painbox), "painbox")
      painbox.Owner = building
      painbox.Position = Vector3(2f, 2f, 2f)
      building.Amenities = painbox

      painbox.Actor ! Painbox.Start()



      // Ensure SOI actor has picked up spawned players
      awaitAssert(() -> {
        assert(building.PlayersInSOI.length == 3)
      }, 6 seconds)

      assert(playerVS.Health == 100)
      assert(playerTR.Health == 100)
      assert(playerNC.Health == 100)

      door.Open = Some(playerVS) // Open the door

      painbox.Actor ! "startup"
      expectNoMessage(500 milliseconds)
      assert(playerVS.Health == 100)
      assert(playerTR.Health < 100)
      assert(playerNC.Health < 100)
    }

    "Not damage hostile player is dependent door is closed" in {

    }

    "Not rely on nearby door state if not dependent on door (open)" in {
//      val (player, painbox, door) = PainboxControlTest.SetUpAgents(context, PlanetSideEmpire.NC, GlobalDefinitions.painbox_continuous)
//
//      val originalHealth = player.Health
//      door.Open = Some(player) // Open the door
//
//      painbox.Actor ! "startup"
//      expectNoMessage(500 milliseconds)
//      assert(player.Health < originalHealth)
//
//      door.Open = None // Close the door
//      expectNoMessage(500 milliseconds)
//      assert(player.Health < originalHealth)
    }

    "Not damage friendly players" in {

    }

    "Not damage player outside range" in {

    }

    "Not damage player if Owner faction is NEUTRAL" in {

    }

    "Throw error if no SOI is defined on Owner" in {

    }

    "Damage once per second at most" in {

    }
  }
}

object PainboxControlTest {
  def CreatePlayer(zone: Zone, faction: PlanetSideEmpire.Value, avatarId: Int): Player = {
    val player = Player(Avatar(avatarId, s"test-${faction.toString}", faction, CharacterSex.Male, 0, CharacterVoice.Mute))
    zone.Population ! Zone.Population.Join(player.avatar)
    zone.Population ! Zone.Population.Spawn(player.avatar, player, null)

    player
  }

  def RemovePlayer(zone: Zone, player: Player): Unit = {
    zone.Population ! Zone.Population.Leave(player.avatar)
  }

  def SetUpAgents(context: ActorContext, faction : PlanetSideEmpire.Value, definition : PainboxDefinition)(implicit system : ActorSystem) : (Player, Painbox, Door) = {

    // Initialize the zone so the SphereOfInfluence actor starts running
    val zone = new Zone("test", new ZoneMap("map6"), 1)
    zone.init(context)
//    zone.Actor = system.actorOf(Props(classOf[ZoneActor], zone), "test-zone")
//    zone.Actor ! Zone.Init()

    val avatar = Avatar(0, "test", faction, CharacterSex.Male, 0, CharacterVoice.Mute)
    val player = Player(avatar)
    player.Spawn
    player.Position = Vector3(4f, 4f, 4f)
    zone.Population ! Zone.Population.Join(avatar)

    val building = new Building("Building", building_guid = 0, map_id = 0, zone, StructureType.Building, GlobalDefinitions.amp_station)

    building.Position = Vector3(1f, 1f, 1f)
    building.Faction = PlanetSideEmpire.VS

    Thread.sleep(5000) // Give SOI actor time to repopulate

    val painbox = Painbox(definition)
    painbox.Actor = system.actorOf(Props(classOf[PainboxControl], painbox), "painbox")
    painbox.Owner = building
    painbox.Position = Vector3(2f, 2f, 2f)

    val door = Door(GlobalDefinitions.door)
    door.Actor = system.actorOf(Props(classOf[DoorControl], door), "door")
    door.Owner = building
    door.Position = Vector3(3f, 3f, 3f)

    (player, painbox, door)

  }
}