// Copyright (c) 2021 PSForever
package net.psforever.objects.zones.blockmap

import net.psforever.objects.PlanetSideGameObject
import net.psforever.objects.serverobject.environment.PieceOfEnvironment
import net.psforever.objects.serverobject.structures.Building
import net.psforever.objects.zones.MapScale
import net.psforever.types.Vector3

import scala.collection.mutable.ListBuffer

/**
  * A data structure which divides coordinate space into buckets or coordinate spans.
  * The function of the blockmap is to organize the instantiated game objects (entities)
  * that can be represented in coordinate space into a bucket each or into multiple buckets each
  * that reflect their locality with other game objects in the same coordinate space.
  * Polling based on either positions or on entities should be able to recover a lists of entities
  * that are considered neighbors in the context of that position and a rectangular distance around the position.
  * The purpose of the blockmap is to improve targeting when making such locality determinations.<br>
  * <br>
  * The coordinate space of a PlanetSide zone may contain 65535 entities, one of which is the same target entity.
  * A bucket on the blockmap should contain only a small fraction of the full zone's entities.
  * @param fullMapWidth maximum width of the coordinate space (m)
  * @param fullMapHeight maximum height of the coordinate space (m)
  * @param desiredSpanSize the amount of coordinate space attributed to each bucket in the blockmap (m)
  */
class BlockMap(fullMapWidth: Int, fullMapHeight: Int, desiredSpanSize: Int) {
  /** a clamping of the desired span size to a realistic value to use for the span size;
    * blocks can not be too small, but also should not be much larger than the width of the representable region
    * a block spanning as wide as the map is an acceptable cap
    */
  val spanSize: Int = math.min(math.max(10, desiredSpanSize), fullMapWidth)
  /** how many sectors are in a row;
    * the far side sector may run off into un-navigable regions but will always contain a sliver of represented map space,
    * for example, on a 0-10 grid where the span size is 3, the spans will begin at (0, 3, 6, 9)
    * and the last span will only have two-thirds of its region valid;
    * the invalid, not represented regions should be silently ignored
    */
  val blocksInRow: Int = fullMapWidth / spanSize + (if (fullMapWidth % spanSize > 0) 1 else 0)
  /** the sectors / blocks / buckets into which entities that submit themselves are divided;
    * while the represented region need not be square, the sectors are defined as squares
    */
  val blocks: ListBuffer[Sector] = {
    val horizontal: List[Int] = List.range(0, fullMapWidth, spanSize)
    val vertical: List[Int] = List.range(0, fullMapHeight, spanSize)
    ListBuffer.newBuilder[Sector].addAll(
      vertical.flatMap { latitude =>
        horizontal.map { longitude =>
          new Sector(longitude, latitude, spanSize)
        }
      }
    ).result()
  }

  /**
    * Given a blockmap entity,
    * one that is allegedly represented on this blockmap,
    * find the sector conglomerate in which this entity is allocated.
    * @see `BlockMap.quickToSectorGroup`
    * @param entity the target entity
    * @return a conglomerate sector which lists all of the entities in the discovered sector(s)
    */
  def sector(entity: BlockMapEntity): SectorPopulation = {
    entity.blockMapEntry match {
      case Some(entry) =>
        BlockMap.quickToSectorGroup(BlockMap.sectorsOnlyWithinBlockStructure(entry.sectors, entry.map.blocks))
      case None =>
        SectorGroup(Nil)
    }
  }

  /**
    * Given a coordinate position within representable space and a range from that representable space,
    * find the sector conglomerate to which this range allocates.
    * @see `BlockMap.findSectorIndices`
    * @see `BlockMap.quickToSectorGroup`
    * @see `BlockMap::sector(Iterable[Int], Float)`
    * @param p the game world coordinates
    * @param range the axis distance from the provided coordinates
    * @return a conglomerate sector which lists all of the entities in the discovered sector(s)
    */
  def sector(p: Vector3, range: Float): SectorPopulation = {
    sector(BlockMap.findSectorIndices(blockMap = this, p, range), range)
  }

