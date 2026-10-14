package com.example.citizensecurity.help

import android.content.Intent
import android.net.Uri
import com.example.citizensecurity.domain.TrustedContact

/** Opening the dialer never places a call or proves someone received a request. */
internal object HelpDialer {
    fun intent(phoneNumber: String): Intent {
        val target = if (phoneNumber == "911" || phoneNumber == "089") phoneNumber
            else TrustedContact.normalizePhone(phoneNumber)
        require(target == phoneNumber) { "Número no normalizado." }
        return Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", target, null))
    }
}
