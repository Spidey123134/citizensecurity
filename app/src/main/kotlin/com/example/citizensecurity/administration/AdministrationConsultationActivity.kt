package com.example.citizensecurity.administration

import android.os.Bundle
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import com.example.citizensecurity.CitizenSecurityApplication
import com.example.citizensecurity.account.ReportWorkspaceActivity
import com.example.citizensecurity.account.ReportWorkspaceActivityBinding
import com.example.citizensecurity.preview.PreviewTheme

/** Private route. Every reading checks the installation grant and original session on IO. */
class AdministrationConsultationActivity : ReportWorkspaceActivity() {
    internal lateinit var resolutionModel: com.example.citizensecurity.resolution.ReportResolutionViewModel
        private set
    private var model: AdministrationConsultationViewModel? = null
    private val change = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (hasReportWorkspace) model?.let { query -> query.refresh(query.state.value) }
    }
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
        resolutionModel = com.example.citizensecurity.resolution.bindReportResolution(this, reportWorkspace)
        setContent { PreviewTheme { AdministrationConsultationScreen(query, ::finish, onChangeStatus = { reportId ->
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && ensureReportWorkspaceCurrent()) {
                change.launch(ReportWorkspaceActivityBinding.stamp(Intent(this, AdministrationStatusChangeActivity::class.java).apply {
                    reportId?.let { putExtra(AdministrationStatusChangeActivity.EXTRA_REPORT_ID, it) }
                }, reportWorkspace.session))
            }
        }, resolution = resolutionModel) } }
    }

    override fun onResume() {
        super.onResume()
        if (hasReportWorkspace) model?.checkAccess()
    }
}
