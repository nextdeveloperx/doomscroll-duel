package com.doomscrollduel.domain.model

/** An Indian mobile number in E.164 form (`+91XXXXXXXXXX`), the format Firebase phone sign-in needs. */
@JvmInline
value class PhoneNumber private constructor(val e164: String) {
    override fun toString(): String = e164

    companion object {
        /** Accepts "98765 43210", "09876543210", "919876543210" and "+91 98765-43210". Mobile numbers start with 6 to 9. */
        fun parseIndian(input: String): PhoneNumber? {
            var digits = input.filter { it.isDigit() }
            digits = when {
                digits.length == 12 && digits.startsWith("91") -> digits.drop(2)
                digits.length == 11 && digits.startsWith("0") -> digits.drop(1)
                else -> digits
            }
            return if (digits.length == 10 && digits.first() in '6'..'9') PhoneNumber("+91$digits") else null
        }
    }
}