  /**
   * Given a coordinate position within representable space and a range from that representable space,
   * find the sector conglomerate to which this range allocates.
   * @see `BlockMap.findSectorIndices`
   * @see `BlockMap.quickToSectorGroup`
   * @param indices an enumeration that directly associates with the structure of the block map
   * @param range the axis distance from the provided coordinates
   * @return a conglomerate sector which lists all of the entities in the discovered sector(s)
   */
  def sector(indices: Iterable[Int], range: Float): SectorPopulation = {
    if (indices.max < blocks.size) {
      BlockMap.quickToSectorGroup(range, BlockMap.sectorsOnlyWithinBlockStructure(indices, blocks) )
    } else {
      SectorGroup(Nil)
    }
  }

  /**
    * Allocate this entity into appropriate sectors on the blockmap.
    * @see `addTo(BlockMapEntity, Vector3)`
    * @param target the entity
    * @return a conglomerate sector which lists all of the entities in the allocated sector(s)
    */
  def addTo(target: BlockMapEntity): SectorPopulation = {
    addTo(target, target.Position)
  }

  /**
    * Allocate this entity into appropriate sectors on the blockmap
    * at the provided game world coordinates.
    * @see `addTo(BlockMapEntity, Vector3, Float)`
    * @see `BlockMap.rangeFromEntity`
    * @param target the entity
    * @param toPosition the custom game world coordinates that indicate the central sector
    * @return a conglomerate sector which lists all of the entities in the allocated sector(s)
    */
  def addTo(target: BlockMapEntity, toPosition: Vector3): SectorPopulation = {
    val (y,x) = BlockMap.rangeFromEntity(target)
    addTo(target, toPosition, x, y)
  }

  /**
    * Allocate this entity into appropriate sectors on the blockmap
    * using the provided custom axis range.
    * @see `addTo(BlockMapEntity, Vector3, Float)`
    * @param target the entity
    * @param range the custom distance from the central sector along the major axes
    * @return a conglomerate sector which lists all of the entities in the allocated sector(s)
    */
  def addTo(target: BlockMapEntity, range: Float): SectorPopulation = {
    addTo(target, target.Position, range)
  }

  /**
    * Allocate this entity into appropriate sectors on the blockmap
    * using the provided game world coordinates and the provided axis range.
    * @see `BlockMap.findSectorIndices`
    * @param target the entity
    * @param toPosition the game world coordinates that indicate the central sector
    * @param range the distance from the central sector along the major axes
    * @return a conglomerate sector which lists all of the entities in the allocated sector(s)
    */
  def addTo(target: BlockMapEntity, toPosition: Vector3, range: Float): SectorPopulation = {
    addTo(target, toPosition, range, range)
  }

  /**
    * Allocate this entity into appropriate sectors on the blockmap
    * using the provided game world coordinates and the provided axis range.
    * @see `BlockMap.findSectorIndices`
    * @param target the entity
    * @param toPosition the game world coordinates that indicate the central sector
    * @param rangeX the distance from the central sector along the major x-axis
    * @param rangeY the distance from the central sector along the major y-axis
    * @return a conglomerate sector which lists all of the entities in the allocated sector(s)
    */
  def addTo(target: BlockMapEntity, toPosition: Vector3, rangeX: Float, rangeY: Float): SectorPopulation = {
    val to = BlockMap.findSectorIndices(blockMap = this, toPosition, rangeX, rangeY)
    val toSectors = BlockMap.sectorsOnlyWithinBlockStructure(to, blocks)
    toSectors.foreach { block => block.addTo(target) }
    target.blockMapEntry = Some(BlockMapEntry(this, toPosition, rangeX, rangeY, to.toSet))
    BlockMap.quickToSectorGroup(rangeX, rangeY, toSectors)
  }

