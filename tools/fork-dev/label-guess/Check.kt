import fyi.slot.konkr.ArtGuess
import java.io.File

internal fun dat(n: String) = ArtGuess.parseDat(File("/tmp/$n.dat").readText())
internal fun names(n: String) = File("/tmp/$n.box.txt").readLines().map { it.substringAfter("Named_Boxarts/").removeSuffix(".png") }.map { ArtGuess.Known(it) }

internal fun show(label: String, clues: ArtGuess.Clues, known: List<ArtGuess.Known>) {
    val r = ArtGuess.rank(clues, known, 3)
    println("%-70s -> %s".format(label.take(70), r.joinToString(" | ") { "%s %.2f".format(it.known.name, it.score) }.ifEmpty { "(none)" }))
}

fun main(args: Array<String>) {
    val sets = mapOf(
        "gba" to dat("Nintendo_-_Game_Boy_Advance"), "gb" to dat("Nintendo_-_Game_Boy"),
        "gbc" to dat("Nintendo_-_Game_Boy_Color") + dat("Nintendo_-_Game_Boy").map { it.copy(tag = "GB") }, "snes" to dat("Nintendo_-_Super_Nintendo_Entertainment_System"),
        "gen" to dat("Sega_-_Mega_Drive_-_Genesis"), "psx" to names("Sony_-_PlayStation"),
        "tgcd" to names("NEC_-_PC_Engine_CD_-_TurboGrafx-CD"))
    println(sets.mapValues { it.value.size })
    val serial = mapOf("Pokemon" to "BPEE")
    for (line in File(args[0]).readLines()) {
        val (sys, name, code) = (line.split('\t') + listOf("", "")).take(3)
        val known = sets[sys] ?: continue
        show("$sys $name ${code}", ArtGuess.Clues(name, serial = code.ifBlank { null }), known)
    }
}
