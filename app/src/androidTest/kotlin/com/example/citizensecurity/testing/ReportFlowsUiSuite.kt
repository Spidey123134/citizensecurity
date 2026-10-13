package com.example.citizensecurity.testing

import com.example.citizensecurity.preview.ReportDraftUiTest
import com.example.citizensecurity.release.ReportHomeUiTest
import com.example.citizensecurity.report.ReportPhotoUiTest
import com.example.citizensecurity.report.ReportSimilarityUiTest
import com.example.citizensecurity.report.ReportSubmissionUiTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

/** Existing report flows as one explicit runner; permission cases use separate system setups. */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    ReportDraftUiTest::class,
    ReportHomeUiTest::class,
    ReportPhotoUiTest::class,
    ReportSimilarityUiTest::class,
    ReportSubmissionUiTest::class,
)
class ReportFlowsUiSuite