  /**
    * Deallocate this entity from appropriate sectors on the blockmap.
    * @see `actuallyRemoveFrom(BlockMapEntity, Vector3, Float)`
    * @param target the entity
    * @return a conglomerate sector which lists all of the entities in the allocated sector(s)
    */
  def removeFrom(target: BlockMapEntity): SectorPopulation = {
    target.blockMapEntry match {
      case Some(entry) => actuallyRemoveFrom(target, entry.coords, entry.rangeX, entry.rangeY)
      case None        => SectorGroup(Nil)
    }
  }

  /**
    * Deallocate this entity from appropriate sectors on the blockmap.
    * Other parameters are included for symmetry with a respective `addto` method,
    * but are ignored since removing an entity from a sector from which it is not represented is ill-advised
    * as is not removing an entity from any sector that it occupies.
    * @see `removeFrom(BlockMapEntity)`
    * @param target the entity
    * @param fromPosition ignored
    * @return a conglomerate sector which lists all of the entities in the allocated sector(s)
    */
  def removeFrom(target: BlockMapEntity, fromPosition: Vector3): SectorPopulation = {
    removeFrom(target)
  }

  /**
    * Deallocate this entity from appropriate sectors on the blockmap.
    * Other parameters are included for symmetry with a respective `addto` method,
    * but are ignored since removing an entity from a sector from which it is not represented is ill-advised
    * as is not removing an entity from any sector that it occupies.
    * @see `removeFrom(BlockMapEntity)`
    * @param target the entity
    * @param range ignored
    * @return a conglomerate sector which lists all of the entities in the allocated sector(s)
    */
  def removeFrom(target: BlockMapEntity, range: Float): SectorPopulation =
    removeFrom(target)

  /**
    * Deallocate this entity from appropriate sectors on the blockmap.
    * Other parameters are included for symmetry with a respective `addto` method,
    * but are ignored since removing an entity from a sector from which it is not represented is ill-advised
    * as is not removing an entity from any sector that it occupies.
    * @see `removeFrom(BlockMapEntity)`
    * @param target the entity
    * @param fromPosition ignored
    * @param range ignored
    * @return a conglomerate sector which lists all of the entities in the allocated sector(s)
    */
  def removeFrom(target: BlockMapEntity, fromPosition: Vector3, range: Float): SectorPopulation = {
    removeFrom(target)
  }

  /**
    * Deallocate this entity from appropriate sectors on the blockmap.
    * Really.
    * @param target the entity
    * @param fromPosition the game world coordinates that indicate the central sector
    * @param rangeX the distance from the central sector along the major x-axis
    * @param rangeY the distance from the central sector along the major y-axis
    * @return a conglomerate sector which lists all of the entities in the allocated sector(s)
    */
  private def actuallyRemoveFrom(target: BlockMapEntity, fromPosition: Vector3, rangeX: Float, rangeY: Float): SectorPopulation = {
    target.blockMapEntry match {
      case Some(entry) =>
        target.blockMapEntry = None
        val from = BlockMap.sectorsOnlyWithinBlockStructure(entry.sectors, entry.map.blocks)
        from.foreach { block => block.removeFrom(target) }
        BlockMap.quickToSectorGroup(rangeX, rangeY, from)
      case None =>
        SectorGroup(Nil)
    }
  }

  /**
    * Move an entity on the blockmap structure and update the prerequisite internal information.
    * @see `move(BlockMapEntity, Vector3, Vector3, Float)`
    * @param target the entity
    * @return a conglomerate sector which lists all of the entities in the allocated sector(s)
    */
  def move(target: BlockMapEntity): SectorPopulation = {
    target.blockMapEntry match {
      case Some(entry) => move(target, target.Position, entry.coords, entry.rangeX, entry.rangeY)
      case None        => SectorGroup(Nil)
    }
  }

