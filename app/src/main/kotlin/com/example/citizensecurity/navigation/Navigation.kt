package com.example.citizensecurity.navigation

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.example.citizensecurity.screen.Home
import com.example.citizensecurity.screen.ReportDetails

/** Conecta el contenedor de la actividad con las pantallas del proyecto. */
class Navigation(private val container: FrameLayout) {
    fun showHome() {
        show(Home(container.context))
    }

    /** Reserva la ruta de detalle para cuando se desarrolle su interfaz. */
    fun showReportDetails() {
        show(ReportDetails(container.context))
    }

    private fun show(screen: View) {
        container.removeAllViews()
        container.addView(
            screen,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }
}
