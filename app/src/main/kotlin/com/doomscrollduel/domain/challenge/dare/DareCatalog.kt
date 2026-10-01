package com.doomscrollduel.domain.challenge.dare

/**
 * The only dares that exist. The winner picks from this list; there is no free-text dare, so nobody can
 * type something unsafe or humiliating. Each entry is reviewed against [DareSafety] before it is added.
 *
 * What the user reads is in `strings.xml` under `dare_<id>`. [reviewText] is the English summary used
 * for review and for the automated safety test; it is never shown to players.
 */
enum class DareCategory { SILLY, MUSIC_AND_MOVEMENT, CREATIVE, KIND }

/** Proof is a photo or a video of 10 seconds at most. */
enum class ProofType { PHOTO, VIDEO_10S }

data class Dare(
    val id: String,
    val category: DareCategory,
    val proof: ProofType,
    val reviewText: String,
)

object DareCatalog {
    val all: List<Dare> = listOf(
        Dare("funny_face_selfie", DareCategory.SILLY, ProofType.PHOTO, "Take a selfie making your funniest face."),
        Dare("spoon_on_nose", DareCategory.SILLY, ProofType.PHOTO, "Balance a spoon on your nose and take a photo."),
        Dare("paper_hat", DareCategory.SILLY, ProofType.PHOTO, "Make a paper hat, wear it, and take a selfie."),
        Dare("draw_moustache", DareCategory.SILLY, ProofType.PHOTO, "Draw a moustache on a sheet of paper and hold it under your nose."),
        Dare("inside_out_tshirt", DareCategory.SILLY, ProofType.PHOTO, "At home, wear a t-shirt inside out and take a selfie."),
        Dare("animal_sounds", DareCategory.SILLY, ProofType.VIDEO_10S, "Make three animal sounds in a 10 second video."),
        Dare("tongue_twister", DareCategory.SILLY, ProofType.VIDEO_10S, "Say a tongue twister three times in a 10 second video."),
        Dare("sing_chorus", DareCategory.MUSIC_AND_MOVEMENT, ProofType.VIDEO_10S, "Sing the chorus of your favourite song for 10 seconds."),
        Dare("dance_step", DareCategory.MUSIC_AND_MOVEMENT, ProofType.VIDEO_10S, "Do your best dance step at home for 10 seconds."),
        Dare("jumping_jacks", DareCategory.MUSIC_AND_MOVEMENT, ProofType.VIDEO_10S, "Do ten easy jumping jacks in a 10 second video."),
        Dare("film_dialogue", DareCategory.MUSIC_AND_MOVEMENT, ProofType.VIDEO_10S, "Act out a famous film dialogue in 10 seconds."),
        Dare("draw_winner", DareCategory.CREATIVE, ProofType.PHOTO, "Draw a cartoon of the winner and show the drawing."),
        Dare("two_line_poem", DareCategory.CREATIVE, ProofType.PHOTO, "Write a two line funny poem about the winner and show the paper."),
        Dare("tidy_desk", DareCategory.CREATIVE, ProofType.PHOTO, "Tidy your study desk and photograph it."),
        Dare("compliment_video", DareCategory.KIND, ProofType.VIDEO_10S, "Say three nice things about the winner in a 10 second video."),
        Dare("glass_of_water", DareCategory.KIND, ProofType.PHOTO, "Drink a full glass of water and show the empty glass."),
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): Dare? = byId[id]
}

/**
 * The rules every dare must satisfy. They are checked in a unit test against [DareCatalog], so a bad
 * dare cannot be added by accident.
 *
 * A dare must NOT involve: another person (no calling, texting or filming anyone else), going outside or
 * to a public place, anything dangerous or physically hard, food or drink beyond water, clothing
 * beyond a t-shirt worn at home, anything about bodies, looks, religion, caste, money, alcohol, or
 * anything that embarrasses the person in front of others.
 */
object DareSafety {
    val bannedWords: Set<String> = setOf(
        "alcohol", "beer", "smoke", "cigarette", "knife", "fire", "burn", "hot", "spicy", "chilli", "jump off",
        "stranger", "public", "outside", "street", "road", "money", "pay", "call", "text", "message",
        "naked", "bikini", "underwear", "kiss", "slap", "hit", "insult", "ugly", "fat", "religion", "caste",
        "eat", "swallow", "dangerous", "climb",
    )

    fun violations(dare: Dare): List<String> {
        val text = dare.reviewText.lowercase()
        return bannedWords.filter { word -> Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(text) }
    }
}
