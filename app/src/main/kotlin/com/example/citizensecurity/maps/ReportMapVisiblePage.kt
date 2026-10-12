package com.example.citizensecurity.maps

import com.example.citizensecurity.domain.ReportMapPage
import com.example.citizensecurity.domain.MapRegion

/** List and pins share one completed reading; loading/failed/retired scenes expose no old list. */
fun currentVisibleMapPage(scene: ReportMapSceneState, model: ReportMapState): ReportMapPage? {
    if (scene !is ReportMapSceneState.Ready || model !is ReportMapState.Ready) return null
    if (scene.query != model.query || scene.count != model.page.markers.size ||
        scene.hasMore != model.page.hasMore) return null
    return model.page
}

/** A tap must also match the current camera, even before the SDK delivers camera-idle. */
fun currentVisibleMapPageInRegion(
    scene: ReportMapSceneState, model: ReportMapState, visibleRegion: MapRegion?,
): ReportMapPage? {
    if (scene !is ReportMapSceneState.Ready || visibleRegion != scene.query.region) return null
    return currentVisibleMapPage(scene, model)
}
