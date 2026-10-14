package com.example.citizensecurity.report

import androidx.core.content.FileProvider

/** Separate, non-exported provider whose XML exposes only report_camera in app cache. */
class ReportCameraFileProvider : FileProvider()
