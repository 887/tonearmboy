package com.eight87.tonearmboy.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D.18.6 — CRUD + reorder consistency for [CustomTabDao].
 *
 * The reorder transaction is the load-bearing assertion: after a call
 * to [CustomTabDao.reorder] the new positions must reflect the
 * supplied order, and `observeAll` must emit a list sorted by those
 * positions in a single emission (i.e. observers shouldn't see a
 * half-shuffled state).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CustomTabDaoTest {

  private lateinit var db: LibraryDatabase
  private lateinit var dao: CustomTabDao

  @Before fun setUp() {
    db = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      LibraryDatabase::class.java,
    )
      .allowMainThreadQueries()
      .build()
    dao = db.customTabDao()
  }

  @After fun tearDown() { db.close() }

  private fun tab(name: String, position: Int = 0, contentType: CustomTabContentType = CustomTabContentType.SONGS) =
    CustomTabEntity(
      id = 0,
      name = name,
      position = position,
      contentType = contentType,
      criteriaJson = "{}",
    )

  @Test fun upsert_assigns_autoincrement_id() = runTest {
    val id = dao.upsert(tab("Synthwave"))
    assertNotNull(dao.getById(id))
  }

  @Test fun observeAll_returns_rows_in_position_order() = runTest {
    dao.upsert(tab("B", position = 1))
    dao.upsert(tab("A", position = 0))
    dao.upsert(tab("C", position = 2))
    val rows = dao.observeAll().first()
    assertEquals(listOf("A", "B", "C"), rows.map { it.name })
  }

  @Test fun delete_removes_row() = runTest {
    val id = dao.upsert(tab("Doomed"))
    dao.delete(id)
    assertNull(dao.getById(id))
  }

  @Test fun reorder_rewrites_positions_in_one_transaction() = runTest {
    val a = dao.upsert(tab("A", position = 0))
    val b = dao.upsert(tab("B", position = 1))
    val c = dao.upsert(tab("C", position = 2))

    dao.reorder(listOf(c, a, b))

    val rows = dao.observeAll().first()
    assertEquals(listOf("C", "A", "B"), rows.map { it.name })
    assertEquals(listOf(0, 1, 2), rows.map { it.position })
  }

  @Test fun maxPosition_returns_null_when_empty_else_highest() = runTest {
    assertNull(dao.maxPosition())
    dao.upsert(tab("first", position = 0))
    dao.upsert(tab("second", position = 5))
    assertEquals(5, dao.maxPosition())
  }
}
