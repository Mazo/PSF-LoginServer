package net.psforever.types

/*
  * Cavern benefits: (stackable)<br>
  * 000 - None<br>
  * 004 - Speed Module<br>
  * 008 - Shield Module<br>
  * 016 - Vehicle Module<br>
  * 032 - Equipment Module<br>
  * 064 - Health Module<br>
  * 128 - Pain Module<br>
 */
object LatticeCavernBenefits extends Enumeration {
  type Type = Int

  val None = Value(0)
  val Speed = Value(4)
  val Shield = Value(8)
  val Vehicle = Value(16)
  val Equipment = Value(32)
  val Health = Value(64)
  val Pain = Value(128)
}
