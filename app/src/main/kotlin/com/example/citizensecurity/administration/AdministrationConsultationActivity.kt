package com.example.citizensecurity.administration

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.lifecycle.ViewModelProvider
import com.example.citizensecurity.CitizenSecurityApplication
import com.example.citizensecurity.account.ReportWorkspaceActivity
import com.example.citizensecurity.preview.PreviewTheme

/** Private route. Every reading checks the installation grant and original session on IO. */
class AdministrationConsultationActivity : ReportWorkspaceActivity() {
    private var model: AdministrationConsultationViewModel? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasReportWorkspace) return
        val binding = try { (application as CitizenSecurityApplication).openReportAdministration(reportWorkspace) }
        catch (_: Exception) {
            setContent { PreviewTheme { Text("Inicia sesión con la cuenta administrativa local para consultar.") } }
            return
        }
        val query = ViewModelProvider(this, AdministrationConsultationViewModel.factory(
            binding, reportWorkspace::checkActive))[AdministrationConsultationViewModel::class.java]
        model = query
        setContent { PreviewTheme { AdministrationConsultationScreen(query, ::finish) } }
    }

    override fun onResume() {
        super.onResume()
        if (hasReportWorkspace) model?.checkAccess()
    }
}