  /**
    * Move an entity on the blockmap structure and update the prerequisite internal information.
    * @see `move(BlockMapEntity, Vector3, Vector3, Float)`
    * @param target the entity
    * @param toPosition the next location of the entity in world coordinates
    * @return a conglomerate sector which lists all of the entities in the allocated sector(s)
    */
  def move(target: BlockMapEntity, toPosition: Vector3): SectorPopulation = {
    target.blockMapEntry match {
      case Some(entry) => move(target, toPosition, entry.coords, entry.rangeX, entry.rangeY)
      case _           => SectorGroup(Nil)
    }
  }

  /**
    * Move an entity on the blockmap structure and update the prerequisite internal information.
    * @see `move(BlockMapEntity, Vector3)`
    * @param target the entity
    * @param toPosition the next location of the entity in world coordinates
    * @param fromPosition ignored
    * @return a conglomerate sector which lists all of the entities in the allocated sector(s)
    */
  def move(target: BlockMapEntity, toPosition: Vector3, fromPosition: Vector3): SectorPopulation = {
    move(target, toPosition)
  }

  /**
    * Move an entity on the blockmap structure and update the prerequisite internal information.
    * @param target the entity
    * @param toPosition the next location of the entity in world coordinates
    * @param fromPosition the current location of the entity in world coordinates
    * @param range the distance from the location along the major axes
    * @return a conglomerate sector which lists all of the entities in the allocated sector(s)
    */
  def move(target: BlockMapEntity, toPosition: Vector3, fromPosition: Vector3, range: Float): SectorPopulation = {
    move(target, toPosition, fromPosition, range, range)
  }

  /**
    * Move an entity on the blockmap structure and update the prerequisite internal information.
    * @param target the entity
    * @param toPosition the next location of the entity in world coordinates
    * @param fromPosition the current location of the entity in world coordinates
    * @param rangeX the distance from the location along the major x-axis
    * @param rangeY the distance from the location along the major y-axis
    * @return a conglomerate sector which lists all of the entities in the allocated sector(s)
    */
  def move(target: BlockMapEntity, toPosition: Vector3, fromPosition: Vector3, rangeX: Float, rangeY: Float): SectorPopulation = {
    target.blockMapEntry match {
      case Some(entry) =>
        val from = entry.sectors
        val to = BlockMap.findSectorIndices(blockMap = this, toPosition, rangeX, rangeY).toSet
        to.diff(from).foreach { index => BlockMap.sectorOnlyWithinBlockStructure(index, blocks).addTo(target) }
        from.diff(to).foreach { index => BlockMap.sectorOnlyWithinBlockStructure(index, entry.map.blocks).removeFrom(target) }
        target.blockMapEntry = Some(BlockMapEntry(this, toPosition, rangeX, rangeY, to))
        BlockMap.quickToSectorGroup(rangeX, rangeY, BlockMap.sectorsOnlyWithinBlockStructure(to, blocks))
      case None    =>
        SectorGroup(Nil)
    }
  }
}

object BlockMap {
  /**
    * Overloaded constructor that uses a `MapScale` field, common with `Zone` entities.
    * @param scale the two-dimensional scale of the map
    * @param desiredSpanSize the length and width of a sector
    * @return a ` BlockMap` entity
    */
  def apply(scale: MapScale, desiredSpanSize: Int): BlockMap = {
    new BlockMap(scale.width.toInt, scale.height.toInt, desiredSpanSize)
  }

  /**
    * The blockmap is mapped to a coordinate range in two directions,
    * so find the indices of the sectors that correspond to the region
    * defined by the range around a coordinate position.
    * @param blockMap the blockmap structure
    * @param p the coordinate position
    * @param range a rectangular range aigned with lateral axes extending from a coordinate position
    * @return the indices of the sectors in the blockmap structure
    */
  def findSectorIndices(blockMap: BlockMap, p: Vector3, range: Float): Iterable[Int] = {
    findSectorIndices(blockMap, p, range, range)
  }

