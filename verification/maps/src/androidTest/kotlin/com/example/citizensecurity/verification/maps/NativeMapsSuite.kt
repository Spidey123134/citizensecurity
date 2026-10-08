package com.example.citizensecurity.verification.maps

import org.junit.runner.RunWith
import org.junit.runners.Suite

/** Selección explícita: conserva los cuatro casos anteriores y añade seis de consulta por zona. */
@RunWith(Suite::class)
@Suite.SuiteClasses(MapLibreNativeTest::class, ReportMapSceneNativeTest::class)
class NativeMapsSuite
