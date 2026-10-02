package com.sagewire.rangewater.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FieldRecordDaoTest {
    private lateinit var db: RangeWaterDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RangeWaterDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() = db.close()

    @Test
    fun taskLifecycleAndNewestFirstLedgerRemainDurable() = runBlocking {
        val dao = db.fieldRecordDao()
        val taskId = dao.create(
            FieldRecordEntity(
                recordType = FieldRecordType.TASK,
                note = "Repair west wire",
                latitude = 40.0,
                longitude = -80.0,
                taskStatus = FieldTaskStatus.OPEN,
                observedAt = 100,
                createdAt = 100,
                updatedAt = 100
            )
        )
        dao.create(
            FieldRecordEntity(
                recordType = FieldRecordType.WEATHER,
                note = "Heavy rain",
                latitude = 40.1,
                longitude = -80.1,
                observedAt = 200,
                createdAt = 200,
                updatedAt = 200
            )
        )

        assertEquals(FieldRecordType.WEATHER, dao.observeActive().first().first().recordType)
        dao.completeTask(taskId, now = 300)
        assertEquals(FieldTaskStatus.COMPLETED, dao.getById(taskId)?.taskStatus)
        assertNotNull(dao.getById(taskId)?.completedAt)
        dao.reopenTask(taskId, now = 400)
        assertEquals(FieldTaskStatus.OPEN, dao.getById(taskId)?.taskStatus)
        assertNull(dao.getById(taskId)?.completedAt)
        dao.archive(taskId, now = 500)
        assertEquals(1, dao.observeActive().first().size)
        dao.reactivate(taskId, now = 600)
        assertEquals(2, dao.observeActive().first().size)
    }
}