  /**
    * The blockmap is mapped to a coordinate range in two directions,
    * so find the indices of the sectors that correspond to the region
    * defined by the range around a coordinate position.
    * @param blockMap the blockmap structure
    * @param p the coordinate position
    * @param rangeX a rectangular range aigned with the lateral x-axis extending from a coordinate position
    * @param rangeY a rectangular range aigned with the lateral y-axis extending from a coordinate position
    * @return the indices of the sectors in the blockmap structure
    */
  def findSectorIndices(blockMap: BlockMap, p: Vector3, rangeX: Float, rangeY: Float): Iterable[Int] = {
    findSectorIndices(blockMap.spanSize, blockMap.blocksInRow, blockMap.blocks.size, p, rangeX, rangeY)
  }

  /**
    * The blockmap is mapped to a coordinate range in two directions,
    * so find the indices of the sectors that correspond to the region
    * defined by the range around a coordinate position.
    * @param spanSize the length and width of a sector
    * @param blocksInRow the number of sectors across the width (in a row) of the blockmap
    * @param blocksTotal the number of sectors in the blockmap
    * @param p the coordinate position
    * @param rangeX a rectangular range aigned with a lateral x-axis extending from a coordinate position
    * @param rangeY a rectangular range aigned with a lateral y-axis extending from a coordinate position
    * @return the indices of the sectors in the blockmap structure
    */
  private def findSectorIndices(
                                 spanSize: Int,
                                 blocksInRow: Int,
                                 blocksTotal: Int,
                                 p: Vector3,
                                 rangeX: Float,
                                 rangeY: Float
                               ): Iterable[Int] = {
    val corners = {
      /*
      find the corners of a rectangular region extending in all cardinal directions from the position;
      transform these corners into four sector indices;
      if the first index matches the last index, the position and range are only in one sector;
        [----][----][----]
        [----][1234][----]
        [----][----][----]
      if the first and the second or the first and the third are further apart than an adjacent column or row,
      then the missing indices need to be filled in and all of those sectors include the position and range;
        [----][----][----][----][----]
        [----][1   ][    ][2   ][----]
        [----][    ][    ][    ][----]
        [----][3   ][    ][4   ][----]
        [----][----][----][----][----]
      if neither of the previous, just return all distinct corner indices
        [----][----][----][----]      [----][----][----]      [----][----][----][----]
        [----][1   ][2   ][----]      [----][1  2][----]      [----][1  3][2  4][----]
        [----][3   ][4   ][----]      [----][3  4][----]      [----][----][----][----]
        [----][----][----][----]      [----][----][----]
       */
      val blocksInColumn = blocksTotal / blocksInRow
      val lowx = math.max(0, p.x - rangeX)
      val highx = math.min(p.x + rangeX, (blocksInRow * spanSize - 1).toFloat)
      val lowy = math.max(0, p.y - rangeY)
      val highy = math.min(p.y + rangeY, (blocksInColumn * spanSize - 1).toFloat)
      Seq( (lowx,  lowy), (highx, lowy), (lowx,  highy), (highx, highy) )
    }.map { case (x, y) =>
      (y / spanSize).toInt * blocksInRow + (x / spanSize).toInt
    }
    if (corners.head == corners(3)) {
      List(corners.head)
    } else if (corners(1) - corners.head > 1 || corners(2) - corners.head > blocksInRow) {
      (0 to (corners(2) - corners.head) / blocksInRow).flatMap { d =>
        val perRow = d * blocksInRow
        (corners.head + perRow) to (corners(1) + perRow)
      }
    } else {
      corners.distinct
    }
  }

