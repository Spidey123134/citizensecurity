package com.example.citizensecurity.message

import android.content.Intent
import android.net.Uri
import com.example.citizensecurity.domain.ReportMessageDraft
import com.example.citizensecurity.domain.TrustedContact

/** Composes a single SMS target; the receiving app still owns editing and sending. */
internal object ReportMessageComposer {
    fun intent(draft: ReportMessageDraft): Intent {
        require(draft.contact.phoneNumber == TrustedContact.normalizePhone(draft.contact.phoneNumber))
        return Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", draft.contact.phoneNumber, null))
            .putExtra("sms_body", draft.body)
    }
}
