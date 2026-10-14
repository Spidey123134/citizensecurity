package com.example.citizensecurity.data

import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(ReportStatusChangeStoreTest::class, SqliteReportStatusChangeAccessTest::class,
    SqliteReportHistoryTest::class)
class ReportStatusRecoveryDataSuite