  /**
    * Calculate the range expressed by a certain entity that can be allocated into a sector on the blockmap.
    * Entities have different ways of expressing these ranges.
    * @param target   the entity
    * @param defaultX a default range for the x-axis, if no specific case is discovered;
    *                 if no default case, the default-default case is a single unit (`1.0f`)
    * @param defaultY a default range for the y-axis, if no specific case is discovered;
    *                      if no default case, the default-default case is a single unit (`1.0f`)
    * @return the distance from a central position along the major axes (y-axis, then x-axis)
    */
  def rangeFromEntity(target: BlockMapEntity, defaultX: Option[Float] = None, defaultY: Option[Float] = None): (Float, Float) = {
    target match {
      case b: Building =>
        //use the building's sphere of influence
        (b.Definition.SOIRadius.toFloat, b.Definition.SOIRadius.toFloat)

      case o: PlanetSideGameObject =>
        //use the server geometry
        val pos = target.Position
        val v = o.Definition.Geometry(o)
        val out = math.sqrt(math.max(
          Vector3.DistanceSquared(pos, v.pointOnOutside(Vector3(1,0,0)).asVector3),
          Vector3.DistanceSquared(pos, v.pointOnOutside(Vector3(0,1,0)).asVector3)
        )).toFloat
        (out, out)

      case e: PieceOfEnvironment =>
        //use the bounds (like server geometry, but is always a rectangle on the XY-plane)
        val bounds = e.collision.bounding
        ((bounds.top - bounds.base) * 0.5f, (bounds.right - bounds.left) * 0.5f)

      case _ =>
        //default and default-default
        (defaultX.getOrElse(1.0f), defaultY.getOrElse(1.0f))
    }
  }

  /**
    * If only one sector, just return that sector.
    * If a group of sectors, organize them into a single referential sector.
    * @param to all allocated sectors
    * @return a conglomerate sector which lists all of the entities in the allocated sector(s)
    */
  def quickToSectorGroup(to: Iterable[Sector]): SectorPopulation = {
    if (to.size == 1) {
      SectorGroup(to.head)
    } else {
      SectorGroup(to)
    }
  }

  /**
    * If only one sector, just return that sector.
    * If a group of sectors, organize them into a single referential sector.
    * @param range a custom range value
    * @param to all allocated sectors
    * @return a conglomerate sector which lists all of the entities in the allocated sector(s)
    */
  def quickToSectorGroup(range: Float, to: Iterable[Sector]): SectorPopulation = {
    quickToSectorGroup(range, range, to)
  }



  /**
    * If only one sector, just return that sector.
    * If a group of sectors, organize them into a single referential sector.
    * @param rangeX a custom range value for the x-axis
    * @param rangeY a custom range value for the y-axis
    * @param to all allocated sectors
    * @return a conglomerate sector which lists all of the entities in the allocated sector(s)
    */
  def quickToSectorGroup(rangeX: Float, rangeY: Float, to: Iterable[Sector]): SectorPopulation = {
    if (to.size == 1) {
      SectorGroup(rangeX, rangeY, to.head)
    } else {
      SectorGroup(rangeX, rangeY, to)
    }
  }

  /**
    * Find a blockmap sector that most closely corresponds to the index.
    * @see `sectorsOnlyWithinBlockStructure`
    * @param index the index of the sector
    * @param structure the collection of sectors
    * @return the sector at the index position, or a blank sector
    */
  private def sectorOnlyWithinBlockStructure(
                                              index: Int,
                                              structure: Iterable[Sector]
                                            ): Sector = {
    if (index < structure.size) {
      structure.toSeq(index)
    } else {
      Sector.Empty
    }
  }

  /**
    * Find a collection of blockmap sectors that most closely corresponds to the indices.
    * @see `sectorOnlyWithinBlockStructure`
    * @param list the indices of sectors
    * @param structure the collection of sectors
    * @return the collection of sectors at the index positions, or a blank collection
    */
  private def sectorsOnlyWithinBlockStructure(
                                               list: Iterable[Int],
                                               structure: Iterable[Sector]
                                             ): Iterable[Sector] = {
    if (list.max < structure.size) {
      val structureSeq = structure.toSeq
      list.toSet.map { structureSeq }
    } else {
      List[Sector]()
    }
  }
}
