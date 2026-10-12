package com.example.citizensecurity.maps

import com.example.citizensecurity.domain.ReportMapPage

/** List and pins share one completed reading; loading/failed/retired scenes expose no old list. */
fun currentVisibleMapPage(scene: ReportMapSceneState, model: ReportMapState): ReportMapPage? {
    if (scene !is ReportMapSceneState.Ready || model !is ReportMapState.Ready) return null
    if (scene.query != model.query || scene.count != model.page.markers.size ||
        scene.hasMore != model.page.hasMore) return null
    return model.page
}
