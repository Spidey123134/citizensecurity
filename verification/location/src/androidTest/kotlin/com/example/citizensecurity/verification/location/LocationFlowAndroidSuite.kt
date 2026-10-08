package com.example.citizensecurity.verification.location

import org.junit.runner.RunWith
import org.junit.runners.Suite

/** Un solo selector del runner incluye composición controlada y comprobación GPS nativa. */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    LocationFlowNativeTest::class,
    AndroidGpsSourceNativeTest::class,
    IncidentLocationFlowBindingNativeTest::class,
    ReportDraftNativeTest::class,
    ReportLocationFlowNativeTest::class,
)
class LocationFlowAndroidSuite
