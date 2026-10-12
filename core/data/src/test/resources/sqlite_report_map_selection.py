"""Execute Kotlin's production selection on the relevant SQLite columns in memory."""
import sqlite3
import sys
from pathlib import Path

lines = Path(sys.argv[1]).read_text(encoding="utf-8").splitlines()
sql = lines[0]
arguments = lines[1].split("\t")
rows = []
for line in lines[2:]:
    values = line.split("\t")
    rows.append((
        values[0],
        None if values[1] == "NULL" else float(values[1]),
        None if values[2] == "NULL" else float(values[2]),
        values[3], values[4],
        int(values[5]), int(values[6]), int(values[7]), int(values[8]),
    ))

with sqlite3.connect(":memory:") as database:
    database.execute("""
        CREATE TABLE reports (
            id TEXT PRIMARY KEY, latitude REAL, longitude REAL,
            incident_type TEXT NOT NULL, status TEXT NOT NULL,
            occurred_at_seconds INTEGER NOT NULL, occurred_at_nanos INTEGER NOT NULL,
            created_at_seconds INTEGER NOT NULL, created_at_nanos INTEGER NOT NULL
        )
    """)
    database.executemany("INSERT INTO reports VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", rows)
    before = database.execute("SELECT COUNT(*) FROM reports").fetchone()[0]
    result = database.execute(sql, arguments).fetchall()
    assert before == database.execute("SELECT COUNT(*) FROM reports").fetchone()[0]
    for row in result:
        print(row[0])
