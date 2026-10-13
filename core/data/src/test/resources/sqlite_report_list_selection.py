"""Execute the Kotlin list selection on existing rows in a separate in-memory SQLite fixture."""
import sqlite3
import sys
from pathlib import Path

lines = Path(sys.argv[1]).read_text(encoding="utf-8").splitlines()
sql = lines[0]
arguments = lines[1].split("\t") if lines[1] else []
rows = []
for line in lines[2:]:
    values = line.split("\t")
    rows.append(tuple(values[:4]) + tuple(int(value) for value in values[4:]))

with sqlite3.connect(":memory:") as database:
    database.execute("""
        CREATE TABLE reports (
            id TEXT PRIMARY KEY, incident_type TEXT NOT NULL,
            status TEXT NOT NULL, priority TEXT NOT NULL,
            occurred_at_seconds INTEGER NOT NULL, occurred_at_nanos INTEGER NOT NULL,
            created_at_seconds INTEGER NOT NULL, created_at_nanos INTEGER NOT NULL,
            description TEXT NOT NULL DEFAULT 'Árbol 100% _ seguro',
            location_reference TEXT NOT NULL DEFAULT 'Referencia local',
            latitude REAL, longitude REAL, photo_id TEXT
        )
    """)
    database.execute("""
        CREATE INDEX reports_created_at_idx
        ON reports (created_at_seconds DESC, created_at_nanos DESC, id DESC)
    """)
    database.execute("PRAGMA user_version = 2")
    database.executemany("""
        INSERT INTO reports (
            id, incident_type, status, priority, occurred_at_seconds, occurred_at_nanos,
            created_at_seconds, created_at_nanos
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """, rows)
    before = database.execute("SELECT * FROM reports ORDER BY id").fetchall()
    schema = database.execute("SELECT type, name, sql FROM sqlite_master ORDER BY name").fetchall()
    changes = database.total_changes
    database.execute("PRAGMA query_only = ON")
    result = database.execute(sql, arguments).fetchall()
    assert before == database.execute("SELECT * FROM reports ORDER BY id").fetchall()
    assert schema == database.execute("SELECT type, name, sql FROM sqlite_master ORDER BY name").fetchall()
    assert changes == database.total_changes
    assert database.execute("PRAGMA user_version").fetchone()[0] == 2
    for row in result:
        print(row[0])
