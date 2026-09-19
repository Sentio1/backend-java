-- Deadline.needsChecking (DeadlineEngine.marksNeedsChecking) з'явився в сутності без міграції -
-- ddl-auto: validate падав на старті ("missing column [needs_checking] in table [deadlines]").
-- true = подію перераховано, а цей строк уже зафіксований (DONE/MISSED/EXTENDED) - юристу
-- варто перевірити, чи він ще актуальний; сам статус перерахунок не чіпає.
ALTER TABLE core.deadlines
    ADD COLUMN needs_checking BOOLEAN NOT NULL DEFAULT FALSE;
